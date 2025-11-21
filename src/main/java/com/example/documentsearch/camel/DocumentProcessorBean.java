package com.example.documentsearch.camel;

import com.example.documentsearch.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Bean Spring che processa i documenti intercettati da Apache Camel.
 * Supporta PDF, DOC, DOCX, XLS, XLSX, TXT, HTML e molti altri formati.
 * 
 * Converte il File del file system in un formato che DocumentService
 * può processare, quindi lo invia a Elasticsearch per l'indicizzazione.
 */
@Component("documentProcessorBean")
@Slf4j
@RequiredArgsConstructor
public class DocumentProcessorBean {

    private final DocumentService documentService;

    /**
     * Processa un documento dal file system e lo indicizza in Elasticsearch
     * 
     * @param exchange Camel Exchange contenente il file
     * @throws Exception se il processamento fallisce
     */
    public void processDocument(Exchange exchange) throws Exception {
        File file = exchange.getIn().getBody(File.class);
        String filename = exchange.getIn().getHeader("CamelFileName", String.class);
        
        log.info("🔄 Inizio processamento documento: {} ({} bytes)", 
                filename, file.length());

        try {
            // Determina se usare upload sincrono o asincrono in base alla dimensione
            long fileSizeBytes = file.length();
            long fileSizeMB = fileSizeBytes / (1024 * 1024);
            
            if (fileSizeMB > 10) {
                // File grande - upload asincrono
                log.info("📦 File grande ({}MB) - uso upload asincrono", fileSizeMB);
                String documentId;
                try (InputStream is = new FileInputStream(file)) {
                    documentId = documentService.indexDocumentAsync(
                        filename, 
                        is, 
                        fileSizeBytes
                    ).get(); // Aspetta il completamento
                }
                
                log.info("✅ Documento processato con successo (async): {} - DocumentID: {}", 
                        filename, documentId);
                exchange.getIn().setHeader("DocumentId", documentId);
            } else {
                // File piccolo - upload sincrono
                log.info("📄 File piccolo ({}MB) - uso upload sincrono", fileSizeMB);
                try (InputStream inputStream = new FileInputStream(file)) {
                    var result = documentService.indexDocument(filename, inputStream);
                    log.info("✅ Documento processato con successo (sync): {} - ID: {}", 
                            filename, result.getDocumentId());
                    exchange.getIn().setHeader("DocumentId", result.getDocumentId());
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Errore nel processamento di {}: {}", filename, e.getMessage());
            throw e; // Rilancia per gestione errori della route
        }
    }
}
