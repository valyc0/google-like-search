package com.example.documentsearch.service;

import com.example.documentsearch.model.SearchDocument;
import com.example.documentsearch.model.UploadStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final ElasticsearchOperations elastic;
    private final Tika tika = new Tika();
    
    @Value("${document.chunk.size:5000}")
    private int chunkSize;
    
    // In-memory storage per tracking upload status (in produzione usa Redis/DB)
    private final ConcurrentHashMap<String, UploadStatus> uploadStatusMap = new ConcurrentHashMap<>();

    /**
     * Processa in modo asincrono un documento grande dividendolo in chunk.
     * Supporta PDF, DOC, DOCX, XLS, XLSX, TXT, HTML e molti altri formati.
     */
    @Async
    public CompletableFuture<String> indexDocumentAsync(String filename, InputStream inputStream, long fileSize) {
        String documentId = UUID.randomUUID().toString();
        
        try {
            // Inizializza lo status
            UploadStatus status = new UploadStatus();
            status.setDocumentId(documentId);
            status.setFilename(filename);
            status.setStatus("PROCESSING");
            status.setFileSize(fileSize);
            status.setProcessedChunks(0);
            uploadStatusMap.put(documentId, status);
            
            log.info("Inizio estrazione testo da documento: {} ({})", filename, documentId);
            
            // Estrai tutto il testo usando Tika (rileva automaticamente il formato)
            String text = tika.parseToString(inputStream);
            
            log.info("Testo estratto: {} caratteri. Inizio chunking...", text.length());
            
            // Dividi in chunk
            List<String> chunks = splitIntoChunks(text, chunkSize);
            status.setTotalChunks(chunks.size());
            
            log.info("Creati {} chunk. Inizio indicizzazione...", chunks.size());
            
            // Indicizza ogni chunk
            List<SearchDocument> documents = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                SearchDocument doc = new SearchDocument();
                doc.setId(UUID.randomUUID().toString());
                doc.setDocumentId(documentId);
                doc.setFilename(filename);
                doc.setContent(chunks.get(i));
                doc.setChunkIndex(i);
                doc.setTotalChunks(chunks.size());
                doc.setFileSize(fileSize);
                doc.setUploadedAt(LocalDateTime.now());
                doc.setStatus("COMPLETED");
                
                elastic.save(doc);
                documents.add(doc);
                
                status.setProcessedChunks(i + 1);
                log.debug("Indicizzato chunk {}/{}", i + 1, chunks.size());
            }
            
            status.setStatus("COMPLETED");
            status.setMessage("Documento indicizzato con successo in " + chunks.size() + " chunk");
            
            log.info("Indicizzazione completata per: {} ({})", filename, documentId);
            
            return CompletableFuture.completedFuture(documentId);
            
        } catch (Exception e) {
            log.error("Errore durante l'indicizzazione di: " + filename, e);
            
            UploadStatus status = uploadStatusMap.get(documentId);
            if (status != null) {
                status.setStatus("FAILED");
                status.setMessage("Errore: " + e.getMessage());
            }
            
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Metodo sincrono per file piccoli (con chunking).
     * Supporta tutti i formati rilevati da Apache Tika.
     */
    public SearchDocument indexDocument(String filename, InputStream inputStream) throws Exception {
        String text = tika.parseToString(inputStream);
        String documentId = UUID.randomUUID().toString();
        
        // Usa chunking anche per file piccoli
        List<String> chunks = splitIntoChunks(text, chunkSize);
        log.info("Creati {} chunk per file sincrono: {}", chunks.size(), filename);
        
        SearchDocument lastDoc = null;
        for (int i = 0; i < chunks.size(); i++) {
            SearchDocument doc = new SearchDocument();
            doc.setId(UUID.randomUUID().toString());
            doc.setDocumentId(documentId);
            doc.setFilename(filename);
            doc.setContent(chunks.get(i));
            doc.setChunkIndex(i);
            doc.setTotalChunks(chunks.size());
            doc.setUploadedAt(LocalDateTime.now());
            doc.setStatus("COMPLETED");
            
            lastDoc = elastic.save(doc);
        }
        
        return lastDoc; // Ritorna l'ultimo chunk per compatibilità
    }
    
    public SearchDocument indexDocument(String filename, byte[] bytes) throws Exception {
        return indexDocument(filename, new java.io.ByteArrayInputStream(bytes));
    }
    
    /**
     * Ottieni lo status di un upload
     */
    public UploadStatus getUploadStatus(String documentId) {
        return uploadStatusMap.get(documentId);
    }
    
    /**
     * Dividi il testo in chunk di dimensione specificata
     */
    private List<String> splitIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        
        int length = text.length();
        for (int i = 0; i < length; i += chunkSize) {
            int end = Math.min(i + chunkSize, length);
            
            // Cerca di spezzare su un confine di parola per evitare di tagliare a metà
            if (end < length) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > i) {
                    end = lastSpace;
                }
            }
            
            chunks.add(text.substring(i, end).trim());
            i = end - chunkSize; // Aggiusta l'indice dopo il trim
        }
        
        return chunks;
    }
}
