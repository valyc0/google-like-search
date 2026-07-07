package com.example.documentsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmbeddingService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final boolean enabled;
    private final int dimension;

    public EmbeddingService(
            @Value("${embedding.api.url}") String apiUrl,
            @Value("${embedding.api.key}") String apiKey,
            @Value("${embedding.api.model}") String model,
            @Value("${embedding.enabled:true}") boolean enabled,
            @Value("${embedding.dimension:1024}") int dimension,
            RestTemplateBuilder builder) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled;
        this.dimension = dimension;
        this.restTemplate = builder.build();
        this.objectMapper = new ObjectMapper();
    }

    public float[] embed(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return new float[dimension];
        }
        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "input", text
            );
            String json = callApi(body);
            if (json != null) {
                return parseSingleEmbedding(json);
            }
        } catch (Exception e) {
            log.error("Errore embedding per testo ({} chars): {}", text.length(), e.getMessage());
        }
        return new float[dimension];
    }

    public List<float[]> embedBatch(List<String> texts) {
        if (!enabled || texts == null || texts.isEmpty()) {
            return texts.stream().map(t -> new float[dimension]).toList();
        }
        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "input", texts
            );
            String json = callApi(body);
            if (json != null) {
                return parseBatchEmbeddings(json, texts.size());
            }
        } catch (Exception e) {
            log.error("Errore embedding batch ({} testi): {}", texts.size(), e.getMessage());
        }
        return texts.stream().map(t -> new float[dimension]).toList();
    }

    private String callApi(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);
        return response.getBody();
    }

    private float[] parseSingleEmbedding(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode data = root.get("data");
        if (data != null && data.isArray() && data.size() > 0) {
            JsonNode embeddingNode = data.get(0).get("embedding");
            return jsonNodeToFloats(embeddingNode);
        }
        throw new RuntimeException("Formato risposta embedding non riconosciuto");
    }

    private List<float[]> parseBatchEmbeddings(String json, int expectedSize) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode data = root.get("data");
        List<float[]> result = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode item : data) {
                JsonNode embeddingNode = item.get("embedding");
                result.add(jsonNodeToFloats(embeddingNode));
            }
        }
        return result;
    }

    private float[] jsonNodeToFloats(JsonNode node) {
        if (node == null || !node.isArray()) return new float[dimension];
        float[] result = new float[node.size()];
        for (int i = 0; i < node.size(); i++) {
            result[i] = (float) node.get(i).asDouble();
        }
        return result;
    }
}
