package com.example.documentsearch.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Document(indexName = "documents")
public class SearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String documentId; // ID del documento originale (uguale per tutti i chunk)

    @Field(type = FieldType.Text)
    private String filename;

    @Field(type = FieldType.Keyword)
    private String fileChecksum; // SHA-256 del file originale per de-duplicazione

    @Field(type = FieldType.Text)
    private String content; // Il chunk di testo
    
    // Metadati estratti da Tika
    @Field(type = FieldType.Text)
    private String author; // Autore del documento
    
    @Field(type = FieldType.Text)
    private String title; // Titolo del documento
    
    @Field(type = FieldType.Keyword)
    private String contentType; // Tipo MIME (application/pdf, text/html, etc.)
    
    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime creationDate; // Data creazione documento
    
    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime lastModified; // Data ultima modifica
    
    @Field(type = FieldType.Text)
    private String creator; // Software/applicazione che ha creato il documento
    
    @Field(type = FieldType.Text)
    private String keywords; // Parole chiave del documento
    
    @Field(type = FieldType.Text)
    private String subject; // Oggetto/argomento del documento
    
    @Field(type = FieldType.Integer)
    private Integer pageCount; // Numero di pagine (per PDF)

    @Field(type = FieldType.Integer)
    private Integer chunkIndex; // Indice del chunk (0, 1, 2, ...)

    @Field(type = FieldType.Integer)
    private Integer totalChunks; // Numero totale di chunk per questo documento

    @Field(type = FieldType.Long)
    private Long fileSize; // Dimensione file originale in bytes

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime uploadedAt;

    @Field(type = FieldType.Keyword)
    private String status; // PROCESSING, COMPLETED, FAILED
}
