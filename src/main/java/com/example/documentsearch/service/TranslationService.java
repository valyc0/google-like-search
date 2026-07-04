package com.example.documentsearch.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
public class TranslationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    public TranslationService(
            @Value("${translation.api.url}") String apiUrl,
            @Value("${translation.api.key}") String apiKey,
            @Value("${translation.api.model}") String model,
            @Value("${translation.enabled:true}") boolean enabled,
            RestTemplateBuilder builder) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled;
        this.restTemplate = builder.build();
        this.objectMapper = new ObjectMapper();
    }

    public TranslationResult translateToItalian(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return new TranslationResult(text, "unknown");
        }

        try {
            ChatRequest request = new ChatRequest();
            request.setModel(model);
            request.setTemperature(0.1);
            request.setMaxTokens(8192);
            request.setStream(false);
            request.setMessages(List.of(
                    new ChatMessage("system", """
                            Translate the following text to Italian.
                            Detect the source language.
                            Return ONLY a JSON object with two fields:
                            - "source_lang": ISO 639-1 language code of the source (e.g., "en", "fr", "de")
                            - "translation": the Italian translation
                            Example: {"source_lang": "en", "translation": "Ciao mondo"}
                            No other text, no markdown, just the JSON object.
                            """),
                    new ChatMessage("user", text)
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ChatResponse> response = restTemplate.postForEntity(
                    apiUrl, entity, ChatResponse.class);

            if (response.getBody() != null
                    && response.getBody().getChoices() != null
                    && !response.getBody().getChoices().isEmpty()
                    && response.getBody().getChoices().get(0).getMessage() != null) {

                String content = response.getBody().getChoices().get(0).getMessage().getContent().strip();
                return parseResult(content, text);
            }

            log.warn("Risposta vuota per chunk ({} chars)", text.length());
            return new TranslationResult(text, "unknown");

        } catch (Exception e) {
            log.error("Errore traduzione chunk ({} chars): {}", text.length(), e.getMessage());
            return new TranslationResult(text, "unknown");
        }
    }

    private TranslationResult parseResult(String content, String originalText) {
        try {
            JsonNode json = objectMapper.readTree(content);
            String sourceLang = json.has("source_lang")
                    ? json.get("source_lang").asText("unknown")
                    : "unknown";
            String translation = json.has("translation")
                    ? json.get("translation").asText(originalText)
                    : originalText;
            return new TranslationResult(translation, sourceLang);
        } catch (Exception e) {
            log.warn("Impossibile parsare JSON, uso risposta grezza: {}", e.getMessage());
            return new TranslationResult(content, "unknown");
        }
    }

    @Data
    public static class TranslationResult {
        private final String translatedText;
        private final String sourceLang;
    }

    @Data
    private static class ChatRequest {
        private String model;
        private List<ChatMessage> messages;
        private double temperature;
        @JsonProperty("max_tokens")
        private int maxTokens;
        private boolean stream;
    }

    @Data
    private static class ChatMessage {
        private String role;
        private String content;

        ChatMessage() {}

        ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    private static class ChatResponse {
        private List<Choice> choices;
    }

    @Data
    private static class Choice {
        private ChatMessage message;
    }
}
