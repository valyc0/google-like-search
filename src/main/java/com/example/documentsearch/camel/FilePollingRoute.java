package com.example.documentsearch.camel;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Route Apache Camel per il polling automatico di documenti da una directory.
 * Supporta PDF, DOC, DOCX, XLS, XLSX, TXT, HTML e molti altri formati.
 * 
 * Monitora una directory configurabile e quando arrivano nuovi file
 * li processa automaticamente inviandoli al servizio di indicizzazione Elasticsearch.
 * 
 * Configurazione in application.properties:
 * - file-polling.enabled: true/false per abilitare/disabilitare
 * - file-polling.input-directory: directory da monitorare
 * - file-polling.processed-directory: dove spostare i file processati
 * - file-polling.error-directory: dove spostare i file con errori
 * - file-polling.delay: frequenza di polling in ms
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "file-polling.enabled", havingValue = "true", matchIfMissing = false)
public class FilePollingRoute extends RouteBuilder {

    @Value("${file-polling.input-directory:./upload}")
    private String inputDirectory;

    @Value("${file-polling.processed-directory:./processed}")
    private String processedDirectory;

    @Value("${file-polling.error-directory:./errors}")
    private String errorDirectory;

    @Value("${file-polling.delay:5000}")
    private int pollingDelay;

    @Value("${file-polling.initial-delay:1000}")
    private int initialDelay;

    @Value("${file-polling.max-concurrent:3}")
    private int maxConcurrent;

    @Override
    public void configure() throws Exception {
        
        // Crea le directory se non esistono
        createDirectoriesIfNotExist();

        log.warn("🚀 ===== AVVIO ROUTE DI POLLING FILE DOCUMENTI =====");
        log.warn("📂 Directory input: {}", new File(inputDirectory).getAbsolutePath());
        log.warn("✅ Directory processati: {}", new File(processedDirectory).getAbsolutePath());
        log.warn("❌ Directory errori: {}", new File(errorDirectory).getAbsolutePath());
        log.warn("⏱️  Polling delay: {}ms", pollingDelay);
        log.warn("🔍 Pattern file: tutti i formati supportati");
        log.warn("⚙️  Endpoint Camel: {}", buildFileEndpoint());

        // Route principale per il polling
        from(buildFileEndpoint())
            .routeId("document-polling-route")
            .log("📥 Nuovo documento rilevato: ${header.CamelFileName}")
            
            // Limita il numero di file processati in parallelo
            .threads().poolSize(maxConcurrent).maxPoolSize(maxConcurrent)
            
            // Gestione errori: se fallisce, sposta in error-directory
            .onException(Exception.class)
                .log("❌ Errore nel processamento di ${header.CamelFileName}: ${exception.message}")
                .handled(true)
                .to("file:" + errorDirectory)
                .log("📁 File spostato in error-directory: ${header.CamelFileName}")
            .end()
            
            // Processa il file con il bean DocumentProcessorBean
            .bean("documentProcessorBean", "processDocument")
            
            // Se tutto OK, sposta il file nella processed-directory
            .to("file:" + processedDirectory)
            .log("✅ File processato e spostato: ${header.CamelFileName}");
    }

    /**
     * Costruisce l'endpoint Camel File con tutti i parametri configurati.
     * Accetta tutti i formati di documento supportati da Apache Tika.
     */
    private String buildFileEndpoint() {
        // Pattern per accettare documenti comuni
        String pattern = ".*\\.(pdf|doc|docx|xls|xlsx|txt|html|htm|rtf|odt|ods|csv|xml|json|md)$";
        
        return String.format(
            "file:%s?delay=%d&initialDelay=%d&include=%s&noop=false&delete=true",
            inputDirectory,
            pollingDelay,
            initialDelay,
            pattern
        );
    }

    /**
     * Crea le directory necessarie se non esistono
     */
    private void createDirectoriesIfNotExist() {
        createDirectory(inputDirectory);
        createDirectory(processedDirectory);
        createDirectory(errorDirectory);
    }

    private void createDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                log.info("📁 Directory creata: {}", dir.getAbsolutePath());
            } else {
                log.warn("⚠️ Impossibile creare directory: {}", path);
            }
        }
    }
}
