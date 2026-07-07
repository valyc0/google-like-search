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
    private final TranslationService translationService;
    private final EmbeddingService embeddingService;
    private final Tika tika = new Tika();

    @Value("${document.chunk.size:5000}")
    private int chunkSize;

    @Value("${translation.chunk.max-size:15000}")
    private int translationChunkMaxSize;

    // In-memory storage per tracking upload status (in produzione usa Redis/DB)
    private final ConcurrentHashMap<String, UploadStatus> uploadStatusMap = new ConcurrentHashMap<>();

    // ---- PUBLIC METHODS ----

    @Async
    public CompletableFuture<String> indexDocumentAsync(String filename, InputStream inputStream, long fileSize) {
        String documentId = UUID.randomUUID().toString();

        try {
            UploadStatus status = new UploadStatus();
            status.setDocumentId(documentId);
            status.setFilename(filename);
            status.setStatus("PROCESSING");
            status.setFileSize(fileSize);
            status.setProcessedChunks(0);
            uploadStatusMap.put(documentId, status);

            log.info("Inizio estrazione testo da documento: {} ({})", filename, documentId);

            byte[] fileBytes = inputStream.readAllBytes();
            String checksum = calculateChecksum(fileBytes);
            log.info("Checksum calcolato: {}", checksum);

            Metadata metadata = extractMetadata(fileBytes);

            if (documentExists(filename, checksum)) {
                log.info("Documento già esistente (stesso nome e checksum): {} - SKIP", filename);
                status.setStatus("SKIPPED");
                status.setMessage("File già indicizzato (stesso contenuto)");
                return CompletableFuture.completedFuture(documentId);
            }

            String text = tika.parseToString(new ByteArrayInputStream(fileBytes));
            log.info("Testo estratto: {} caratteri", text.length());

            // Step 1: split in macro-chunk a confini di paragrafo (~15000 char)
            List<String> translationChunks = splitIntoTranslationChunks(text, translationChunkMaxSize);
            status.setTotalChunks(translationChunks.size());
            log.info("Creati {} macro-chunk per traduzione", translationChunks.size());

            // Step 2: traduci, sub-chunka, indicizza
            List<SearchDocument> allDocs = new ArrayList<>();
            int globalChunkIndex = 0;

            for (int tci = 0; tci < translationChunks.size(); tci++) {
                String chunkText = translationChunks.get(tci);

                TranslationService.TranslationResult result = translationService.translateToItalian(chunkText);

                List<String> origSubChunks = splitIntoChunks(chunkText, chunkSize);
                List<String> transSubChunks = splitIntoChunks(result.getTranslatedText(), chunkSize);

                for (String sub : origSubChunks) {
                    SearchDocument doc = buildDocument(documentId, filename, checksum, fileSize, sub,
                            globalChunkIndex++, result.getSourceLang(), metadata);
                    elastic.save(doc);
                    allDocs.add(doc);
                }

                if (!result.getTranslatedText().equals(chunkText)) {
                    for (String sub : transSubChunks) {
                        SearchDocument doc = buildDocument(documentId, filename, checksum, fileSize, sub,
                                globalChunkIndex++, "it", metadata);
                        elastic.save(doc);
                        allDocs.add(doc);
                    }
                }

                status.setProcessedChunks(tci + 1);
                log.debug("Processato macro-chunk {}/{} ({} EN, {} IT)",
                        tci + 1, translationChunks.size(), origSubChunks.size(), transSubChunks.size());
            }

            int total = allDocs.size();
            for (SearchDocument doc : allDocs) {
                doc.setTotalChunks(total);
            }

            generateEmbeddings(allDocs);

            elastic.save(allDocs);

            status.setStatus("COMPLETED");
            status.setMessage("Documento indicizzato con successo: " + translationChunks.size()
                    + " macro-chunk, " + total + " index-chunk (originale + IT)");

            log.info("Indicizzazione completata per: {} ({}) — {} index-chunk totali", filename, documentId, total);

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

    public SearchDocument indexDocument(String filename, InputStream inputStream) throws Exception {
        byte[] fileBytes = inputStream.readAllBytes();
        long fileSize = fileBytes.length;
        String checksum = calculateChecksum(fileBytes);
        log.info("Checksum calcolato per file sincrono: {}", checksum);

        Metadata metadata = extractMetadata(fileBytes);

        if (documentExists(filename, checksum)) {
            log.info("Documento già esistente (stesso nome e checksum): {} - SKIP", filename);
            return null;
        }

        String text = tika.parseToString(new ByteArrayInputStream(fileBytes));
        String documentId = UUID.randomUUID().toString();

        List<String> translationChunks = splitIntoTranslationChunks(text, translationChunkMaxSize);
        log.info("Creati {} macro-chunk per file sincrono: {}", translationChunks.size(), filename);

        SearchDocument lastDoc = null;
        List<SearchDocument> allDocs = new ArrayList<>();
        int globalChunkIndex = 0;

        for (String chunkText : translationChunks) {
            TranslationService.TranslationResult result = translationService.translateToItalian(chunkText);

            List<String> origSubChunks = splitIntoChunks(chunkText, chunkSize);
            List<String> transSubChunks = splitIntoChunks(result.getTranslatedText(), chunkSize);

            for (String sub : origSubChunks) {
                SearchDocument doc = buildDocument(documentId, filename, checksum, fileSize, sub,
                        globalChunkIndex++, result.getSourceLang(), metadata);
                lastDoc = elastic.save(doc);
                allDocs.add(doc);
            }

            if (!result.getTranslatedText().equals(chunkText)) {
                for (String sub : transSubChunks) {
                    SearchDocument doc = buildDocument(documentId, filename, checksum, fileSize, sub,
                            globalChunkIndex++, "it", metadata);
                    elastic.save(doc);
                    allDocs.add(doc);
                }
            }
        }

        int total = allDocs.size();
        for (SearchDocument doc : allDocs) {
            doc.setTotalChunks(total);
        }

        generateEmbeddings(allDocs);

        elastic.save(allDocs);

        return lastDoc;
    }

    public SearchDocument indexDocument(String filename, byte[] bytes) throws Exception {
        return indexDocument(filename, new ByteArrayInputStream(bytes));
    }

    public UploadStatus getUploadStatus(String documentId) {
        return uploadStatusMap.get(documentId);
    }

    private void generateEmbeddings(List<SearchDocument> allDocs) {
        try {
            List<String> texts = allDocs.stream()
                    .map(SearchDocument::getContent)
                    .toList();
            List<float[]> vectors = embeddingService.embedBatch(texts);
            for (int i = 0; i < allDocs.size(); i++) {
                allDocs.get(i).setVector(vectors.get(i));
            }
            log.info("Generati {} embeddings", vectors.size());
        } catch (Exception e) {
            log.warn("Errore generazione embeddings: {}", e.getMessage());
        }
    }

    // ---- PRIVATE HELPERS ----

    private SearchDocument buildDocument(String documentId, String filename, String checksum,
                                          long fileSize, String content, int chunkIndex,
                                          String lang, Metadata metadata) {
        SearchDocument doc = new SearchDocument();
        doc.setId(UUID.randomUUID().toString());
        doc.setDocumentId(documentId);
        doc.setFilename(filename);
        doc.setFileChecksum(checksum);
        doc.setContent(content);
        doc.setChunkIndex(chunkIndex);
        doc.setFileSize(fileSize);
        doc.setUploadedAt(LocalDateTime.now());
        doc.setStatus("COMPLETED");
        doc.setLang(lang);
        applyMetadata(doc, metadata);
        return doc;
    }

    private Metadata extractMetadata(byte[] fileBytes) {
        try {
            Parser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            parser.parse(new ByteArrayInputStream(fileBytes), handler, metadata, context);
            return metadata;
        } catch (Exception e) {
            log.warn("Errore nell'estrazione metadati: {}", e.getMessage());
            return new Metadata();
        }
    }

    private void applyMetadata(SearchDocument doc, Metadata metadata) {
        try {
            String author = metadata.get(TikaCoreProperties.CREATOR);
            if (author == null) author = metadata.get("Author");
            doc.setAuthor(author);

            doc.setTitle(metadata.get(TikaCoreProperties.TITLE));
            doc.setContentType(metadata.get("Content-Type"));

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

            doc.setCreator(metadata.get("producer"));
            if (doc.getCreator() == null) {
                doc.setCreator(metadata.get("Application-Name"));
            }

            String keywords = metadata.get("Keywords");
            if (keywords == null) keywords = metadata.get("meta:keyword");
            doc.setKeywords(keywords);

            doc.setSubject(metadata.get(TikaCoreProperties.SUBJECT));

            String pages = metadata.get("xmpTPg:NPages");
            if (pages == null) pages = metadata.get("Page-Count");
            if (pages != null) {
                try {
                    doc.setPageCount(Integer.parseInt(pages));
                } catch (NumberFormatException e) {
                    log.debug("Impossibile parsare numero pagine: {}", pages);
                }
            }

        } catch (Exception e) {
            log.warn("Errore nell'applicazione metadati: {}", e.getMessage());
        }
    }

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
     * Divide il testo in macro-chunk a confini di paragrafo (\n\n)
     * per massimizzare il contesto nella traduzione LLM.
     */
    private List<String> splitIntoTranslationChunks(String text, int maxChunkSize) {
        List<String> result = new ArrayList<>();

        if (text == null || text.isEmpty()) return result;

        String[] paragraphs = text.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            if (current.length() + trimmed.length() + 2 > maxChunkSize && current.length() > 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            }

            if (current.length() > 0) current.append("\n\n");
            current.append(trimmed);
        }

        if (current.length() > 0) {
            result.add(current.toString().trim());
        }

        // Se un singolo paragrafo supera maxChunkSize, splittalo forzatamente
        List<String> finalResult = new ArrayList<>();
        for (String chunk : result) {
            if (chunk.length() > maxChunkSize) {
                finalResult.addAll(splitIntoChunks(chunk, maxChunkSize));
            } else {
                finalResult.add(chunk);
            }
        }

        return finalResult;
    }

    /**
     * Divide il testo in chunk di dimensione fissa, cercando confini di parola.
     */
    private List<String> splitIntoChunks(String text, int size) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) return chunks;

        int length = text.length();
        for (int i = 0; i < length; i += size) {
            int end = Math.min(i + size, length);

            if (end < length) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > i) {
                    end = lastSpace;
                }
            }

            chunks.add(text.substring(i, end).trim());
            i = end - size;
        }

        return chunks;
    }
}
