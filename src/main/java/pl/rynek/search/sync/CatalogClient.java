package pl.rynek.search.sync;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Zmiany ofert z catalog-service ({@code GET /internal/offers/search-sync}) zamiast zdarzeń {@code offer.*}. */
@Component
public class CatalogClient {

    private final RestClient http;

    public CatalogClient(@Value("${rynek.catalog.url}") String catalogUrl) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(60));
        this.http = RestClient.builder().baseUrl(catalogUrl).requestFactory(requests).build();
    }

    /** Strona zmian od kursora {@code (since, afterId)}; błąd połączenia wychodzi jako wyjątek (synchronizacja spróbuje później). */
    public SyncPage page(Instant since, UUID afterId, int limit) {
        SyncPage page = http.get().uri(uri -> uri.path("/internal/offers/search-sync")
                        .queryParam("since", since.toString()).queryParam("afterId", afterId.toString()).queryParam("limit", limit).build())
                .retrieve().body(SyncPage.class);
        if (page == null) {
            throw new IllegalStateException("Pusta odpowiedź catalog-service.");
        }
        return page;
    }

    /** {@code nextSince}/{@code nextAfterId} puste przy pustej stronie. */
    public record SyncPage(List<Item> items, Instant nextSince, UUID nextAfterId, boolean hasMore) {
    }

    /** Oferta w indeksie albo „nagrobek” ({@code active = false}, reszta pól pusta). {@code card} to {@code OfferCard}. */
    public record Item(UUID id, boolean active, Instant updatedAt, Map<String, Object> card, String description, String categoryId,
                       List<String> categoryPath, List<String> categoryIds, boolean hasVideo, boolean available, Instant createdAt,
                       Instant publishedAt, List<Param> params) {
    }

    public record Param(String label, String value) {
    }
}
