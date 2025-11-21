package com.example.documentsearch.service;

import com.example.documentsearch.dto.SearchResultDto;
import com.example.documentsearch.model.SearchDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchOperations elastic;

    @Value("${document.index.name}")
    private String indexName;

    /**
     * Cerca nei chunk e restituisce risultati raggruppati per documento
     */
    public List<SearchResultDto> search(String query, Integer maxResults) {
        if (maxResults == null) {
            maxResults = 10;
        }
        
        HighlightFieldParameters highlightParameters = HighlightFieldParameters.builder()
                .withPreTags(new String[]{"<mark>"})
                .withPostTags(new String[]{"</mark>"})
                .withFragmentSize(150) // Limita la dimensione dei frammenti
                .withNumberOfFragments(3) // Max 3 frammenti per risultato
                .build();

        HighlightField highlightField = new HighlightField("content", highlightParameters);
        Highlight highlight = new Highlight(List.of(highlightField));
        HighlightQuery highlightQuery = new HighlightQuery(highlight, null);

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q
                        .match(m -> m
                                .field("content")
                                .query(query)
                        )
                )
                .withHighlightQuery(highlightQuery)
                .withMaxResults(maxResults * 3) // Prendi più risultati perché poi raggruppiamo
                .build();

        SearchHits<SearchDocument> searchHits = elastic.search(nativeQuery, SearchDocument.class);
        
        // Converti in DTO e raggruppa per documento
        Map<String, SearchResultDto> resultsByDocument = new HashMap<>();
        
        for (SearchHit<SearchDocument> hit : searchHits.getSearchHits()) {
            SearchDocument doc = hit.getContent();
            String docId = doc.getDocumentId() != null ? doc.getDocumentId() : doc.getId();
            
            SearchResultDto result = resultsByDocument.get(docId);
            
            if (result == null) {
                result = new SearchResultDto();
                result.setDocumentId(docId);
                result.setFilename(doc.getFilename());
                result.setChunkIndex(doc.getChunkIndex());
                result.setScore(Double.valueOf(hit.getScore()));
                result.setHighlights(new ArrayList<>());
                resultsByDocument.put(docId, result);
            }
            
            // Aggiungi gli highlights di questo chunk
            List<String> highlights = hit.getHighlightFields().get("content");
            if (highlights != null && !highlights.isEmpty()) {
                result.getHighlights().addAll(highlights);
            }
            
            // Mantieni lo score più alto
            if (hit.getScore() > result.getScore()) {
                result.setScore(Double.valueOf(hit.getScore()));
                result.setChunkIndex(doc.getChunkIndex());
            }
        }
        
        // Ordina per score e limita i risultati
        return resultsByDocument.values().stream()
                .sorted(Comparator.comparing(SearchResultDto::getScore).reversed())
                .limit(maxResults)
                .collect(Collectors.toList());
    }
    
    /**
     * Metodo legacy per retrocompatibilità
     */
    public List<SearchHit<SearchDocument>> searchRaw(String query) {
        HighlightFieldParameters highlightParameters = HighlightFieldParameters.builder()
                .withPreTags(new String[]{"<mark>"})
                .withPostTags(new String[]{"</mark>"})
                .build();

        HighlightField highlightField = new HighlightField("content", highlightParameters);
        Highlight highlight = new Highlight(List.of(highlightField));
        HighlightQuery highlightQuery = new HighlightQuery(highlight, null);

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q
                        .match(m -> m
                                .field("content")
                                .query(query)
                        )
                )
                .withHighlightQuery(highlightQuery)
                .build();

        SearchHits<SearchDocument> searchHits = elastic.search(nativeQuery, SearchDocument.class);
        return searchHits.getSearchHits();
    }
    
    /**
     * Restituisce la lista dei nomi file unici indicizzati
     */
    public List<String> getIndexedFilenames() {
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q.matchAll(m -> m))
                .withFields("filename")
                .withMaxResults(10000)
                .build();

        SearchHits<SearchDocument> searchHits = elastic.search(nativeQuery, SearchDocument.class);
        
        return searchHits.getSearchHits().stream()
                .map(hit -> hit.getContent().getFilename())
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
