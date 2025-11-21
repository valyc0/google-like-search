package com.example.documentsearch.config;

import com.example.documentsearch.model.SearchDocument;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchConfig {

    private final ElasticsearchOperations elasticsearchOperations;

    @PostConstruct
    public void createIndex() {
        // Attendi che Elasticsearch sia disponibile
        int maxRetries = 10;
        int retryDelay = 2000; // 2 secondi
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                IndexOperations indexOps = elasticsearchOperations.indexOps(SearchDocument.class);
                
                if (indexOps.exists()) {
                    log.warn("⚠️  Indice 'documents' già esistente");
                    log.warn("💡 Per aggiornare lo schema con i nuovi metadati, esegui:");
                    log.warn("   curl -X DELETE http://localhost:9200/documents");
                    log.warn("   e poi riavvia l'applicazione");
                } else {
                    log.info("📦 Creazione indice 'documents' in Elasticsearch...");
                    indexOps.create();
                    indexOps.putMapping(indexOps.createMapping());
                    log.info("✅ Indice 'documents' creato con successo con metadati!");
                }
                return; // Successo, esci
                
            } catch (Exception e) {
                if (i < maxRetries - 1) {
                    log.warn("⏳ Elasticsearch non ancora disponibile, riprovo tra {}ms... ({}/{})", 
                            retryDelay, i + 1, maxRetries);
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("❌ Interruzione durante l'attesa di Elasticsearch");
                        return;
                    }
                } else {
                    log.error("❌ Elasticsearch non disponibile dopo {} tentativi", maxRetries);
                    log.error("💡 Verifica che Elasticsearch sia in esecuzione su http://localhost:9200");
                }
            }
        }
    }
}
