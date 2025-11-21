package com.example.documentsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDto {
    private String documentId;
    private String filename;
    private Integer chunkIndex;
    private List<String> highlights;
    private Double score;
}
