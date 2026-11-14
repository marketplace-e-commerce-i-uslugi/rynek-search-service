package pl.rynek.search.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Kształty z rynek-frontend ({@code core/api/models/search.ts}). */
public final class SearchDtos {

    private SearchDtos() {
    }

    /** {@code items} to karty ofert ({@code OfferCard}) w kolejności wyników; {@code nextCursor} pusty na ostatniej stronie. */
    public record SearchResult(List<Map<String, Object>> items, String nextCursor, long total, Map<String, List<FacetValue>> facets) {
    }

    public record FacetValue(String value, long count) {
    }

    public record ChannelHit(String id, String handle, String name, long followersCount, boolean verified) {
    }

    public record LocationHit(String city, String region) {
    }

    /** W JSON pole nazywa się {@code notify} (kontrakt frontendu); w rekordzie nie może, bo koliduje z {@code Object.notify()}. */
    public record SavedSearchResponse(UUID id, String label, Map<String, Object> filters, @JsonProperty("notify") boolean notifyOnNew) {
    }

    public record CreateSavedSearchRequest(
            @NotBlank(message = "Nazwij zapisywane wyszukiwanie.") @Size(max = 60, message = "Nazwa może mieć do 60 znaków.") String label,
            @NotNull(message = "Brakuje filtrów wyszukiwania.") Map<String, Object> filters,
            @JsonProperty("notify") Boolean notifyOnNew) {
    }
}
