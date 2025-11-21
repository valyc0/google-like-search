# Esempi Pratici - Supporto Multi-Formato

Questa guida fornisce esempi pratici per l'utilizzo del sistema con diversi formati di documento.

## 📤 Upload di Documenti

### File di Testo (.txt)
```bash
# Upload di un file di testo semplice
curl -F "file=@notes.txt" http://localhost:8080/api/documents/upload

# Upload di log file
curl -F "file=@application.log" http://localhost:8080/api/documents/upload
```

### Documenti Word (.doc, .docx)
```bash
# Upload di documento Word
curl -F "file=@report.docx" http://localhost:8080/api/documents/upload

# Upload asincrono per documenti grandi
curl -F "file=@large-document.docx" http://localhost:8080/api/documents/upload-async
```

### Fogli di Calcolo Excel (.xls, .xlsx)
```bash
# Upload di foglio Excel
curl -F "file=@financial-data.xlsx" http://localhost:8080/api/documents/upload

# Upload di file CSV
curl -F "file=@sales-report.csv" http://localhost:8080/api/documents/upload
```

### Documenti PDF
```bash
# Upload di PDF
curl -F "file=@manual.pdf" http://localhost:8080/api/documents/upload

# Upload asincrono per PDF grandi
curl -F "file=@large-book.pdf" http://localhost:8080/api/documents/upload-async
```

### Pagine HTML
```bash
# Upload di pagina HTML
curl -F "file=@webpage.html" http://localhost:8080/api/documents/upload

# Upload di documentazione HTML
curl -F "file=@api-docs.htm" http://localhost:8080/api/documents/upload
```

### File Markdown
```bash
# Upload di README
curl -F "file=@README.md" http://localhost:8080/api/documents/upload

# Upload di documentazione Markdown
curl -F "file=@GUIDE.md" http://localhost:8080/api/documents/upload
```

### File XML e JSON
```bash
# Upload di configurazione XML
curl -F "file=@config.xml" http://localhost:8080/api/documents/upload

# Upload di dati JSON
curl -F "file=@data.json" http://localhost:8080/api/documents/upload
```

### OpenDocument (.odt, .ods)
```bash
# Upload di documento OpenDocument Text
curl -F "file=@document.odt" http://localhost:8080/api/documents/upload

# Upload di foglio OpenDocument
curl -F "file=@spreadsheet.ods" http://localhost:8080/api/documents/upload
```

## 🔍 Ricerca nei Documenti

### Ricerca Base
```bash
# Ricerca semplice
curl -X POST http://localhost:8080/api/search/query \
  -H "Content-Type: application/json" \
  -d '{"question": "financial report"}'

# Ricerca con limite risultati
curl -X POST http://localhost:8080/api/search/query \
  -H "Content-Type: application/json" \
  -d '{"question": "data analysis", "maxResults": 5}'
```

### Ricerca per Parole Chiave
```bash
# Ricerca tecnica
curl -X POST http://localhost:8080/api/search/query \
  -H "Content-Type: application/json" \
  -d '{"question": "API REST endpoint"}'

# Ricerca di codice
curl -X POST http://localhost:8080/api/search/query \
  -H "Content-Type: application/json" \
  -d '{"question": "function implementation"}'
```

### Ricerca di Dati Specifici
```bash
# Ricerca numerica
curl -X POST http://localhost:8080/api/search/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Q3 2024 revenue"}'

# Ricerca di nomi
curl -X POST http://localhost:8080/api/search/query \
  -H "Content-Type: application/json" \
  -d '{"question": "John Smith project manager"}'
```

## 🤖 Utilizzo con File Polling Automatico

### Configurazione
```bash
# 1. Abilita il file polling
echo "file-polling.enabled=true" >> src/main/resources/application.properties

# 2. Riavvia l'applicazione
./start.sh
```

### Utilizzo
```bash
# Prepara una directory con documenti misti
mkdir -p ./upload
cp ~/Documents/*.pdf ./upload/
cp ~/Documents/*.docx ./upload/
cp ~/Documents/*.xlsx ./upload/
cp ~/Documents/*.txt ./upload/

# I file verranno automaticamente:
# - Processati ed indicizzati
# - Spostati in ./processed/ se successo
# - Spostati in ./errors/ se errore

# Monitora l'elaborazione
tail -f nohup.out | grep "documento"
```

## 📊 Gestione dell'Indice Elasticsearch

### Statistiche
```bash
# Conta documenti totali
curl "http://localhost:9200/documents/_count?pretty"

# Visualizza mapping dell'indice
curl "http://localhost:9200/documents/_mapping?pretty"

# Statistiche dell'indice
curl "http://localhost:9200/documents/_stats?pretty"
```

