# Google-like Search with Spring Boot 3 and Elasticsearch

This project demonstrates a "Google-like" search engine using Spring Boot 3, Elasticsearch, and Apache Tika for document content extraction. **Supports multiple document formats** including PDF, DOC, DOCX, XLS, XLSX, TXT, HTML and many more, with automatic chunking and asynchronous processing.

## Features

✅ **Multi-format support** - Upload PDF, DOC, DOCX, XLS, XLSX, TXT, HTML, RTF, ODT, ODS, CSV, XML, JSON, Markdown  
✅ **Large file support** - Upload documents up to 10GB  
✅ **Automatic chunking** - Large documents are split into searchable chunks  
✅ **Asynchronous processing** - Non-blocking uploads for better performance  
✅ **Full-text search** - Fast and accurate text search with Elasticsearch  
✅ **Highlighting** - Search results include highlighted snippets  
✅ **Status tracking** - Monitor processing progress for large files  
✅ **Automatic format detection** - Apache Tika automatically detects file formats  

## Prerequisites

- Docker and Docker Compose
- Java 17+
- Maven

## Setup

1.  **Start Elasticsearch and Kibana**:
    ```bash
    docker-compose up -d
    ```
    Elasticsearch will be available at `http://localhost:9200`.
    Kibana will be available at `http://localhost:5601`.

2.  **Run the Application**:
    ```bash
    ./mvnw spring-boot:run
    ```
    (You might need to run `mvn wrapper:wrapper` first if mvnw is missing, or just use `mvn spring-boot:run`)

## Usage

### 1. Upload a Document

**Small files (< 10MB)** - Synchronous upload:
```bash
curl -F "file=@/path/to/document.pdf" http://localhost:8080/api/documents/upload
curl -F "file=@/path/to/report.docx" http://localhost:8080/api/documents/upload
curl -F "file=@/path/to/data.xlsx" http://localhost:8080/api/documents/upload
curl -F "file=@/path/to/page.html" http://localhost:8080/api/documents/upload
```

**Large files (> 10MB)** - Asynchronous upload:
```bash
curl -F "file=@/path/to/large-document.pdf" http://localhost:8080/api/documents/upload-async
```

Response for async upload:
```json
{
  "message": "Upload started",
  "status": "Use /api/documents/status endpoint to check progress"
}
```

### 2. Check Upload Status

Monitor the processing status of a large file:
```bash
curl "http://localhost:8080/api/documents/status/{documentId}"
```

Response:
```json
{
  "documentId": "123e4567-e89b-12d3-a456-426614174000",
  "filename": "large-document.pdf",
  "status": "PROCESSING",
  "totalChunks": 50,
  "processedChunks": 25,
  "fileSize": 52428800,
  "message": "Processing..."
}
```

Status values: `PROCESSING`, `COMPLETED`, `FAILED`

### 3. Search

Search for text within the uploaded documents. Results are grouped by document with highlighted snippets.

**GET method** (with URL encoding):
```bash
curl "http://localhost:8080/api/search?q=arca%20del%20Signore&maxResults=5"
```

**POST method** (recommended - no URL encoding needed):
```bash
curl -X POST http://localhost:8080/api/search/query \
  -H "Content-Type: application/json" \
  -d '{"question": "arca del Signore", "maxResults": 5}'
```

**More examples:**
```bash
# Search in 1Samuele.pdf
curl -X POST http://localhost:8080/api/search/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Chi doveva custodire l arca del Signore?"}'

# Search in any document type
curl -X POST http://localhost:8080/api/search/query \
  -H "Content-Type: application/json" \
  -d '{"question": "report analysis"}'

# Limit results
curl -X POST http://localhost:8080/api/search/query \
  -H "Content-Type: application/json" \
  -d '{"question": "data", "maxResults": 3}'
```

Response:
```json
Response:
```json
[
  {
    "documentId": "789ed6f3-8965-4914-8d05-d6ffa8999987",
    "filename": "report.docx",
    "chunkIndex": 11,
    "score": 1.346,
    "highlights": [
      "The analysis shows <mark>data</mark> trends",
      "Key <mark>data</mark> points indicate growth",
      "Financial <mark>data</mark> from Q3"
    ]
  }
]
```

**Raw search** (for debugging - returns all chunks):
```bash
curl "http://localhost:9200/api/search/raw?q=report"
```

### 4. Check Elasticsearch Index

View indexed documents:
```bash
curl "http://localhost:9200/documents/_search?pretty"
```

View index mapping:
```bash
curl "http://localhost:9200/documents/_mapping?pretty"
```

Count documents:
```bash
curl "http://localhost:9200/documents/_count?pretty"
```
```

**Raw search** (for debugging - returns all chunks):
```bash
curl "http://localhost:8080/api/search/raw?q=Samuele"
```

**List indexed files**:
```bash
curl "http://localhost:8080/api/search/files"
```

### 4. Check Elasticsearch Index

View indexed documents:
```bash
curl "http://localhost:9200/documents/_search?pretty"
```

