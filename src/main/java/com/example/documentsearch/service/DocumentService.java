package com.example.documentsearch.service;

import com.example.documentsearch.model.SearchDocument;
import com.example.documentsearch.model.UploadStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;

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
            
            // Leggi i byte per calcolare checksum ed estrarre metadati
            byte[] fileBytes = inputStream.readAllBytes();
            String checksum = calculateChecksum(fileBytes);
            log.info("Checksum calcolato: {}", checksum);
            
            // Estrai metadati
            Metadata metadata = extractMetadata(fileBytes);
            
            // Verifica se esiste già
            if (documentExists(filename, checksum)) {
                log.info("⚠️ Documento già esistente (stesso nome e checksum): {} - SKIP", filename);
                status.setStatus("SKIPPED");
                status.setMessage("File già indicizzato (stesso contenuto)");
                return CompletableFuture.completedFuture(documentId);
            }
            
            // Estrai tutto il testo usando Tika (rileva automaticamente il formato)
            String text = tika.parseToString(new java.io.ByteArrayInputStream(fileBytes));
            
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
                doc.setFileChecksum(checksum);
                doc.setContent(chunks.get(i));
                doc.setChunkIndex(i);
                doc.setTotalChunks(chunks.size());
                doc.setFileSize(fileSize);
                doc.setUploadedAt(LocalDateTime.now());
                doc.setStatus("COMPLETED");
                
                // Applica metadati (uguali per tutti i chunk dello stesso documento)
                applyMetadata(doc, metadata);
                
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
        // Leggi i byte per calcolare checksum ed estrarre metadati
        byte[] fileBytes = inputStream.readAllBytes();
        String checksum = calculateChecksum(fileBytes);
        log.info("Checksum calcolato per file sincrono: {}", checksum);
        
        // Estrai metadati
        Metadata metadata = extractMetadata(fileBytes);
        
        // Verifica se esiste già
        if (documentExists(filename, checksum)) {
            log.info("⚠️ Documento già esistente (stesso nome e checksum): {} - SKIP", filename);
            return null; // Ritorna null per indicare skip
        }
        
        String text = tika.parseToString(new java.io.ByteArrayInputStream(fileBytes));
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
            doc.setFileChecksum(checksum);
            doc.setContent(chunks.get(i));
            doc.setChunkIndex(i);
            doc.setTotalChunks(chunks.size());
            doc.setUploadedAt(LocalDateTime.now());
            doc.setStatus("COMPLETED");
            
            // Applica metadati
            applyMetadata(doc, metadata);
            
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
     * Estrae metadati dal file usando Tika
     */
    private Metadata extractMetadata(byte[] fileBytes) {
        try {
            Parser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1); // -1 = no limit
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            
            parser.parse(new ByteArrayInputStream(fileBytes), handler, metadata, context);
            return metadata;
        } catch (Exception e) {
            log.warn("Errore nell'estrazione metadati: {}", e.getMessage());
            return new Metadata(); // Ritorna metadata vuoti
        }
    }
    
    /**
     * Applica i metadati estratti al documento
     */
    private void applyMetadata(SearchDocument doc, Metadata metadata) {
        try {
            // Autore
            String author = metadata.get(TikaCoreProperties.CREATOR);
            if (author == null) author = metadata.get("Author");
            doc.setAuthor(author);
            
            // Titolo
            doc.setTitle(metadata.get(TikaCoreProperties.TITLE));
            
            // Content Type
            doc.setContentType(metadata.get("Content-Type"));
            
            // Data creazione
            String created = metadata.get(TikaCoreProperties.CREATED);
            if (created != null) {
                try {
                    Date creationDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(created);
                    doc.setCreationDate(java.time.Instant.ofEpochMilli(creationDate.getTime())
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
                } catch (Exception e) {
                    log.debug("Impossibile parsare data creazione: {}", created);
                }
            }
            
            // Data modifica
            String modified = metadata.get(TikaCoreProperties.MODIFIED);
            if (modified != null) {
                try {
                    Date modifiedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(modified);
                    doc.setLastModified(java.time.Instant.ofEpochMilli(modifiedDate.getTime())
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
                } catch (Exception e) {
                    log.debug("Impossibile parsare data modifica: {}", modified);
                }
            }
            
            // Creator (software)
            doc.setCreator(metadata.get("producer")); // PDF producer
            if (doc.getCreator() == null) {
                doc.setCreator(metadata.get("Application-Name"));
            }
            
            // Keywords
            String keywords = metadata.get("Keywords");
            if (keywords == null) keywords = metadata.get("meta:keyword");
            doc.setKeywords(keywords);
            
            // Subject
            doc.setSubject(metadata.get(TikaCoreProperties.SUBJECT));
            
            // Page count (principalmente per PDF)
            String pages = metadata.get("xmpTPg:NPages");
            if (pages == null) pages = metadata.get("Page-Count");
            if (pages != null) {
                try {
                    doc.setPageCount(Integer.parseInt(pages));
                } catch (NumberFormatException e) {
                    log.debug("Impossibile parsare numero pagine: {}", pages);
                }
            }
            
            log.debug("Metadati estratti - Autore: {}, Titolo: {}, Tipo: {}, Pagine: {}",
                    doc.getAuthor(), doc.getTitle(), doc.getContentType(), doc.getPageCount());
                    
        } catch (Exception e) {
            log.warn("Errore nell'applicazione metadati: {}", e.getMessage());
        }
    }
    
    /**
     * Calcola il checksum SHA-256 di un array di byte
     */
    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Errore nel calcolo del checksum", e);
            return null;
        }
    }
    
    /**
     * Verifica se esiste già un documento con lo stesso filename e checksum
     */
    private boolean documentExists(String filename, String checksum) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> b
                                .must(m -> m.term(t -> t.field("filename.keyword").value(filename)))
                                .must(m -> m.term(t -> t.field("fileChecksum").value(checksum)))
                        )
                )
                .withMaxResults(1)
                .build();
        
        SearchHits<?> hits = elastic.search(query, SearchDocument.class);
        return hits.getTotalHits() > 0;
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
