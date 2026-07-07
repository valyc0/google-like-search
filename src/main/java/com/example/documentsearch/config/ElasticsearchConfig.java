package com.example.documentsearch.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import com.example.documentsearch.model.SearchDocument;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchConfig {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;

    @Value("${embedding.dimension:1024}")
    private int embeddingDimension;

    @PostConstruct
    public void createIndex() {
        int maxRetries = 10;
        int retryDelay = 2000;

        for (int i = 0; i < maxRetries; i++) {
            try {
                IndexOperations indexOps = elasticsearchOperations.indexOps(SearchDocument.class);

                if (indexOps.exists()) {
                    log.warn("Indice 'documents' già esistente");
                    addVectorMapping();
                } else {
                    log.info("Creazione indice 'documents' in Elasticsearch...");
                    indexOps.create();
                    indexOps.putMapping(indexOps.createMapping());
                    log.info("Indice 'documents' creato con successo con metadati!");
                    addVectorMapping();
                }
                return;

            } catch (Exception e) {
                if (i < maxRetries - 1) {
                    log.warn("Elasticsearch non ancora disponibile, riprovo tra {}ms... ({}/{})",
                            retryDelay, i + 1, maxRetries);
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Interruzione durante l'attesa di Elasticsearch");
                        return;
                    }
                } else {
                    log.error("Elasticsearch non disponibile dopo {} tentativi", maxRetries);
                    log.error("Verifica che Elasticsearch sia in esecuzione su http://localhost:9200");
                }
            }
        }
    }

    private void addVectorMapping() {
        try {
            elasticsearchClient.indices().putMapping(req -> req
                .index("documents")
                .properties("vector", Property.of(p -> p
                    .denseVector(dv -> dv
                        .dims(embeddingDimension)
                        .index(true)
                        .similarity("cosine")
                    )
                ))
            );
            log.info("Campo 'vector' (dense_vector, dims={}) aggiunto al mapping", embeddingDimension);
        } catch (Exception e) {
            log.warn("Impossibile aggiornare mapping con dense_vector: {}", e.getMessage());
        }
    }
}
