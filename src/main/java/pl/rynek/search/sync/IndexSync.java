package pl.rynek.search.sync;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.rynek.search.index.Docs;
import pl.rynek.search.index.Mappings;
import pl.rynek.search.index.OpenSearch;
import pl.rynek.search.index.OpenSearch.BulkOp;

/**
 * Utrzymuje indeksy ofert i kanałów zamiast zdarzeń {@code offer.*}, {@code channel.*} i {@code review.created}.
 * <ul>
 *   <li><b>Oferty, przyrost:</b> co {@code sync-interval} pobiera zmiany z catalog po kursorze {@code (updatedAt, id)} i dopisuje
 *   albo usuwa dokumenty (opublikowanie, edycja, wstrzymanie, usunięcie, wyczerpanie zapasu).</li>
 *   <li><b>Oferty, pełne przebudowanie:</b> przy starcie i co {@code reindex-interval} buduje nowy indeks i przełącza na niego
 *   alias atomowo. Odświeża oceny kanałów (nie zmieniają oferty) i usuwa to, czego catalog już nie zna.</li>
 *   <li><b>Kanały:</b> pełny przegląd co minutę (liczba obserwujących i weryfikacja zmieniają się bez zmiany kanału), a
 *   kanały, których przegląd nie odświeżył, są usuwane.</li>
 * </ul>
 * Wszystko pod jedną blokadą, więc zaplanowana synchronizacja i ręczna reindeksacja nie nakładają się.
 */
@Component
public class IndexSync {

    public static final String OFFERS = "offers";
    public static final String CHANNELS = "channels";

    private static final Logger log = LoggerFactory.getLogger(IndexSync.class);
    private static final int OFFER_PAGE = 100;
    private static final int MAX_PAGES_PER_RUN = 50;
    private static final Duration CHANNELS_EVERY = Duration.ofMinutes(1);

    private record Cursor(Instant since, UUID afterId) {
        static final Cursor START = new Cursor(Instant.EPOCH, new UUID(0, 0));
    }

    /** Wynik pełnej reindeksacji: liczby dokumentów i czas. */
    public record Result(long offers, long channels, long tookMs) {
    }

    private final OpenSearch search;
    private final CatalogClient catalog;
    private final ChannelClient channels;
    private final Clock clock;
    private final boolean enabled;
    private final Duration reindexEvery;
    private final Object lock = new Object();

    /** Kursor przyrostu; pusty, dopóki w tym procesie nie zbudowano indeksu. */
    private Cursor cursor;
    private Instant lastReindex = Instant.EPOCH;
    private Instant lastChannels = Instant.EPOCH;

    public IndexSync(OpenSearch search, CatalogClient catalog, ChannelClient channels, Clock clock,
                     @Value("${rynek.search.sync-enabled:true}") boolean enabled,
                     @Value("${rynek.search.reindex-interval:10m}") Duration reindexEvery) {
        this.search = search;
        this.catalog = catalog;
        this.channels = channels;
        this.clock = clock;
        this.enabled = enabled;
        this.reindexEvery = reindexEvery;
    }

    @Scheduled(fixedDelayString = "${rynek.search.sync-interval:15s}", initialDelayString = "${rynek.search.sync-initial-delay:3s}")
    void scheduled() {
        if (!enabled) {
            return;
        }
        try {
            sync();
        } catch (RuntimeException e) {
            log.warn("Synchronizacja indeksu wyszukiwarki nie powiodła się, spróbujemy za chwilę: {}", e.getMessage());
        }
    }

    /** Jeden przebieg: pełna reindeksacja, gdy trzeba, inaczej przyrost ofert i (co minutę) przegląd kanałów. */
    public void sync() {
        synchronized (lock) {
            Instant now = clock.instant();
            if (cursor == null || Duration.between(lastReindex, now).compareTo(reindexEvery) >= 0) {
                reindexLocked();
                return;
            }
            incrementalOffers();
            if (Duration.between(lastChannels, now).compareTo(CHANNELS_EVERY) >= 0) {
                syncChannels();
            }
        }
    }

