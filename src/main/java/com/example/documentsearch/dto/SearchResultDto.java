package com.example.documentsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDto {
    private String documentId;
    private String filename;
    private String fileChecksum;
    private Integer chunkIndex;
    private List<String> highlights;
    private Double score;
    
    // Metadati
    private String author;
    private String title;
    private String contentType;
    private LocalDateTime creationDate;
    private LocalDateTime lastModified;
    private String creator;
    private String keywords;
    private String subject;
    private Integer pageCount;
}
