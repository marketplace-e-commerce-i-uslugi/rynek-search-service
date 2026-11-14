package pl.rynek.search.saved;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Zapisane wyszukiwanie użytkownika: nazwa i filtry (JSON); {@code notify} to chęć powiadomień o nowych ofertach. */
@Entity
@Table(name = "saved_searches")
public class SavedSearch {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    private String label;
    private String filters;
    private boolean notify;

    @Column(name = "created_at")
    private Instant createdAt;

    protected SavedSearch() {
    }

    public SavedSearch(UUID userId, String label, String filters, boolean notify, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.label = label;
        this.filters = filters;
        this.notify = notify;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getFilters() {
        return filters;
    }

    public boolean isNotify() {
        return notify;
    }
}
