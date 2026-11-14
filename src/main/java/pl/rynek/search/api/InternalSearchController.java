package pl.rynek.search.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.search.sync.IndexSync;

/** Tylko dla innych serwisów i administratora (gateway tego nie wystawia). */
@RestController
public class InternalSearchController {

    private final IndexSync sync;

    public InternalSearchController(IndexSync sync) {
        this.sync = sync;
    }

    /**
     * Pełna reindeksacja: nowy indeks ofert z całego catalog (alias przełączany atomowo, więc wyszukiwanie działa bez przerwy)
     * i odświeżony indeks kanałów. Zwraca liczby dokumentów i czas. Dostęp: int/admin.
     */
    @PostMapping("/internal/search/reindex")
    public IndexSync.Result reindex() {
        try {
            return sync.reindex();
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Nie udało się przebudować indeksu: " + e.getMessage());
        }
    }
}
