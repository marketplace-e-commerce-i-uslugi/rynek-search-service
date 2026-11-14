package pl.rynek.search.api;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.search.api.dto.SearchDtos.ChannelHit;
import pl.rynek.search.api.dto.SearchDtos.CreateSavedSearchRequest;
import pl.rynek.search.api.dto.SearchDtos.LocationHit;
import pl.rynek.search.api.dto.SearchDtos.SavedSearchResponse;
import pl.rynek.search.api.dto.SearchDtos.SearchResult;
import pl.rynek.search.config.Viewer;
import pl.rynek.search.history.HistoryService;
import pl.rynek.search.query.OfferFilters;
import pl.rynek.search.query.SearchService;
import pl.rynek.search.saved.SavedSearchService;

/**
 * Wyszukiwanie ofert, kanałów i miejscowości (publiczne) oraz historia i zapisane wyszukiwania (zalogowany użytkownik).
 */
@RestController
public class SearchController {

    private final SearchService search;
    private final HistoryService history;
    private final SavedSearchService saved;
    private final Viewer viewer;

    public SearchController(SearchService search, HistoryService history, SavedSearchService saved, Viewer viewer) {
        this.search = search;
        this.history = history;
        this.saved = saved;
        this.viewer = viewer;
    }

    /**
     * Wyniki z licznikami filtrów ({@code facets}) i liczbą wszystkich ({@code total}). Ceny {@code priceMin}/{@code priceMax}
     * w złotych. Zalogowanemu zapytanie ({@code q}) trafia do historii (tylko pierwsza strona).
     */
    @GetMapping("/api/v1/search/offers")
    public SearchResult searchOffers(HttpServletRequest request, @RequestParam(required = false) String q,
                                     @RequestParam(required = false) String category, @RequestParam(required = false) String type,
                                     @RequestParam(required = false) Long priceMin, @RequestParam(required = false) Long priceMax,
                                     @RequestParam(required = false) String city, @RequestParam(required = false) Integer radiusKm,
                                     @RequestParam(required = false) String condition, @RequestParam(required = false) Double minRating,
                                     @RequestParam(required = false) Boolean hasVideo, @RequestParam(required = false) String sort,
                                     @RequestParam(required = false) String cursor) {
        OfferFilters filters = new OfferFilters(q, category, type, priceMin, priceMax, city, radiusKm, condition, minRating, hasVideo, sort, cursor);
        SearchResult result = search.offers(filters);
        if (cursor == null || cursor.isBlank()) {
            viewer.of(request).ifPresent(user -> history.record(user, q));
        }
        return result;
    }

    @GetMapping("/api/v1/search/suggest")
    public List<String> suggest(@RequestParam(required = false) String q) {
        return search.suggest(q);
    }

    @GetMapping("/api/v1/search/channels")
    public List<ChannelHit> searchChannels(@RequestParam(required = false) String q) {
        return search.channels(q);
    }

    @GetMapping("/api/v1/search/locations")
    public List<LocationHit> searchLocations(@RequestParam(required = false) String q) {
        return search.locations(q);
    }

    /** 8 ostatnich zapytań zalogowanego użytkownika, od najnowszego. */
    @GetMapping("/api/v1/search/history")
    public List<String> getHistory(@AuthenticationPrincipal Jwt token) {
        return history.recent(user(token));
    }

    @DeleteMapping("/api/v1/search/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(@AuthenticationPrincipal Jwt token) {
        history.clear(user(token));
    }

    @GetMapping("/api/v1/search/saved")
    public List<SavedSearchResponse> listSavedSearches(@AuthenticationPrincipal Jwt token) {
        return saved.list(user(token));
    }

    /** Zapisuje wyszukiwanie (do 20). Powiadomienia o nowych ofertach ({@code notify}) są na razie tylko zapamiętywane. */
    @PostMapping("/api/v1/search/saved")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedSearchResponse createSavedSearch(@AuthenticationPrincipal Jwt token, @Valid @RequestBody CreateSavedSearchRequest request) {
        return saved.create(user(token), request.label(), request.filters(), Boolean.TRUE.equals(request.notifyOnNew()));
    }

    @DeleteMapping("/api/v1/search/saved/{savedSearchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSavedSearch(@AuthenticationPrincipal Jwt token, @PathVariable UUID savedSearchId) {
        saved.delete(user(token), savedSearchId);
    }

    private static UUID user(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
