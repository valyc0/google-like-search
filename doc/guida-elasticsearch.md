# Guida Pratica a Elasticsearch

## Introduzione

Elasticsearch è un motore di ricerca e analisi distribuito, basato su Apache Lucene.
È utilizzato per ricerche full-text, analisi di log, metriche e molto altro.

## Concetti Fondamentali

### Indici
Un indice è una collezione di documenti che condividono caratteristiche simili.
È l'equivalente di un database nei sistemi relazionali.

### Documenti
I documenti sono le unità base di informazione che possono essere indicizzate.
Sono espressi in formato JSON.

### Mapping
Il mapping definisce come i documenti e i loro campi vengono memorizzati e indicizzati.

## Operazioni Base

### Creare un Indice
```bash
PUT /mio-indice
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 1
  }
}
```

### Indicizzare un Documento
```bash
POST /mio-indice/_doc/1
{
  "titolo": "Guida Elasticsearch",
  "autore": "Mario Rossi",
  "data": "2025-11-21"
}
```

### Effettuare una Ricerca
```bash
GET /mio-indice/_search
{
  "query": {
    "match": {
      "titolo": "elasticsearch"
    }
  }
}
```

## Best Practices

1. **Scegliere il numero corretto di shard** - Dipende dal volume di dati
2. **Utilizzare i tipi di campo appropriati** - Text per ricerca full-text, keyword per filtri
3. **Monitorare le performance** - Utilizzare gli strumenti di monitoring
4. **Implementare backup regolari** - Proteggere i dati con snapshot
5. **Ottimizzare le query** - Evitare query troppo generiche

## Conclusioni

Elasticsearch è uno strumento potente e versatile. Con la giusta configurazione
e ottimizzazione, può gestire enormi volumi di dati garantendo ricerche velocissime.
