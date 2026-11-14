package pl.rynek.search.history;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.rynek.search.index.Cities;

/** Historia wyszukiwań zalogowanego użytkownika: 8 ostatnich na liście, 20 w pamięci, bez powtórzeń. */
@Service
public class HistoryService {

    static final int KEEP = 20;
    static final int LISTED = 8;

    private final SearchHistoryRepository history;
    private final Clock clock;

    public HistoryService(SearchHistoryRepository history, Clock clock) {
        this.history = history;
        this.clock = clock;
    }

    /** Zapisuje zapytanie; puste i zbyt długie pomija. Ponowne wyszukanie tego samego odświeża jego miejsce na liście. */
    @Transactional
    public void record(UUID userId, String query) {
        String clean = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty() || clean.length() > 100) {
            return;
        }
        history.record(UUID.randomUUID(), userId, clean, Cities.fold(clean), clock.instant());
        history.trim(userId, KEEP);
    }

    @Transactional(readOnly = true)
    public List<String> recent(UUID userId) {
        return history.recent(userId, LISTED);
    }

    @Transactional
    public void clear(UUID userId) {
        history.clear(userId);
    }
}