### Ricerca Diretta in Elasticsearch
```bash
# Cerca tutti i documenti
curl "http://localhost:9200/documents/_search?pretty"

# Cerca per filename
curl -X POST "http://localhost:9200/documents/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "match": {
        "filename": "report"
      }
    }
  }'

# Cerca per content
curl -X POST "http://localhost:9200/documents/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "match": {
        "content": "financial data"
      }
    }
  }'
```

### Gestione Documenti
```bash
# Elimina tutti i documenti (usa con cautela!)
curl -X POST "http://localhost:9200/documents/_delete_by_query?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "match_all": {}
    }
  }'

# Elimina documenti specifici per filename
curl -X POST "http://localhost:9200/documents/_delete_by_query?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "match": {
        "filename": "old-report.pdf"
      }
    }
  }'
```

## 🧪 Test con Script Python

```python
import requests
import os

API_BASE = "http://localhost:8080/api"

def upload_document(file_path):
    """Upload un documento"""
    with open(file_path, 'rb') as f:
        files = {'file': f}
        response = requests.post(f"{API_BASE}/documents/upload", files=files)
        return response.json()

def search_documents(query, max_results=10):
    """Cerca nei documenti"""
    data = {
        "question": query,
        "maxResults": max_results
    }
    response = requests.post(f"{API_BASE}/search/query", json=data)
    return response.json()

# Esempio di utilizzo
if __name__ == "__main__":
    # Upload di diversi formati
    documents = [
        "report.pdf",
        "data.xlsx",
        "notes.txt",
        "presentation.pptx"
    ]
    
    for doc in documents:
        if os.path.exists(doc):
            result = upload_document(doc)
            print(f"Uploaded {doc}: {result.get('documentId')}")
    
    # Ricerca
    results = search_documents("financial analysis", max_results=5)
    for result in results:
        print(f"Found in {result['filename']}: score {result['score']}")
        for highlight in result['highlights'][:2]:
            print(f"  - {highlight}")
```

## 🔄 Batch Processing

### Script Bash per Upload Multipli
```bash
#!/bin/bash

# Upload di tutti i file in una directory
UPLOAD_DIR="/path/to/documents"
API_URL="http://localhost:8080/api/documents/upload"

for file in "$UPLOAD_DIR"/*; do
    if [ -f "$file" ]; then
        filename=$(basename "$file")
        echo "Uploading $filename..."
        
        response=$(curl -s -F "file=@$file" "$API_URL")
        
        if echo "$response" | grep -q "documentId"; then
            echo "✅ $filename uploaded successfully"
        else
            echo "❌ Failed to upload $filename"
        fi
        
        # Pausa per non sovraccaricare il server
        sleep 1
    fi
done
```

## 📈 Monitoraggio e Debugging

### Verifica Status Upload Asincrono
```bash
# Ottieni il documentId dall'upload
DOCUMENT_ID="123e4567-e89b-12d3-a456-426614174000"

# Verifica lo status
curl "http://localhost:8080/api/documents/status/$DOCUMENT_ID"

# Risposta esempio:
# {
#   "documentId": "123e4567-e89b-12d3-a456-426614174000",
#   "filename": "large-document.pdf",
#   "status": "COMPLETED",
#   "totalChunks": 50,
#   "processedChunks": 50,
#   "fileSize": 52428800,
#   "message": "Documento indicizzato con successo in 50 chunk"
# }
```

### Log Monitoring
```bash
# Monitora i log dell'applicazione
tail -f nohup.out

# Filtra per upload
tail -f nohup.out | grep "Upload"

# Filtra per errori
tail -f nohup.out | grep "ERROR"

# Monitora l'indicizzazione
tail -f nohup.out | grep "indicizzat"
```

## 💡 Best Practices

1. **Upload di File Grandi**
   - Usa l'endpoint `/upload-async` per file > 10MB
   - Monitora lo status con `/status/{documentId}`

2. **Ricerca Efficiente**
   - Limita il numero di risultati con `maxResults`
   - Usa termini di ricerca specifici per risultati migliori

3. **File Polling**
   - Configura `max-concurrent` in base alle risorse disponibili
   - Usa directory separate per input/processed/errors

4. **Manutenzione Indice**
   - Monitora la dimensione dell'indice Elasticsearch
   - Elimina documenti obsoleti periodicamente

5. **Performance**
   - Aumenta la memoria JVM per file molto grandi: `-Xmx2g`
   - Configura correttamente il chunk size in `application.properties`
