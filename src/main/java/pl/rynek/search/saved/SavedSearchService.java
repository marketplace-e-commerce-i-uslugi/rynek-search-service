package pl.rynek.search.saved;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.search.api.dto.SearchDtos.SavedSearchResponse;
import pl.rynek.search.query.OfferFilters;
import tools.jackson.databind.json.JsonMapper;

/**
 * Zapisane wyszukiwania: do 20 na użytkownika. Filtry zapisujemy tylko znane, bez kursora, i sprawdzamy tak jak przy
 * wyszukiwaniu, więc nie da się zapisać wyszukiwania, którego nie dałoby się wykonać.
 */
@Service
public class SavedSearchService {

    static final int MAX_PER_USER = 20;
    private static final Set<String> KEYS = Set.of("q", "category", "type", "priceMin", "priceMax", "city", "radiusKm", "condition",
            "minRating", "hasVideo", "sort");

    private final SavedSearchRepository saved;
    private final JsonMapper json;
    private final Clock clock;

    public SavedSearchService(SavedSearchRepository saved, JsonMapper json, Clock clock) {
        this.saved = saved;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SavedSearchResponse> list(UUID userId) {
        return saved.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList();
    }

    @Transactional
    public SavedSearchResponse create(UUID userId, String label, Map<String, Object> filters, boolean notify) {
        Map<String, Object> clean = sanitize(filters);
        if (clean.keySet().stream().allMatch("sort"::equals)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wpisz zapytanie albo ustaw filtry, zanim zapiszesz wyszukiwanie.");
        }
        validate(clean);
        if (saved.countByUserId(userId) >= MAX_PER_USER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Możesz zapisać najwyżej " + MAX_PER_USER + " wyszukiwań. Usuń któreś, żeby dodać nowe.");
        }
        return view(saved.save(new SavedSearch(userId, label.trim(), json.writeValueAsString(clean), notify, clock.instant())));
    }

    @Transactional
    public void delete(UUID userId, UUID savedSearchId) {
        if (saved.deleteOwned(savedSearchId, userId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy takiego zapisanego wyszukiwania.");
        }
    }

    private SavedSearchResponse view(SavedSearch s) {
        @SuppressWarnings("unchecked")
        Map<String, Object> filters = json.readValue(s.getFilters(), Map.class);
        return new SavedSearchResponse(s.getId(), s.getLabel(), filters, s.isNotify());
    }

    /** Tylko znane filtry z prostymi wartościami (tekst, liczba, tak/nie), bez pustych. */
    private static Map<String, Object> sanitize(Map<String, Object> filters) {
        Map<String, Object> clean = new LinkedHashMap<>();
        filters.forEach((key, value) -> {
            if (!KEYS.contains(key)) {
                return;
            }
            if (value instanceof String s && !s.isBlank() && s.length() <= 100) {
                clean.put(key, s.trim());
            } else if (value instanceof Number || value instanceof Boolean) {
                clean.put(key, value);
            }
        });
        return clean;
    }

    /** Te same reguły co przy wyszukiwaniu (błędne filtry dają 400 po polsku). */
    private static void validate(Map<String, Object> f) {
        try {
            new OfferFilters(text(f, "q"), text(f, "category"), text(f, "type"), number(f, "priceMin"), number(f, "priceMax"), text(f, "city"),
                    number(f, "radiusKm") == null ? null : number(f, "radiusKm").intValue(), text(f, "condition"), decimal(f, "minRating"),
                    f.get("hasVideo") instanceof Boolean b ? b : null, text(f, "sort"), null).normalized();
        } catch (ClassCastException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtry wyszukiwania mają nieprawidłowy format.");
        }
    }

    private static String text(Map<String, Object> f, String key) {
        return f.get(key) instanceof String s ? s : null;
    }

    private static Long number(Map<String, Object> f, String key) {
        return f.get(key) instanceof Number n ? n.longValue() : null;
    }

    private static Double decimal(Map<String, Object> f, String key) {
        return f.get(key) instanceof Number n ? n.doubleValue() : null;
    }
}
