# rynek-search-service

`search-service` — wyszukiwarka i filtry

> Status: **szkielet**. Wszystkie endpointy z tabeli istnieją w kontrolerach (`src/main/java/.../api/`) i zwracają `501 Not Implemented` (RFC 7807). Ciała żądań to puste rekordy w `api/dto/`.

## Uruchomienie

```bash
./mvnw spring-boot:run   # port 8087, zmienna PORT
./mvnw verify            # testy
```

- Swagger UI: http://localhost:8087/swagger-ui.html
- Zdrowie: http://localhost:8087/actuator/health

## Zakres i endpointy

_Skopiowane z `rynek-docs/architektura/rynek-endpointy-mikroserwisy.md`. Od teraz to repozytorium jest właścicielem tej specyfikacji._

Ekrany: 3 Wyniki wyszukiwania. Indeks Elasticsearch/OpenSearch budowany ze zdarzeń `offer.*` i `channel.*`.

| Metoda | Ścieżka | Opis | Dostęp |
|---|---|---|---|
| GET | `/api/v1/search/offers` | Wyniki; parametry: `q`, `category`, `type`, `priceMin`, `priceMax`, `city`, `radiusKm`, `condition`, `minRating`, `hasVideo`, `sort`, `cursor`. Odpowiedź zawiera liczniki filtrów (`facets`) i `total` | pub |
| GET | `/api/v1/search/suggest?q=` | Podpowiedzi zapytań | pub |
| GET | `/api/v1/search/channels?q=` | Wyszukiwanie kanałów sprzedawców | pub |
| GET | `/api/v1/search/locations?q=` | Podpowiedzi miejscowości (filtr lokalizacji) | pub |
| GET | `/api/v1/search/history` | Historia wyszukiwań | user |
| DELETE | `/api/v1/search/history` | Wyczyść historię | user |
| GET | `/api/v1/search/saved` | Zapisane wyszukiwania | user |
| POST | `/api/v1/search/saved` | Zapisz wyszukiwanie z powiadomieniami o nowych ofertach | user |
| DELETE | `/api/v1/search/saved/{savedSearchId}` | Usuń zapisane wyszukiwanie | user |
| POST | `/internal/search/reindex` | Pełna reindeksacja | int/admin |

## Zdarzenia Kafka

Wiersze z §17, w których występuje `search` (publikuje lub słucha).

| Zdarzenie | Publikuje | Słuchają |
|---|---|---|
| `channel.created`, `channel.updated` | channel | search, feed |
| `offer.published`, `offer.updated`, `offer.unpublished` | catalog | search, feed, cart, analytics |
| `video.synced`, `video.attached`, `video.removed` | tiktok-integration | catalog, feed, search |
| `review.created` | engagement | channel (ocena), search, notification |

## Dane i zależności

- **Magazyn danych:** Elasticsearch/OpenSearch
- **Zależności:** zdarzenia offer.*, channel.*, video.*, review.*
- **Kontrakty:** `rynek-contracts/openapi/` i `rynek-contracts/events/`
- **Lokalne uruchomienie:** `rynek-infra/local/`

## Kolejność budowy

Etap z §19: 3. `search-service`, `feed-service` — odkrywanie ofert.
