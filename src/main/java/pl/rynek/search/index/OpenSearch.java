package pl.rynek.search.index;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cienki klient REST do OpenSearch (bez biblioteki klienckiej): tylko operacje, których potrzebuje search-service. Błędy
 * połączenia z zapytań użytkownika wracają jako 503, a w synchronizacji w tle jako wyjątek do zalogowania.
 */
@Component
public class OpenSearch {

    private static final Logger log = LoggerFactory.getLogger(OpenSearch.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    private final RestClient http;
    private final JsonMapper json;

    public OpenSearch(@Value("${rynek.opensearch.url}") String url, JsonMapper json) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder().baseUrl(url).requestFactory(requests).build();
        this.json = json;
    }

    /** Operacja zbiorcza: zapis dokumentu albo usunięcie po id. */
    public record BulkOp(String id, Map<String, Object> document) {

        public static BulkOp index(String id, Map<String, Object> document) {
            return new BulkOp(id, document);
        }

        public static BulkOp delete(String id) {
            return new BulkOp(id, null);
        }
    }

    public boolean exists(String index) {
        return Boolean.TRUE.equals(http.head().uri("/{index}", index).exchange((request, response) -> response.getStatusCode().is2xxSuccessful()));
    }

    public void createIndex(String index, String settingsAndMappings) {
        http.put().uri("/{index}", index).contentType(MediaType.APPLICATION_JSON).body(settingsAndMappings).retrieve().toBodilessEntity();
    }

    public void deleteIndex(String index) {
        try {
            http.delete().uri("/{index}", index).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (!e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw e;
            }
        }
    }

    public void refresh(String index) {
        http.post().uri("/{index}/_refresh", index).retrieve().toBodilessEntity();
    }

    /** Wynik zapytania; gdy OpenSearch nie odpowiada: 503 (wyszukiwanie użytkownika nie ma sensu bez indeksu). */
    public Map<String, Object> search(String index, Map<String, Object> body) {
        try {
            return http.post().uri("/{index}/_search", index).contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body))
                    .retrieve().body(MAP);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                // Indeks jeszcze nie powstał (pierwsza synchronizacja): puste wyniki zamiast błędu.
                return Map.of("hits", Map.of("total", Map.of("value", 0), "hits", List.of()));
            }
            log.warn("OpenSearch odrzucił zapytanie ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw unavailable();
        } catch (RuntimeException e) {
            log.warn("Brak odpowiedzi OpenSearch: {}", e.getMessage());
            throw unavailable();
        }
    }

    /** Zapisuje i usuwa dokumenty jedną paczką. Brak usuwanego dokumentu nie jest błędem. */
    public void bulk(String index, List<BulkOp> operations) {
        if (operations.isEmpty()) {
            return;
        }
        StringBuilder lines = new StringBuilder();
        for (BulkOp op : operations) {
            if (op.document() == null) {
                lines.append(json.writeValueAsString(Map.of("delete", Map.of("_index", index, "_id", op.id())))).append('\n');
            } else {
                lines.append(json.writeValueAsString(Map.of("index", Map.of("_index", index, "_id", op.id())))).append('\n')
                        .append(json.writeValueAsString(op.document())).append('\n');
            }
        }
        Map<String, Object> response = http.post().uri("/_bulk").contentType(MediaType.parseMediaType("application/x-ndjson;charset=UTF-8"))
                .body(lines.toString()).retrieve().body(MAP);
        if (response != null && Boolean.TRUE.equals(response.get("errors"))) {
            List<String> failures = new ArrayList<>();
            for (Object item : (List<?>) response.get("items")) {
                Map<?, ?> action = (Map<?, ?>) ((Map<?, ?>) item).values().iterator().next();
                int status = ((Number) action.get("status")).intValue();
                if (status >= 400 && status != 404) {
                    failures.add(action.get("_id") + ": " + action.get("error"));
                }
            }
            if (!failures.isEmpty()) {
                throw new IllegalStateException("OpenSearch odrzucił część paczki: " + failures.stream().limit(3).toList());
            }
        }
    }

    /** Indeksy, na które wskazuje alias (pusta lista, gdy aliasu nie ma). */
    public List<String> aliasTargets(String alias) {
        Map<String, Object> response = http.get().uri("/_alias/{alias}", alias).exchange((request, r) ->
                r.getStatusCode().is2xxSuccessful() ? r.bodyTo(MAP) : null);
        return response == null ? List.of() : List.copyOf(response.keySet());
    }

    /** Przełącza alias atomowo na nowy indeks i usuwa stare, więc wyszukiwanie nie widzi stanu pośredniego. */
    public void swapAlias(String alias, String newIndex) {
        List<String> old = aliasTargets(alias);
        List<Map<String, Object>> actions = new ArrayList<>();
        old.forEach(index -> actions.add(Map.of("remove", Map.of("index", index, "alias", alias))));
        actions.add(Map.of("add", Map.of("index", newIndex, "alias", alias)));
        http.post().uri("/_aliases").contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(Map.of("actions", actions)))
                .retrieve().toBodilessEntity();
        old.forEach(this::deleteIndex);
    }

    /** Usuwa dokumenty pasujące do zapytania i zwraca ich liczbę. */
    public long deleteByQuery(String index, Map<String, Object> query) {
        Map<String, Object> response = http.post().uri("/{index}/_delete_by_query?refresh=true&conflicts=proceed", index)
                .contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(Map.of("query", query))).retrieve().body(MAP);
        return response == null || !(response.get("deleted") instanceof Number n) ? 0 : n.longValue();
    }

    public long count(String index) {
        try {
            Map<String, Object> response = http.get().uri("/{index}/_count", index).retrieve().body(MAP);
            return response == null ? 0 : ((Number) response.get("count")).longValue();
        } catch (RestClientResponseException e) {
            return 0;
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Wyszukiwarka jest chwilowo niedostępna. Spróbuj ponownie za chwilę.");
    }
}
