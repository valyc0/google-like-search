package com.example.documentsearch.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadStatus {
    private String documentId;
    private String filename;
    private String status; // PROCESSING, COMPLETED, FAILED
    private Integer totalChunks;
    private Integer processedChunks;
    private Long fileSize;
    private String message;
}
