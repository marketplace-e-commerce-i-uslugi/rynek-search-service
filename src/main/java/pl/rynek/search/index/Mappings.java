package pl.rynek.search.index;

/**
 * Ustawienia i mapowania indeksów. Polskie znaki są składane do ASCII (ł→l, ą→a…), więc „lodowka” znajduje „lodówkę”, a
 * pole {@code *.ngram} (początki słów) zasila podpowiedzi. Wyniki zwracamy z zapisanej karty ({@code card}), której nie
 * indeksujemy.
 */
public final class Mappings {

    public static final String OFFERS = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "analysis": {
                  "filter": { "prefixes": { "type": "edge_ngram", "min_gram": 2, "max_gram": 15 } },
                  "analyzer": {
                    "text_fold": { "tokenizer": "standard", "filter": ["lowercase", "asciifolding"] },
                    "prefix_fold": { "tokenizer": "standard", "filter": ["lowercase", "asciifolding", "prefixes"] }
                  }
                }
              },
              "mappings": {
                "dynamic": "strict",
                "properties": {
                  "id": { "type": "keyword" },
                  "type": { "type": "keyword" },
                  "title": { "type": "text", "analyzer": "text_fold",
                             "fields": { "prefix": { "type": "text", "analyzer": "prefix_fold", "search_analyzer": "text_fold" } } },
                  "description": { "type": "text", "analyzer": "text_fold" },
                  "params": { "type": "text", "analyzer": "text_fold" },
                  "categoryId": { "type": "keyword" },
                  "categoryIds": { "type": "keyword" },
                  "categoryPath": { "type": "text", "analyzer": "text_fold" },
                  "price": { "type": "long" },
                  "condition": { "type": "keyword" },
                  "locationText": { "type": "text", "analyzer": "text_fold" },
                  "cityKey": { "type": "keyword" },
                  "nationwide": { "type": "boolean" },
                  "geo": { "type": "geo_point" },
                  "channelId": { "type": "keyword" },
                  "channelName": { "type": "text", "analyzer": "text_fold" },
                  "rating": { "type": "float" },
                  "ratingCount": { "type": "integer" },
                  "hasVideo": { "type": "boolean" },
                  "available": { "type": "boolean" },
                  "createdAt": { "type": "date" },
                  "publishedAt": { "type": "date" },
                  "card": { "type": "object", "enabled": false }
                }
              }
            }
            """;

    public static final String CHANNELS = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "analysis": {
                  "filter": { "prefixes": { "type": "edge_ngram", "min_gram": 2, "max_gram": 15 } },
                  "analyzer": {
                    "text_fold": { "tokenizer": "standard", "filter": ["lowercase", "asciifolding"] },
                    "prefix_fold": { "tokenizer": "standard", "filter": ["lowercase", "asciifolding", "prefixes"] }
                  }
                }
              },
              "mappings": {
                "dynamic": "strict",
                "properties": {
                  "id": { "type": "keyword" },
                  "handle": { "type": "text", "analyzer": "text_fold" },
                  "name": { "type": "text", "analyzer": "text_fold",
                            "fields": { "prefix": { "type": "text", "analyzer": "prefix_fold", "search_analyzer": "text_fold" } } },
                  "description": { "type": "text", "analyzer": "text_fold" },
                  "verified": { "type": "boolean" },
                  "followersCount": { "type": "long" },
                  "offersCount": { "type": "integer" },
                  "syncId": { "type": "long" }
                }
              }
            }
            """;

    private Mappings() {
    }
}
