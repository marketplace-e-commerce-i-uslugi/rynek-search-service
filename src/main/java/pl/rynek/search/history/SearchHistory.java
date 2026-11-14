package pl.rynek.search.history;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Zapytanie z historii wyszukiwań użytkownika. Zapisywane natywnym zapytaniem (odświeżenie czasu przy powtórzeniu). */
@Entity
@Table(name = "search_history")
public class SearchHistory {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    private String query;

    @Column(name = "query_key")
    private String queryKey;

    @Column(name = "searched_at")
    private Instant searchedAt;

    protected SearchHistory() {
    }

    public String getQuery() {
        return query;
    }
}
