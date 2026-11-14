package pl.rynek.search.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.rynek.search.TestcontainersConfig;
import pl.rynek.search.sync.CatalogClient;
import pl.rynek.search.sync.CatalogClient.Item;
import pl.rynek.search.sync.CatalogClient.SyncPage;
import pl.rynek.search.sync.ChannelClient;
import pl.rynek.search.sync.IndexSync;

/**
 * Wyszukiwarka na prawdziwym OpenSearch i PostgreSQL; catalog i channel to atrapy, które oddają zmiany po kursorze tak jak
 * prawdziwe serwisy (klucz {@code (updatedAt, id)}). Synchronizacja w tle jest wyłączona, a test uruchamia ją ręcznie.
 */
@SpringBootTest(properties = "rynek.search.sync-enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class SearchFlowTests {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    IndexSync sync;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CatalogClient catalog;

    @MockitoBean
    ChannelClient channels;

    /** „Baza” catalog: zmiany oddawane po kursorze (updatedAt, id), z możliwością zmian między synchronizacjami. */
    private final List<Item> store = new CopyOnWriteArrayList<>();
    private final List<ChannelClient.Item> channelStore = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode(anyString())).willAnswer(call -> {
            String token = call.getArgument(0);
            if (!token.startsWith("user-")) {
                throw new BadJwtException("Nieważny token");
            }
            return Jwt.withTokenValue(token).header("alg", "RS256").subject(token.substring(5))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        });
        given(catalog.page(any(), any(), anyInt())).willAnswer(call -> {
            Instant since = call.getArgument(0);
            UUID after = call.getArgument(1);
            int limit = call.getArgument(2);
            List<Item> page = store.stream().sorted(Comparator.comparing(Item::updatedAt).thenComparing(Item::id))
                    .filter(i -> i.updatedAt().isAfter(since) || (i.updatedAt().equals(since) && i.id().compareTo(after) > 0)).limit(limit).toList();
            Item last = page.isEmpty() ? null : page.getLast();
            return new SyncPage(page, last == null ? null : last.updatedAt(), last == null ? null : last.id(), page.size() == limit);
        });
        given(channels.page(any(), anyInt())).willAnswer(call -> new ChannelClient.Page(List.copyOf(channelStore), null));

        store.clear();
        channelStore.clear();
        List<String> phones = List.of("elektronika", "elektronika-telefony");
        store.add(offer("iPhone 15 128 GB", "PRODUCT", 349900, null, "Nowy", "Kraków", phones, "Elektro-Market", 4.8, 12, true, true, T0.plusSeconds(30),
                "Gwarancja producenta, komplet w pudełku."));
        store.add(offer("Samsung Galaxy S23", "PRODUCT", 199900, null, "Używany", "Warszawa", phones, "Dom i Kawa", 4.2, 5, false, true, T0.plusSeconds(20),
                "Stan bardzo dobry, bez rys."));
        store.add(offer("Słuchawki Bluetooth — czarne", "PRODUCT", 14900, null, "Nowy", "Cała Polska", List.of("elektronika", "elektronika-audio"),
                "Elektro-Market", 0, 0, false, true, T0.plusSeconds(10), "Bezprzewodowe, z mikrofonem."));
        store.add(offer("Malowanie mieszkań", "SERVICE", 2500, "m²", null, "Łódź", List.of("remonty", "remonty-malowanie"), "Remonty24", 4.9, 30, false, true,
                T0.plusSeconds(40), "Ściany i sufity, z gruntowaniem."));
        store.add(offer("Lodówka Samsung", "PRODUCT", 249900, null, "Nowy", "Gdańsk", List.of("agd", "agd-lodowki"), "Dom i Kawa", 4.2, 5, false, false,
                T0.plusSeconds(50), "Wyprzedana."));
        channelStore.add(new ChannelClient.Item("c-1", "elektromarket", "Elektro-Market", null, true, "Sprzęt elektroniczny", 40, 120));
        channelStore.add(new ChannelClient.Item("c-2", "domikawa", "Dom i Kawa", null, false, "Ekspresy i kawa", 12, 300));
        channelStore.add(new ChannelClient.Item("c-3", "sklepqa", "Sklep QA", null, false, "Sklep testowy", 3, 5));
        sync.reindex();
    }

    @Test
    void textSearchIgnoresPolishCharsToleratesTyposAndHidesUnavailable() throws Exception {
        titles("?q=iphone").andExpect(jsonPath("$.items[*].title", contains("iPhone 15 128 GB")));
        titles("?q=sluchawki").andExpect(jsonPath("$.items[*].title", contains("Słuchawki Bluetooth — czarne")));
        titles("?q=Słuchawki").andExpect(jsonPath("$.items[*].title", contains("Słuchawki Bluetooth — czarne")));
        // Literówka i początek słowa.
        titles("?q=iphnoe").andExpect(jsonPath("$.items[*].title", hasItem("iPhone 15 128 GB")));
        titles("?q=galax").andExpect(jsonPath("$.items[*].title", hasItem("Samsung Galaxy S23")));
        // Opis, kategoria i kanał.
        titles("?q=gwarancja").andExpect(jsonPath("$.items[*].title", contains("iPhone 15 128 GB")));
        titles("?q=telefony").andExpect(jsonPath("$.items[*].title", containsInAnyOrder("iPhone 15 128 GB", "Samsung Galaxy S23")));
        titles("?q=Remonty24").andExpect(jsonPath("$.items[*].title", contains("Malowanie mieszkań")));
        // Wyprzedany produkt nie trafia do wyników, mimo że pasuje do zapytania.
        titles("?q=lodowka").andExpect(jsonPath("$.items", hasSize(0))).andExpect(jsonPath("$.total").value(0));
        titles("?q=samsung").andExpect(jsonPath("$.items[*].title", contains("Samsung Galaxy S23")));
        // Wynik to ta sama karta, którą zwraca catalog.
        titles("?q=iphone").andExpect(jsonPath("$.items[0].channel.name").value("Elektro-Market"))
                .andExpect(jsonPath("$.items[0].price.amount").value(349900))
                .andExpect(jsonPath("$.items[0].rating.count").value(12))
                .andExpect(jsonPath("$.items[0].location").value("Kraków"));
        // Bez zapytania: wszystkie dostępne, najnowsze pierwsze.
        titles("").andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[*].title", contains("Malowanie mieszkań", "iPhone 15 128 GB", "Samsung Galaxy S23", "Słuchawki Bluetooth — czarne")));
    }

    @Test
    void filtersNarrowResults() throws Exception {
        titles("?type=SERVICE").andExpect(jsonPath("$.items[*].title", contains("Malowanie mieszkań")));
        titles("?type=PRODUCT").andExpect(jsonPath("$.total").value(3));
        // Kategoria nadrzędna obejmuje podkategorie.
        titles("?category=elektronika").andExpect(jsonPath("$.items[*].title", containsInAnyOrder("iPhone 15 128 GB", "Samsung Galaxy S23", "Słuchawki Bluetooth — czarne")));
        titles("?category=elektronika-telefony").andExpect(jsonPath("$.items[*].title", containsInAnyOrder("iPhone 15 128 GB", "Samsung Galaxy S23")));
        titles("?category=remonty").andExpect(jsonPath("$.items[*].title", contains("Malowanie mieszkań")));
        titles("?category=agd").andExpect(jsonPath("$.items", hasSize(0)));
        titles("?condition=Używany").andExpect(jsonPath("$.items[*].title", contains("Samsung Galaxy S23")));
        // Ceny w złotych: 1000–3000 zł to tylko Galaxy (1999 zł).
        titles("?priceMin=1000&priceMax=3000").andExpect(jsonPath("$.items[*].title", contains("Samsung Galaxy S23")));
        titles("?priceMin=3000").andExpect(jsonPath("$.items[*].title", contains("iPhone 15 128 GB")));
        titles("?priceMax=200").andExpect(jsonPath("$.items[*].title", containsInAnyOrder("Słuchawki Bluetooth — czarne", "Malowanie mieszkań")));
        // Ocena: oferty bez ocen nie mają „co najmniej 4,5”.
        titles("?minRating=4.5").andExpect(jsonPath("$.items[*].title", containsInAnyOrder("iPhone 15 128 GB", "Malowanie mieszkań")));
        titles("?minRating=1").andExpect(jsonPath("$.items[*].title", hasSize(3)));
        titles("?hasVideo=true").andExpect(jsonPath("$.items[*].title", contains("iPhone 15 128 GB")));
        titles("?q=samsung&type=PRODUCT&priceMax=2500").andExpect(jsonPath("$.items[*].title", contains("Samsung Galaxy S23")));
    }

    @Test
    void cityFilterMatchesCityNationwideOffersAndRadius() throws Exception {
        // Miasto i oferty „Cała Polska”.
        titles("?city=Kraków").andExpect(jsonPath("$.items[*].title", containsInAnyOrder("iPhone 15 128 GB", "Słuchawki Bluetooth — czarne")));
        titles("?city=krakow").andExpect(jsonPath("$.items[*].title", containsInAnyOrder("iPhone 15 128 GB", "Słuchawki Bluetooth — czarne")));
        // Promień z miasta: Warszawa–Kraków to ok. 250 km, Warszawa–Łódź ok. 120 km.
        titles("?city=Warszawa&radiusKm=300").andExpect(jsonPath("$.items[*].title",
                containsInAnyOrder("iPhone 15 128 GB", "Samsung Galaxy S23", "Słuchawki Bluetooth — czarne", "Malowanie mieszkań")));
        titles("?city=Warszawa&radiusKm=150").andExpect(jsonPath("$.items[*].title",
                containsInAnyOrder("Samsung Galaxy S23", "Słuchawki Bluetooth — czarne", "Malowanie mieszkań")));
        titles("?city=Warszawa&radiusKm=50").andExpect(jsonPath("$.items[*].title", containsInAnyOrder("Samsung Galaxy S23", "Słuchawki Bluetooth — czarne")));
        // Nieznana miejscowość: tylko dopasowanie po tekście lokalizacji i oferty ogólnopolskie.
        titles("?city=Zielonka").andExpect(jsonPath("$.items[*].title", contains("Słuchawki Bluetooth — czarne")));
    }

    @Test
    void sortingFacetsAndCursorPaging() throws Exception {
        titles("?sort=price_asc").andExpect(jsonPath("$.items[*].price.amount", contains(2500, 14900, 199900, 349900)));
        titles("?sort=price_desc").andExpect(jsonPath("$.items[*].price.amount", contains(349900, 199900, 14900, 2500)));
        titles("?sort=newest").andExpect(jsonPath("$.items[0].title").value("Malowanie mieszkań"));

        String body = titles("").andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> conditions = JsonPath.read(body, "$.facets.condition");
        assertThat(conditions).extracting(c -> c.get("value") + ":" + c.get("count")).containsExactlyInAnyOrder("Nowy:2", "Używany:1");
        List<Map<String, Object>> types = JsonPath.read(body, "$.facets.type");
        assertThat(types).extracting(c -> c.get("value") + ":" + c.get("count")).containsExactlyInAnyOrder("PRODUCT:3", "SERVICE:1");
        List<Map<String, Object>> categories = JsonPath.read(body, "$.facets.category");
        assertThat(categories).extracting(c -> c.get("value") + ":" + c.get("count")).contains("elektronika:3", "elektronika-telefony:2", "remonty:1");

        // 22 dodatkowe rowery: 20 na stronę, kursor prowadzi do reszty i nic się nie powtarza.
        for (int i = 1; i <= 22; i++) {
            store.add(offer("Rower górski " + i, "PRODUCT", 100000 + i, null, "Nowy", "Poznań", List.of("sport", "sport-rowery"), "Rowerownia", 0, 0, false, true,
                    T0.plusSeconds(100 + i), "Rower."));
        }
        sync.reindex();
        String first = titles("?q=rower").andExpect(jsonPath("$.items", hasSize(20))).andExpect(jsonPath("$.total").value(22))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty()).andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.nextCursor");
        String second = titles("?q=rower&cursor=" + cursor).andExpect(jsonPath("$.items", hasSize(2))).andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        List<String> all = JsonPath.read(first, "$.items[*].id");
        all.addAll(JsonPath.read(second, "$.items[*].id"));
        assertThat(all).hasSize(22).doesNotHaveDuplicates();
        // Cena rosnąca z kursorem: stała kolejność między stronami.
        String page1 = titles("?q=rower&sort=price_asc").andReturn().getResponse().getContentAsString();
        String page2 = titles("?q=rower&sort=price_asc&cursor=" + JsonPath.read(page1, "$.nextCursor")).andReturn().getResponse().getContentAsString();
        List<Integer> prices = JsonPath.read(page1, "$.items[*].price.amount");
        prices.addAll(JsonPath.read(page2, "$.items[*].price.amount"));
        assertThat(prices).isSorted().hasSize(22);
    }

    @Test
    void invalidFiltersAreRejectedInPolish() throws Exception {
        titles("?sort=losowo").andExpect(status().isBadRequest());
        titles("?type=INNE").andExpect(status().isBadRequest());
        titles("?priceMin=500&priceMax=100").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Cena od nie może być większa niż cena do."));
        titles("?priceMin=-5").andExpect(status().isBadRequest());
        titles("?radiusKm=0").andExpect(status().isBadRequest());
        titles("?radiusKm=900").andExpect(status().isBadRequest());
        titles("?minRating=6").andExpect(status().isBadRequest());
        titles("?q=" + "x".repeat(101)).andExpect(status().isBadRequest());
        titles("?cursor=to-nie-kursor").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Nieprawidłowy kursor. Zacznij od pierwszej strony."));
        titles("?priceMin=abc").andExpect(status().isBadRequest());
    }

    @Test
    void suggestChannelsAndLocations() throws Exception {
        mvc.perform(get("/api/v1/search/suggest").param("q", "iph")).andExpect(status().isOk()).andExpect(jsonPath("$", contains("iPhone 15 128 GB")));
        // Tytuł jest skracany przed myślnikiem, a krótkie zapytanie nic nie podpowiada.
        mvc.perform(get("/api/v1/search/suggest").param("q", "słuch")).andExpect(jsonPath("$", contains("Słuchawki Bluetooth")));
        mvc.perform(get("/api/v1/search/suggest").param("q", "s")).andExpect(jsonPath("$", empty()));
        mvc.perform(get("/api/v1/search/suggest")).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
        mvc.perform(get("/api/v1/search/suggest").param("q", "lodowka")).andExpect(jsonPath("$", empty()));

        mvc.perform(get("/api/v1/search/channels").param("q", "elektro")).andExpect(jsonPath("$[0].name").value("Elektro-Market"))
                .andExpect(jsonPath("$[0].handle").value("elektromarket")).andExpect(jsonPath("$[0].followersCount").value(120))
                .andExpect(jsonPath("$[0].verified").value(true));
        mvc.perform(get("/api/v1/search/channels").param("q", "kawa")).andExpect(jsonPath("$[0].name").value("Dom i Kawa"));
        // Bez tekstu najpopularniejsze kanały.
        mvc.perform(get("/api/v1/search/channels")).andExpect(jsonPath("$[*].name", contains("Dom i Kawa", "Elektro-Market", "Sklep QA")));
        mvc.perform(get("/api/v1/search/channels").param("q", "nieistniejacy")).andExpect(jsonPath("$", empty()));

        mvc.perform(get("/api/v1/search/locations").param("q", "kr")).andExpect(jsonPath("$[0].city").value("Kraków"))
                .andExpect(jsonPath("$[0].region").value("małopolskie"));
        mvc.perform(get("/api/v1/search/locations").param("q", "lo")).andExpect(jsonPath("$[*].city", contains("Łódź")));
        mvc.perform(get("/api/v1/search/locations").param("q", "xyz")).andExpect(jsonPath("$", empty()));
        mvc.perform(get("/api/v1/search/locations")).andExpect(jsonPath("$", hasSize(8)));
    }

    @Test
    void historyIsRecordedForLoggedInUsersOnly() throws Exception {
        UUID user = UUID.randomUUID();
        mvc.perform(get("/api/v1/search/history")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/search/history")).andExpect(status().isUnauthorized());

        // Gość nic nie zapisuje; zalogowany tak, ale tylko niepuste zapytania z pierwszej strony.
        mvc.perform(get("/api/v1/search/offers").param("q", "gosc"));
        search(user, "?q=iphone");
        search(user, "?q=galaxy");
        search(user, "?q=  ");
        search(user, "?type=PRODUCT");
        mvc.perform(get("/api/v1/search/history").header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(jsonPath("$", contains("galaxy", "iphone")));
        // Powtórzenie (także z inną wielkością liter i znakami) odświeża miejsce na liście, bez dubla.
        search(user, "?q=IPHONE");
        mvc.perform(get("/api/v1/search/history").header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(jsonPath("$", contains("IPHONE", "galaxy")));
        // Lista pokazuje 8 ostatnich.
        for (int i = 1; i <= 10; i++) {
            search(user, "?q=zapytanie" + i);
        }
        mvc.perform(get("/api/v1/search/history").header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0]").value("zapytanie10")).andExpect(jsonPath("$[7]").value("zapytanie3"));
        // Historia jest prywatna.
        mvc.perform(get("/api/v1/search/history").header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID()))).andExpect(jsonPath("$", empty()));
        mvc.perform(delete("/api/v1/search/history").header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/search/history").header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(jsonPath("$", empty()));
        // Zły token na publicznym wyszukiwaniu to gość, nie 401.
        mvc.perform(get("/api/v1/search/offers").param("q", "iphone").header(HttpHeaders.AUTHORIZATION, "Bearer zly")).andExpect(status().isOk());
    }

    @Test
    void savedSearchesAreValidatedLimitedAndPrivate() throws Exception {
        UUID user = UUID.randomUUID();
        mvc.perform(get("/api/v1/search/saved")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/search/saved").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());

        // Nieznane filtry i kursor są odrzucane, a znane zostają razem z flagą powiadomień.
        String body = saved(user, "{\"label\":\"  Telefony do 2000 zł \",\"notify\":true,\"filters\":{\"q\":\"telefon\",\"priceMax\":2000,\"hasVideo\":false,"
                + "\"cursor\":\"abc\",\"zlosliwe\":\"x\",\"sort\":\"price_asc\"}}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.label").value("Telefony do 2000 zł")).andExpect(jsonPath("$.notify").value(true))
                .andExpect(jsonPath("$.filters.q").value("telefon")).andExpect(jsonPath("$.filters.priceMax").value(2000))
                .andExpect(jsonPath("$.filters.cursor").doesNotExist()).andExpect(jsonPath("$.filters.zlosliwe").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/v1/search/saved").header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id)).andExpect(jsonPath("$[0].filters.sort").value("price_asc"));

        // Walidacja: nazwa, same sortowanie, błędne filtry.
        saved(user, "{\"label\":\" \",\"filters\":{\"q\":\"a\"}}").andExpect(status().isBadRequest());
        saved(user, "{\"label\":\"Pusto\",\"filters\":{\"sort\":\"newest\"}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Wpisz zapytanie albo ustaw filtry, zanim zapiszesz wyszukiwanie."));
        saved(user, "{\"label\":\"Zły zakres\",\"filters\":{\"priceMin\":500,\"priceMax\":100}}").andExpect(status().isBadRequest());
        saved(user, "{\"label\":\"x\"}").andExpect(status().isBadRequest());
        saved(user, "{\"label\":\"" + "n".repeat(61) + "\",\"filters\":{\"q\":\"a\"}}").andExpect(status().isBadRequest());

        // Prywatność: cudze zapisane wyszukiwanie wygląda jak nieistniejące.
        UUID other = UUID.randomUUID();
        mvc.perform(get("/api/v1/search/saved").header(HttpHeaders.AUTHORIZATION, bearer(other))).andExpect(jsonPath("$", empty()));
        mvc.perform(delete("/api/v1/search/saved/" + id).header(HttpHeaders.AUTHORIZATION, bearer(other))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/search/saved/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(status().isNotFound());

        // Limit 20 na użytkownika.
        for (int i = 2; i <= 20; i++) {
            saved(user, "{\"label\":\"Zapis " + i + "\",\"filters\":{\"q\":\"zapis" + i + "\"}}").andExpect(status().isCreated());
        }
        saved(user, "{\"label\":\"Za dużo\",\"filters\":{\"q\":\"x\"}}").andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/search/saved/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user))).andExpect(status().isNoContent());
        saved(user, "{\"label\":\"Teraz się mieści\",\"filters\":{\"q\":\"x\"}}").andExpect(status().isCreated());
    }

    @Test
    void incrementalSyncAddsChangesAndRemovesOffersWithoutRebuildingIndex() throws Exception {
        titles("?q=hulajnoga").andExpect(jsonPath("$.items", hasSize(0)));

        // Nowa oferta pojawia się po przyrostowej synchronizacji, bez przebudowy indeksu.
        store.add(offer("Hulajnoga elektryczna", "PRODUCT", 150000, null, "Nowy", "Wrocław", List.of("sport", "sport-hulajnogi"), "Rowerownia", 0, 0, false, true,
                T0.plusSeconds(200), "Szybka."));
        sync.sync();
        titles("?q=hulajnoga").andExpect(jsonPath("$.items[*].title", contains("Hulajnoga elektryczna")));

        // Zmiana ceny (nowy updatedAt) aktualizuje dokument.
        replace("Hulajnoga elektryczna", item -> offerFrom(item, 99900, true, T0.plusSeconds(210), null));
        sync.sync();
        titles("?q=hulajnoga").andExpect(jsonPath("$.items[0].price.amount").value(99900));

        // Wyprzedana znika z wyników (zostaje w indeksie jako niedostępna).
        replace("Hulajnoga elektryczna", item -> offerFrom(item, 99900, false, T0.plusSeconds(220), null));
        sync.sync();
        titles("?q=hulajnoga").andExpect(jsonPath("$.items", hasSize(0)));
        replace("Hulajnoga elektryczna", item -> offerFrom(item, 99900, true, T0.plusSeconds(230), null));
        sync.sync();
        titles("?q=hulajnoga").andExpect(jsonPath("$.items", hasSize(1)));

        // „Nagrobek” (wstrzymana albo usunięta oferta) usuwa dokument.
        replace("Hulajnoga elektryczna", item -> tombstone(item.id(), T0.plusSeconds(240)));
        sync.sync();
        titles("?q=hulajnoga").andExpect(jsonPath("$.items", hasSize(0)));
        // Pozostałe oferty nietknięte.
        titles("").andExpect(jsonPath("$.total").value(4));
    }

    @Test
    void reindexRemovesWhatCatalogNoLongerKnowsAndRefreshesChannels() throws Exception {
        titles("?q=iphone").andExpect(jsonPath("$.items", hasSize(1)));
        // Twarde usunięcie bez „nagrobka” i zmiana oceny (nie zmienia updatedAt oferty) nadrabia pełna reindeksacja.
        store.removeIf(i -> i.card().get("title").equals("iPhone 15 128 GB"));
        replace("Samsung Galaxy S23", item -> offerFrom(item, 199900, true, item.updatedAt(), 3.1));
        channelStore.removeIf(c -> c.id().equals("c-3"));
        mvc.perform(post("/internal/search/reindex")).andExpect(status().isOk()).andExpect(jsonPath("$.offers").value(4)).andExpect(jsonPath("$.channels").value(2))
                .andExpect(jsonPath("$.tookMs").isNumber());
        titles("?q=iphone").andExpect(jsonPath("$.items", hasSize(0)));
        titles("?q=galaxy").andExpect(jsonPath("$.items[0].rating.average").value(3.1));
        mvc.perform(get("/api/v1/search/channels").param("q", "sklep")).andExpect(jsonPath("$", empty()));
    }

    private ResultActions titles(String query) throws Exception {
        return mvc.perform(get("/api/v1/search/offers" + query)).andExpect(status().is(org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.is(200), org.hamcrest.Matchers.is(400))));
    }

    private ResultActions search(UUID user, String query) throws Exception {
        return mvc.perform(get("/api/v1/search/offers" + query).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions saved(UUID user, String body) throws Exception {
        return mvc.perform(post("/api/v1/search/saved").header(HttpHeaders.AUTHORIZATION, bearer(user)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void replace(String title, java.util.function.UnaryOperator<Item> change) {
        Item old = store.stream().filter(i -> i.card() != null && title.equals(i.card().get("title"))).findFirst().orElseThrow();
        store.remove(old);
        store.add(change.apply(old));
    }

    /** Ta sama oferta ze zmienioną ceną, dostępnością, czasem zmiany i (opcjonalnie) średnią oceną. */
    private static Item offerFrom(Item item, long price, boolean available, Instant updatedAt, Double rating) {
        Map<String, Object> card = new LinkedHashMap<>(item.card());
        card.put("price", Map.of("amount", price, "currency", "PLN"));
        if (rating != null) {
            card.put("rating", Map.of("average", rating, "count", 5));
        }
        return new Item(item.id(), true, updatedAt, card, item.description(), item.categoryId(), item.categoryPath(), item.categoryIds(), item.hasVideo(),
                available, item.createdAt(), item.publishedAt(), item.params());
    }

    private static Item offer(String title, String type, long price, String unit, String condition, String location, List<String> categoryIds, String channel,
                              double rating, int ratingCount, boolean video, boolean available, Instant at, String description) {
        UUID id = UUID.nameUUIDFromBytes(title.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", id.toString());
        card.put("type", type);
        card.put("title", title);
        card.put("price", Map.of("amount", price, "currency", "PLN"));
        card.put("priceUnit", unit);
        card.put("condition", condition);
        card.put("tone", 1);
        card.put("placeholder", title);
        card.put("channel", Map.of("id", UUID.nameUUIDFromBytes(channel.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(), "handle",
                channel.toLowerCase().replace(' ', '-'), "name", channel, "verified", false));
        card.put("rating", Map.of("average", rating, "count", ratingCount));
        card.put("location", location);
        return new Item(id, true, at, card, description, categoryIds.getLast(), categoryIds.stream().map(c -> c.substring(0, 1).toUpperCase() + c.substring(1)).toList(),
                categoryIds, video, available, at, at, List.of());
    }

    /** „Nagrobek” oferty wstrzymanej albo usuniętej: tylko id i czas zmiany. */
    private static Item tombstone(UUID id, Instant at) {
        return new Item(id, false, at, null, null, null, List.of(), List.of(), false, false, null, null, List.of());
    }

    private static String bearer(UUID userId) {
        return "Bearer user-" + userId;
    }
}
