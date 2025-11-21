package com.example.documentsearch.controller;

import com.example.documentsearch.model.SearchDocument;
import com.example.documentsearch.model.UploadStatus;
import com.example.documentsearch.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class UploadController {

    private final DocumentService documentService;

    /**
     * Upload asincrono per file grandi - supporta tutti i formati (PDF, DOC, DOCX, XLS, XLSX, TXT, HTML, etc.)
     */
    @PostMapping("/upload-async")
    public ResponseEntity<Map<String, String>> uploadAsync(@RequestParam("file") MultipartFile file) {
        try {
            CompletableFuture<String> future = documentService.indexDocumentAsync(
                file.getOriginalFilename(), 
                file.getInputStream(),
                file.getSize()
            );
            
            // Attendi un attimo per ottenere il documentId
            String documentId = future.getNow(null);
            
            // Se non è ancora disponibile, recuperalo dallo status
            if (documentId == null) {
                // In questo caso il processing è già partito, trova l'ultimo status
                Thread.sleep(100); // Piccolo delay per permettere l'inizializzazione
            }
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Upload started");
            response.put("status", "Use /api/documents/status endpoint to check progress");
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Upload sincrono per file piccoli - supporta tutti i formati
     */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            // Se il file è grande (>10MB), usa async
            if (file.getSize() > 10 * 1024 * 1024) {
                return uploadAsync(file);
            }
            
            // Altrimenti processa sincrono
            SearchDocument doc = documentService.indexDocument(file.getOriginalFilename(), file.getBytes());
            return ResponseEntity.ok(doc);
            
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Controlla lo status di un upload
     */
    @GetMapping("/status/{documentId}")
    public ResponseEntity<?> getStatus(@PathVariable String documentId) {
        UploadStatus status = documentService.getUploadStatus(documentId);
        
        if (status == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Document not found or already completed");
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(status);
    }
}
