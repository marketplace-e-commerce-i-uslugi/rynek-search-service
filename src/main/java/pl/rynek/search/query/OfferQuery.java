package pl.rynek.search.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pl.rynek.search.index.Cities;
import pl.rynek.search.index.Cities.City;

/** Buduje zapytanie OpenSearch (DSL jako mapy) z filtrów wyszukiwania ofert. */
final class OfferQuery {

    private OfferQuery() {
    }

    /**
     * @param size        liczba wyników na stronę (pobieramy o jeden więcej, żeby wiedzieć, czy jest następna)
     * @param searchAfter wartości sortowania ostatniego wyniku poprzedniej strony albo {@code null}
     */
    static Map<String, Object> build(OfferFilters f, int size, List<Object> searchAfter) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", size);
        body.put("track_total_hits", true);
        body.put("_source", List.of("card"));
        body.put("query", Map.of("bool", bool(f)));
        body.put("sort", sort(f.sort()));
        body.put("aggs", aggregations());
        if (searchAfter != null) {
            body.put("search_after", searchAfter);
        }
        return body;
    }

    private static Map<String, Object> bool(OfferFilters f) {
        List<Object> filter = new ArrayList<>();
        // Wyprzedane produkty nie trafiają do wyników: nie da się ich kupić.
        filter.add(term("available", true));
        if (f.type() != null) {
            filter.add(term("type", f.type()));
        }
        if (f.category() != null) {
            filter.add(term("categoryIds", f.category()));
        }
        if (f.condition() != null) {
            filter.add(term("condition", f.condition()));
        }
        if (f.priceMin() != null || f.priceMax() != null) {
            Map<String, Object> range = new LinkedHashMap<>();
            if (f.priceMin() != null) {
                range.put("gte", f.priceMin() * 100);
            }
            if (f.priceMax() != null) {
                range.put("lte", f.priceMax() * 100);
            }
            filter.add(Map.of("range", Map.of("price", range)));
        }
        if (f.minRating() != null && f.minRating() > 0) {
            // Oferty bez żadnej oceny nie mają „co najmniej 4,0”.
            filter.add(Map.of("range", Map.of("rating", Map.of("gte", f.minRating()))));
            filter.add(Map.of("range", Map.of("ratingCount", Map.of("gte", 1))));
        }
        if (Boolean.TRUE.equals(f.hasVideo())) {
            filter.add(term("hasVideo", true));
        }
        if (f.city() != null) {
            filter.add(city(f.city(), f.radiusKm()));
        }
        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("filter", filter);
        if (f.q() != null) {
            bool.put("must", List.of(text(f.q())));
        }
        return bool;
    }

    /**
     * Tekst: tytuł najważniejszy, potem kategoria i kanał, opis i parametry; literówki (do 2 zmian) są tolerowane. Przy dwóch
     * słowach muszą pasować oba, przy dłuższym zapytaniu trzy czwarte.
     */
    private static Map<String, Object> text(String q) {
        Map<String, Object> multi = new LinkedHashMap<>();
        multi.put("query", q);
        multi.put("fields", List.of("title^4", "title.prefix", "categoryPath^2", "channelName^2", "description", "params"));
        multi.put("type", "most_fields");
        multi.put("fuzziness", "AUTO");
        multi.put("prefix_length", 1);
        multi.put("minimum_should_match", "2<75%");
        return Map.of("multi_match", multi);
    }

    /**
     * Miejscowość: oferty z tej miejscowości i oferty „Cała Polska”. Dla znanego miasta z promieniem także oferty z miast w tym
     * promieniu, a dla nieznanej miejscowości dopasowanie po tekście lokalizacji.
     */
    private static Map<String, Object> city(String city, Integer radiusKm) {
        List<Object> should = new ArrayList<>();
        should.add(term("nationwide", true));
        Optional<City> known = Cities.exact(city);
        if (known.isPresent()) {
            should.add(term("cityKey", known.get().key()));
            if (radiusKm != null) {
                should.add(Map.of("geo_distance", Map.of("distance", radiusKm + "km", "geo", Map.of("lat", known.get().lat(), "lon", known.get().lon()))));
            }
        } else {
            should.add(Map.of("match_phrase", Map.of("locationText", city)));
        }
        return Map.of("bool", Map.of("should", should, "minimum_should_match", 1));
    }

    /** Sortowanie z rozstrzygnięciem po id, żeby kursor ({@code search_after}) był stabilny. */
    private static List<Object> sort(String sort) {
        return switch (sort) {
            case "newest" -> List.of(desc("publishedAt"), asc("id"));
            case "price_asc" -> List.of(asc("price"), desc("publishedAt"), asc("id"));
            case "price_desc" -> List.of(desc("price"), desc("publishedAt"), asc("id"));
            default -> List.of(desc("_score"), desc("publishedAt"), asc("id"));
        };
    }

    /** Liczniki filtrów liczone na bieżących wynikach: stan, rodzaj i kategorie (z nadrzędnymi). */
    private static Map<String, Object> aggregations() {
        Map<String, Object> aggs = new LinkedHashMap<>();
        aggs.put("condition", Map.of("terms", Map.of("field", "condition", "size", 10)));
        aggs.put("type", Map.of("terms", Map.of("field", "type", "size", 5)));
        aggs.put("category", Map.of("terms", Map.of("field", "categoryIds", "size", 50)));
        return aggs;
    }

    private static Map<String, Object> term(String field, Object value) {
        return Map.of("term", Map.of(field, value));
    }

    private static Map<String, Object> desc(String field) {
        return Map.of(field, Map.of("order", "desc"));
    }

    private static Map<String, Object> asc(String field) {
        return Map.of(field, Map.of("order", "asc"));
    }
}
