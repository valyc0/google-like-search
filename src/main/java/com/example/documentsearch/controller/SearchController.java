package com.example.documentsearch.controller;

import com.example.documentsearch.dto.SearchResultDto;
import com.example.documentsearch.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * Cerca nei documenti con risultati raggruppati e ottimizzati (GET)
     */
    @GetMapping
    public List<SearchResultDto> search(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "10") Integer maxResults) {
        return searchService.search(q, maxResults);
    }
    
    /**
     * Cerca nei documenti con risultati raggruppati e ottimizzati (POST con JSON)
     */
    @PostMapping("/query")
    public List<SearchResultDto> searchPost(@RequestBody Map<String, Object> request) {
        String question = (String) request.get("question");
        Integer maxResults = request.containsKey("maxResults") 
            ? (Integer) request.get("maxResults") 
            : 10;
        return searchService.search(question, maxResults);
    }
    
    /**
     * Ricerca raw per debugging - restituisce tutti i chunk trovati
     */
    @GetMapping("/raw")
    public List<?> searchRaw(@RequestParam String q) {
        return searchService.searchRaw(q);
    }
    
    /**
     * Restituisce la lista dei nomi file unici indicizzati
     */
    @GetMapping("/files")
    public List<String> getIndexedFiles() {
        return searchService.getIndexedFilenames();
    }
}