List unique indexed filenames:
```bash
curl -X GET "http://localhost:9200/documents/_search?pretty" -H 'Content-Type: application/json' -d'
{
  "size": 0,
  "aggs": {
    "unique_files": {
      "terms": {
        "field": "filename.keyword",
        "size": 1000
      }
    }
  }
}
'
```

View index mapping:
```bash
curl "http://localhost:9200/documents/_mapping?pretty"
```

Count documents:
```bash
curl "http://localhost:9200/documents/_count?pretty"
```

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Maximum file size
spring.servlet.multipart.max-file-size=10GB
spring.servlet.multipart.max-request-size=10GB

# Chunk size for large documents (characters per chunk)
document.chunk.size=5000

# Elasticsearch configuration
spring.elasticsearch.uris=http://localhost:9200
spring.elasticsearch.connection-timeout=30s
spring.elasticsearch.socket-timeout=60s

# Index name
document.index.name=documents

# Apache Camel File Polling (optional)
file-polling.enabled=false
file-polling.input-directory=./upload
file-polling.processed-directory=./processed
file-polling.error-directory=./errors
```

## Supported File Formats

Thanks to Apache Tika, the application automatically detects and extracts text from:

- **Documents**: PDF, DOC, DOCX, RTF, ODT, TXT
- **Spreadsheets**: XLS, XLSX, ODS, CSV
- **Web**: HTML, HTM, XML
- **Data**: JSON
- **Markup**: Markdown (MD)
- **And many more formats supported by Apache Tika**

## Automatic File Monitoring (Optional)

The application includes an **Apache Camel** route that can automatically process documents dropped in a monitored directory.

### Enable File Polling

1. Edit `application.properties`:
   ```properties
   file-polling.enabled=true
   ```

2. Restart the application

3. Drop document files in the `./upload` directory

4. Files will be automatically:
   - ✅ Processed and indexed in Elasticsearch
   - 📁 Moved to `./processed` directory on success
   - ❌ Moved to `./errors` directory on failure

### Configuration

```properties
# Enable/disable automatic file monitoring
file-polling.enabled=false

# Directory to monitor for new documents
file-polling.input-directory=./upload

# Where to move successfully processed files
file-polling.processed-directory=./processed

# Where to move files that failed processing
file-polling.error-directory=./errors

# Polling frequency in milliseconds (default: 5 seconds)
file-polling.delay=5000

# Maximum number of files processed concurrently
file-polling.max-concurrent=3
```

### How It Works

1. **Monitor**: Camel watches the input directory every 5 seconds
2. **Detect**: When a document appears, it's automatically picked up
3. **Process**: File is indexed in Elasticsearch with chunking
4. **Move**: Successfully processed files → `processed/`, errors → `errors/`
5. **Parallel**: Up to 3 files can be processed simultaneously

### Example Workflow

```bash
# Enable file polling
echo "file-polling.enabled=true" >> src/main/resources/application.properties

# Restart app
./start.sh

# Drop documents in the monitored directory
cp ~/Documents/report.pdf ./upload/
cp ~/Documents/data.xlsx ./upload/
cp ~/Documents/notes.txt ./upload/

# Check the logs
tail -f nohup.out | grep "📥 Nuovo"

# Files automatically processed and moved
ls processed/
# report.pdf data.xlsx notes.txt
```

## Architecture

### Large File Handling

1. **Upload** → File received via multipart upload
2. **Extraction** → Apache Tika extracts text (automatic format detection)
3. **Chunking** → Text split into chunks of ~5000 characters
4. **Indexing** → Each chunk indexed separately in Elasticsearch
5. **Search** → Query searches across all chunks, results grouped by document

### Components

- **DocumentService**: Handles document parsing, chunking, and asynchronous indexing
- **SearchService**: Full-text search with highlighting and result grouping
- **UploadController**: REST endpoints for upload and status tracking
- **SearchController**: REST endpoints for search
- **Document**: Elasticsearch entity with chunk support
- **AsyncConfig**: Thread pool configuration for async processing
- **Apache Tika**: Automatic format detection and text extraction

### Performance Tips

- **Chunk size**: Adjust `document.chunk.size` based on your needs (smaller = more precision, larger = fewer documents)
- **Thread pool**: Edit `AsyncConfig.java` to tune concurrent processing
- **Elasticsearch**: Increase heap size in `docker-compose.yml` for better performance with large datasets
- **JVM**: Use `-Xmx2g` or higher for processing very large files

## Limitations

- Maximum file size: 10GB (configurable)
- Supported formats: All formats supported by Apache Tika (PDF, DOC, DOCX, XLS, XLSX, TXT, HTML, etc.)
- In-memory status tracking (use Redis/DB for production)

## Production Recommendations

For production deployments:
- Replace in-memory `uploadStatusMap` with Redis or database
- Add authentication and authorization
- Implement rate limiting for uploads
- Add file validation and virus scanning
- Use persistent storage for uploaded files
- Configure Elasticsearch with proper security
- Add monitoring and logging
- Implement retry logic for failed processing
