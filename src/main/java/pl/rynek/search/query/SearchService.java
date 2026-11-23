package pl.rynek.search.query;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.search.api.dto.SearchDtos.ChannelHit;
import pl.rynek.search.api.dto.SearchDtos.FacetValue;
import pl.rynek.search.api.dto.SearchDtos.LocationHit;
import pl.rynek.search.api.dto.SearchDtos.SearchResult;
import pl.rynek.search.index.Cities;
import pl.rynek.search.index.OpenSearch;
import pl.rynek.search.sync.IndexSync;
import tools.jackson.databind.json.JsonMapper;

/** Wyszukiwanie ofert i kanałów w indeksie OpenSearch oraz podpowiedzi zapytań i miejscowości. */
@Service
public class SearchService {

    private static final int SUGGESTIONS = 6;

    private final OpenSearch search;
    private final JsonMapper json;
    static final int MAX_PAGE_SIZE = 50;

    private final int pageSize;

    public SearchService(OpenSearch search, JsonMapper json, @Value("${rynek.search.page-size:20}") int pageSize) {
        this.search = search;
        this.json = json;
        this.pageSize = pageSize;
    }

    public SearchResult offers(OfferFilters raw) {
        return offers(raw, null);
    }

    /** {@code limit}: wyników na stronę, od 1 do {@link #MAX_PAGE_SIZE}; brak oznacza domyślny rozmiar strony. */
    public SearchResult offers(OfferFilters raw, Integer limit) {
        if (limit != null && (limit < 1 || limit > MAX_PAGE_SIZE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit wyników to liczba od 1 do " + MAX_PAGE_SIZE + ".");
        }
        int pageSize = limit == null ? this.pageSize : limit;
        OfferFilters filters = raw.normalized();
        // Pobieramy o jeden wynik więcej: jeśli jest, istnieje następna strona.
        Map<String, Object> response = search.search(IndexSync.OFFERS, OfferQuery.build(filters, pageSize + 1, decode(filters.cursor())));
        Map<String, Object> hits = map(response.get("hits"));
        List<Map<String, Object>> list = maps(hits.get("hits"));
        long total = ((Number) map(hits.get("total")).get("value")).longValue();
        boolean more = list.size() > pageSize;
        List<Map<String, Object>> items = list.stream().limit(pageSize).map(hit -> map(map(hit.get("_source")).get("card"))).toList();
        String next = more ? encode(list.get(pageSize - 1).get("sort")) : null;
        return new SearchResult(items, next, total, facets(map(response.get("aggregations"))));
    }

    /** Początki tytułów ofert pasujące do wpisanego tekstu: bez powtórzeń, od najlepiej dopasowanych. */
    public List<String> suggest(String q) {
        String text = q == null ? "" : q.trim();
        if (text.length() < 2 || text.length() > 100) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", 20);
        body.put("_source", List.of("card.title"));
        body.put("query", Map.of("bool", Map.of(
                "filter", List.of(Map.of("term", Map.of("available", true))),
                "must", List.of(Map.of("match", Map.of("title.prefix", Map.of("query", text, "operator", "and")))))));
        Map<String, Object> response = search.search(IndexSync.OFFERS, body);
        Set<String> folded = new LinkedHashSet<>();
        List<String> suggestions = new ArrayList<>();
        for (Map<String, Object> hit : maps(map(response.get("hits")).get("hits"))) {
            Object title = map(map(hit.get("_source")).get("card")).get("title");
            String clean = title instanceof String s ? shorten(s) : "";
            if (!clean.isEmpty() && folded.add(Cities.fold(clean))) {
                suggestions.add(clean);
                if (suggestions.size() == SUGGESTIONS) {
                    break;
                }
            }
        }
        return suggestions;
    }

    /** Kanały po nazwie lub handle; bez tekstu najpopularniejsze (po liczbie obserwujących). */
    public List<ChannelHit> channels(String q) {
        String text = q == null ? "" : q.trim();
        if (text.length() > 100) {
            return List.of();
        }
        Map<String, Object> query = text.isEmpty()
                ? Map.of("match_all", Map.of())
                : Map.of("bool", Map.of("should", List.of(
                        Map.of("match", Map.of("name", Map.of("query", text, "boost", 3, "fuzziness", "AUTO"))),
                        Map.of("match", Map.of("name.prefix", Map.of("query", text, "operator", "and"))),
                        Map.of("match", Map.of("handle", Map.of("query", text, "boost", 2)))), "minimum_should_match", 1));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", 10);
        body.put("query", query);
        body.put("sort", List.of(Map.of("_score", Map.of("order", "desc")), Map.of("followersCount", Map.of("order", "desc")), Map.of("id", Map.of("order", "asc"))));
        Map<String, Object> response = search.search(IndexSync.CHANNELS, body);
        return maps(map(response.get("hits")).get("hits")).stream().map(hit -> {
            Map<String, Object> source = map(hit.get("_source"));
            return new ChannelHit((String) source.get("id"), (String) source.get("handle"), (String) source.get("name"),
                    ((Number) source.get("followersCount")).longValue(), Boolean.TRUE.equals(source.get("verified")));
        }).toList();
    }

    public List<LocationHit> locations(String q) {
        return Cities.startingWith(q == null ? "" : q, 8).stream().map(city -> new LocationHit(city.name(), city.region())).toList();
    }

    private Map<String, List<FacetValue>> facets(Map<String, Object> aggregations) {
        Map<String, List<FacetValue>> facets = new LinkedHashMap<>();
        aggregations.forEach((name, aggregation) -> facets.put(name, maps(map(aggregation).get("buckets")).stream()
                .map(bucket -> new FacetValue(String.valueOf(bucket.get("key")), ((Number) bucket.get("doc_count")).longValue())).toList()));
        return facets;
    }

    /** Tytuł do podpowiedzi: bez dopisków po przecinku albo myślniku, do 60 znaków. */
    private static String shorten(String title) {
        String head = title.split("\\s[—–-]\\s|,", 2)[0].trim();
        return head.length() > 60 ? head.substring(0, 60).trim() : head;
    }

    private String encode(Object sortValues) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(sortValues));
    }

    private List<Object> decode(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            List<Object> values = json.readValue(Base64.getUrlDecoder().decode(cursor), List.class);
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException();
            }
            return values;
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowy kursor. Zacznij od pierwszej strony.");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        return value instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }
}
