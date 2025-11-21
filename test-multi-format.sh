#!/bin/bash

# Script di test per verificare il supporto multi-formato

API_BASE="http://localhost:8080/api"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "Test Multi-Format Document Support"
echo "=========================================="
echo ""

# Verifica che l'applicazione sia in esecuzione
echo "Verifico che l'applicazione sia attiva..."
if ! curl -s "${API_BASE}/search/raw?q=test" > /dev/null 2>&1; then
    echo -e "${RED}❌ L'applicazione non risponde. Avvia prima ./start.sh${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Applicazione attiva${NC}"
echo ""

# Crea directory di test
TEST_DIR="/tmp/document-test"
mkdir -p "$TEST_DIR"
cd "$TEST_DIR"

echo "Creo file di test in vari formati..."

# 1. Crea un file TXT
echo -e "${YELLOW}1. Creazione file TXT...${NC}"
cat > test-document.txt << 'EOF'
This is a test document for multi-format support.
It contains important information about data analysis.
The financial report shows positive trends.
EOF
echo -e "${GREEN}✅ test-document.txt creato${NC}"

# 2. Crea un file HTML
echo -e "${YELLOW}2. Creazione file HTML...${NC}"
cat > test-page.html << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>Test Document</title>
</head>
<body>
    <h1>Test Page</h1>
    <p>This is a test HTML page for document indexing.</p>
    <p>Contains keywords: innovation, technology, development.</p>
</body>
</html>
EOF
echo -e "${GREEN}✅ test-page.html creato${NC}"

# 3. Crea un file CSV
echo -e "${YELLOW}3. Creazione file CSV...${NC}"
cat > test-data.csv << 'EOF'
Name,Value,Description
Product A,1000,High performance product
Product B,2000,Enterprise solution
Product C,1500,Budget friendly option
EOF
echo -e "${GREEN}✅ test-data.csv creato${NC}"

# 4. Crea un file Markdown
echo -e "${YELLOW}4. Creazione file Markdown...${NC}"
cat > test-readme.md << 'EOF'
# Test Document

This is a test markdown document.

## Features
- Multi-format support
- Full-text search
- Document chunking

## Keywords
Research, development, innovation
EOF
echo -e "${GREEN}✅ test-readme.md creato${NC}"

# 5. Crea un file XML
echo -e "${YELLOW}5. Creazione file XML...${NC}"
cat > test-data.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<document>
    <title>Test XML Document</title>
    <content>This is a test XML file with structured data.</content>
    <keywords>xml, data, structure, testing</keywords>
</document>
EOF
echo -e "${GREEN}✅ test-data.xml creato${NC}"

echo ""
echo "=========================================="
echo "Upload dei documenti"
echo "=========================================="
echo ""

# Funzione per upload e verifica
upload_file() {
    local file=$1
    local format=$2
    
    echo -e "${YELLOW}Upload di $file ($format)...${NC}"
    response=$(curl -s -F "file=@$file" "${API_BASE}/documents/upload")
    
    if echo "$response" | grep -q "documentId"; then
        doc_id=$(echo "$response" | grep -o '"documentId":"[^"]*"' | cut -d'"' -f4)
        echo -e "${GREEN}✅ $format caricato con successo - ID: $doc_id${NC}"
        return 0
    else
        echo -e "${RED}❌ Errore upload $format${NC}"
        echo "Risposta: $response"
        return 1
    fi
}

# Upload dei file
upload_file "test-document.txt" "TXT"
sleep 2
upload_file "test-page.html" "HTML"
sleep 2
upload_file "test-data.csv" "CSV"
sleep 2
upload_file "test-readme.md" "Markdown"
sleep 2
upload_file "test-data.xml" "XML"

echo ""
echo "Attendo l'indicizzazione..."
sleep 5

echo ""
echo "=========================================="
echo "Test di Ricerca"
echo "=========================================="
echo ""

# Funzione per test di ricerca
search_test() {
    local query=$1
    local description=$2
    
    echo -e "${YELLOW}Ricerca: \"$query\" ($description)${NC}"
    response=$(curl -s -X POST "${API_BASE}/search/query" \
        -H "Content-Type: application/json" \
        -d "{\"question\": \"$query\", \"maxResults\": 5}")
    
    if echo "$response" | grep -q "documentId"; then
        count=$(echo "$response" | grep -o '"documentId"' | wc -l)
        echo -e "${GREEN}✅ Trovati $count risultati${NC}"
        # Mostra i primi 3 highlights
        echo "$response" | grep -o '"highlights":\[[^]]*\]' | head -1
    else
        echo -e "${RED}❌ Nessun risultato trovato${NC}"
    fi
    echo ""
}

# Test di ricerca
search_test "financial report" "Ricerca in TXT"
search_test "innovation technology" "Ricerca in HTML/Markdown"
search_test "Product A" "Ricerca in CSV"
search_test "xml structure" "Ricerca in XML"
search_test "data" "Ricerca generica multi-formato"

echo ""
echo "=========================================="
echo "Verifica Indice Elasticsearch"
echo "=========================================="
echo ""

echo "Numero totale di documenti indicizzati:"
curl -s "http://localhost:9200/documents/_count?pretty" | grep count

echo ""
echo "Elenco documenti nell'indice:"
curl -s "http://localhost:9200/documents/_search?pretty&size=100" | grep '"filename"' | sort | uniq

echo ""
echo "=========================================="
echo "Test completato!"
echo "=========================================="
echo ""
echo "File di test creati in: $TEST_DIR"
echo "Per eliminare i file di test: rm -rf $TEST_DIR"
echo ""