    /** Pełna reindeksacja na żądanie ({@code POST /internal/search/reindex}). Błąd połączenia wychodzi do wołającego. */
    public Result reindex() {
        synchronized (lock) {
            return reindexLocked();
        }
    }

    private Result reindexLocked() {
        long started = System.nanoTime();
        long offers = rebuildOffers();
        long channelCount = syncChannels();
        return new Result(offers, channelCount, (System.nanoTime() - started) / 1_000_000);
    }

    private long rebuildOffers() {
        String index = OFFERS + "-" + clock.millis();
        search.createIndex(index, Mappings.OFFERS);
        try {
            Cursor next = Cursor.START;
            long count = 0;
            CatalogClient.SyncPage page;
            do {
                page = catalog.page(next.since(), next.afterId(), 200);
                List<BulkOp> operations = new ArrayList<>();
                for (CatalogClient.Item item : page.items()) {
                    if (item.active()) {
                        operations.add(BulkOp.index(item.id().toString(), Docs.offer(item)));
                    }
                }
                search.bulk(index, operations);
                count += operations.size();
                if (page.nextSince() != null) {
                    next = new Cursor(page.nextSince(), page.nextAfterId());
                }
            } while (page.hasMore());
            search.refresh(index);
            search.swapAlias(OFFERS, index);
            cursor = next;
            lastReindex = clock.instant();
            log.info("Zbudowano indeks ofert {}: {} dokumentów", index, count);
            return count;
        } catch (RuntimeException e) {
            search.deleteIndex(index);
            throw e;
        }
    }

    private void incrementalOffers() {
        Cursor current = cursor;
        boolean changed = false;
        int pages = 0;
        CatalogClient.SyncPage page;
        do {
            page = catalog.page(current.since(), current.afterId(), OFFER_PAGE);
            List<BulkOp> operations = new ArrayList<>();
            for (CatalogClient.Item item : page.items()) {
                operations.add(item.active() ? BulkOp.index(item.id().toString(), Docs.offer(item)) : BulkOp.delete(item.id().toString()));
            }
            search.bulk(OFFERS, operations);
            changed |= !operations.isEmpty();
            if (page.nextSince() != null) {
                // Kursor przesuwamy dopiero po zapisie strony, więc nieudana paczka zostanie ponowiona.
                current = new Cursor(page.nextSince(), page.nextAfterId());
                cursor = current;
            }
        } while (page.hasMore() && ++pages < MAX_PAGES_PER_RUN);
        if (changed) {
            search.refresh(OFFERS);
        }
    }

    /** Pełny przegląd kanałów; błąd channel-service nie blokuje ofert (kanały odświeżą się przy następnym przebiegu). */
    private long syncChannels() {
        try {
            if (!search.exists(CHANNELS)) {
                search.createIndex(CHANNELS, Mappings.CHANNELS);
            }
            long syncId = clock.millis();
            long count = 0;
            String after = null;
            do {
                ChannelClient.Page page = channels.page(after, 100);
                List<BulkOp> operations = page.items().stream().map(item -> BulkOp.index(item.id(), Docs.channel(item, syncId))).toList();
                search.bulk(CHANNELS, operations);
                count += operations.size();
                after = page.nextAfterId();
            } while (after != null);
            // _delete_by_query widzi tylko odświeżone dokumenty, więc bez tego kanał z poprzedniego przebiegu mógłby przetrwać.
            search.refresh(CHANNELS);
            long removed = search.deleteByQuery(CHANNELS, Map.of("range", Map.of("syncId", Map.of("lt", syncId))));
            log.debug("Kanały: zindeksowano {}, usunięto nieaktualnych {} (przebieg {})", count, removed, syncId);
            lastChannels = clock.instant();
            return count;
        } catch (RuntimeException e) {
            log.warn("Nie udało się odświeżyć indeksu kanałów: {}", e.getMessage());
            return 0;
        }
    }
}
