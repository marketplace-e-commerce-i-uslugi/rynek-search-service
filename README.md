# rynek-search-service

`search-service` — wyszukiwarka i filtry

> Status: **wyszukiwarka działa** (OpenSearch dla indeksu, PostgreSQL dla historii i zapisanych wyszukiwań, JWT z JWKS identity): wyniki z filtrami, sortowaniem, licznikami (`facets`) i kursorem, podpowiedzi, kanały, miejscowości, historia, zapisane wyszukiwania i reindeksacja. Indeks budują **zmiany pobierane z catalog i channel (bez Kafki)**. Powiadomienia o nowych ofertach dla zapisanych wyszukiwań (`notify`) są na razie tylko zapamiętywane.

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

## Działanie

- **Wyniki** (`GET /search/offers`): ceny `priceMin`/`priceMax` w złotych (indeks trzyma grosze), `sort`: `relevance` (domyślnie), `newest`, `price_asc`, `price_desc`, strona 20 wyników, `nextCursor` (kursor `search_after`, pusty na ostatniej stronie) i `total`. Odpowiedź ma karty ofert (`OfferCard`, te same co z catalog) oraz `facets`: `condition`, `type` i `category` (z kategoriami nadrzędnymi) liczone na bieżących wynikach. Wyprzedane produkty nie trafiają do wyników. Błędne filtry (nieznany `sort`/`type`, cena od większa niż do, promień poza 1–500 km, ocena poza 0–5, zapytanie dłuższe niż 100 znaków, zły kursor) dają 400 po polsku.
- **Tekst:** polskie znaki są składane do ASCII (`lodowka` znajduje „lodówkę”), tolerowane są literówki (do 2 zmian), a początek słowa pasuje (`galax`). Przeszukiwane są tytuł (najważniejszy), kategoria, nazwa kanału, opis i parametry. Przy dwóch słowach muszą pasować oba, przy dłuższym zapytaniu trzy czwarte.
- **Miejscowość:** `city` zwraca oferty z tej miejscowości i oferty „Cała Polska”; z `radiusKm` także oferty z miast w tym promieniu. Współrzędne pochodzą ze stałej tablicy ok. 55 większych miast (`Cities`), bo oferta ma tylko tekst lokalizacji: oferta w nieznanej miejscowości jest dopasowywana wyłącznie po tekście. `GET /search/locations` podpowiada miasta z tej tablicy.
- **Podpowiedzi** (`/suggest`): początki tytułów dostępnych ofert, skrócone przed przecinkiem lub myślnikiem, bez powtórzeń, do 6; od 2 znaków. **Kanały** (`/channels`): po nazwie lub handle, bez tekstu najpopularniejsze (po liczbie obserwujących).
- **Historia:** zalogowanemu zapytanie (`q`) z pierwszej strony trafia do historii (8 ostatnich na liście, 20 w pamięci, bez powtórzeń z uwzględnieniem wielkości liter i polskich znaków). Gość nic nie zapisuje, a zły token to gość, nie 401.
- **Zapisane wyszukiwania:** do 20 na użytkownika (409 powyżej). Zapisujemy tylko znane filtry bez kursora, sprawdzone tak jak przy wyszukiwaniu; samo sortowanie nie jest wyszukiwaniem (400). Cudze wygląda jak nieistniejące (404).
- **Synchronizacja z catalog i channel (zamiast zdarzeń):** co 15 s (`sync-interval`) search-service pobiera z catalog zmiany po kursorze `(updatedAt, id)` (`GET /internal/offers/search-sync`): opublikowanie, edycja, wstrzymanie, usunięcie i wyczerpanie albo powrót zapasu dają dokument albo „nagrobek”. Przy starcie i co 10 min (`reindex-interval`) buduje nowy indeks i przełącza na niego alias atomowo, co odświeża oceny (nie zmieniają oferty), usuwa to, czego catalog już nie zna, i nie przerywa wyszukiwania. Kanały co minutę w całości (`GET /internal/channels/search-sync`): liczba obserwujących i weryfikacja zmieniają się bez zmiany kanału. Zwykła sprzedaż (zapas nie spada do zera) nie przestawia oferty. Opóźnienie widoczności zmiany to do kilkunastu sekund (oceny i usunięcia bez „nagrobka” do 10 minut); trwała dostawa wymaga Kafki.
## Zdarzenia Kafka

Wiersze z §17, w których występuje `search` (publikuje lub słucha).

| Zdarzenie | Publikuje | Słuchają |
|---|---|---|
| `channel.created`, `channel.updated` | channel | search, feed |
| `offer.published`, `offer.updated`, `offer.unpublished` | catalog | search, feed, cart, analytics |
| `video.synced`, `video.attached`, `video.removed` | tiktok-integration | catalog, feed, search |
| `review.created` | engagement | channel (ocena), search, notification |

## Dane i zależności

- **Magazyn danych:** OpenSearch (indeksy `offers` jako alias i `channels`) oraz PostgreSQL (baza `search`: historia, zapisane wyszukiwania)
- **Zależności:** catalog-service (`GET /internal/offers/search-sync`), channel-service (`GET /internal/channels/search-sync`), identity-service (klucze JWKS); docelowo zdarzenia offer.*, channel.*, video.*, review.*
- **Kontrakty:** `rynek-contracts/openapi/` i `rynek-contracts/events/`
- **Lokalne uruchomienie:** `rynek-infra/local/`

## Kolejność budowy

Etap z §19: 3. `search-service`, `feed-service` — odkrywanie ofert.
