package pl.rynek.search.query;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Filtry wyszukiwania ofert ({@code SearchFilters} z rynek-frontend). Ceny {@code priceMin}/{@code priceMax} są w złotych (tak je
 * wpisuje użytkownik), a w indeksie cena jest w groszach. {@code cursor} to nieprzezroczysty kursor poprzedniej strony.
 */
public record OfferFilters(String q, String category, String type, Long priceMin, Long priceMax, String city, Integer radiusKm,
                           String condition, Double minRating, Boolean hasVideo, String sort, String cursor) {

    private static final Set<String> TYPES = Set.of("PRODUCT", "SERVICE");
    private static final Set<String> SORTS = Set.of("relevance", "newest", "price_asc", "price_desc");

    /** Bez pustych napisów i z ujednoliconymi wartościami; błędne filtry to 400 po polsku. */
    public OfferFilters normalized() {
        String query = blankToNull(q);
        if (query != null && query.length() > 100) {
            throw bad("Zapytanie może mieć do 100 znaków.");
        }
        String kind = blankToNull(type);
        if (kind != null && !TYPES.contains(kind)) {
            throw bad("Typ oferty musi mieć wartość PRODUCT albo SERVICE.");
        }
        String order = blankToNull(sort);
        if (order != null && !SORTS.contains(order)) {
            throw bad("Sortowanie musi mieć wartość relevance, newest, price_asc albo price_desc.");
        }
        if (priceMin != null && priceMin < 0 || priceMax != null && priceMax < 0) {
            throw bad("Cena nie może być ujemna.");
        }
        if (priceMin != null && priceMax != null && priceMin > priceMax) {
            throw bad("Cena od nie może być większa niż cena do.");
        }
        if (radiusKm != null && (radiusKm < 1 || radiusKm > 500)) {
            throw bad("Promień musi mieścić się między 1 a 500 km.");
        }
        if (minRating != null && (minRating < 0 || minRating > 5)) {
            throw bad("Ocena musi mieścić się między 0 a 5.");
        }
        String where = blankToNull(city);
        if (where != null && where.length() > 60) {
            throw bad("Nazwa miejscowości jest za długa.");
        }
        return new OfferFilters(query, blankToNull(category), kind, priceMin, priceMax, where, radiusKm, blankToNull(condition), minRating,
                hasVideo, order == null ? "relevance" : order, blankToNull(cursor));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
