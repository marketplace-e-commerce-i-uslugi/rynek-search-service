package pl.rynek.search.api;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.search.api.dto.CreateSavedSearchRequest;

import static pl.rynek.search.common.NotImplemented.notImplemented;

@RestController
public class SearchController {

    /**
     * Wyniki; parametry: q, category, type, priceMin, priceMax, city, radiusKm, condition, minRating, hasVideo, sort, cursor. Odpowiedź zawiera liczniki filtrów (facets) i total. Dostęp: pub.
     */
    @GetMapping("/api/v1/search/offers")
    public void searchOffers(@RequestParam(required = false) String q, @RequestParam(required = false) String category, @RequestParam(required = false) String type, @RequestParam(required = false) String priceMin, @RequestParam(required = false) String priceMax, @RequestParam(required = false) String city, @RequestParam(required = false) String radiusKm, @RequestParam(required = false) String condition, @RequestParam(required = false) String minRating, @RequestParam(required = false) String hasVideo, @RequestParam(required = false) String sort, @RequestParam(required = false) String cursor) {
        throw notImplemented();
    }

    /**
     * Podpowiedzi zapytań. Dostęp: pub.
     */
    @GetMapping("/api/v1/search/suggest")
    public void suggest(@RequestParam(required = false) String q) {
        throw notImplemented();
    }

    /**
     * Wyszukiwanie kanałów sprzedawców. Dostęp: pub.
     */
    @GetMapping("/api/v1/search/channels")
    public void searchChannels(@RequestParam(required = false) String q) {
        throw notImplemented();
    }

    /**
     * Podpowiedzi miejscowości (filtr lokalizacji). Dostęp: pub.
     */
    @GetMapping("/api/v1/search/locations")
    public void searchLocations(@RequestParam(required = false) String q) {
        throw notImplemented();
    }

    /**
     * Historia wyszukiwań. Dostęp: user.
     */
    @GetMapping("/api/v1/search/history")
    public void getHistory() {
        throw notImplemented();
    }

    /**
     * Wyczyść historię. Dostęp: user.
     */
    @DeleteMapping("/api/v1/search/history")
    public void clearHistory() {
        throw notImplemented();
    }

    /**
     * Zapisane wyszukiwania. Dostęp: user.
     */
    @GetMapping("/api/v1/search/saved")
    public void listSavedSearches() {
        throw notImplemented();
    }

    /**
     * Zapisz wyszukiwanie z powiadomieniami o nowych ofertach. Dostęp: user.
     */
    @PostMapping("/api/v1/search/saved")
    public void createSavedSearch(@RequestBody CreateSavedSearchRequest request) {
        throw notImplemented();
    }

    /**
     * Usuń zapisane wyszukiwanie. Dostęp: user.
     */
    @DeleteMapping("/api/v1/search/saved/{savedSearchId}")
    public void deleteSavedSearch(@PathVariable String savedSearchId) {
        throw notImplemented();
    }
}
