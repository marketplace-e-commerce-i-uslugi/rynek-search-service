package pl.rynek.search.index;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import pl.rynek.search.index.Cities.City;
import pl.rynek.search.sync.CatalogClient;
import pl.rynek.search.sync.ChannelClient;

/**
 * Dokumenty indeksów z danych catalog i channel. Oferta zachowuje całą kartę ({@code card}, zwracaną w wynikach bez zmian),
 * a obok niej pola do szukania, filtrów i sortowania. Współrzędne oferty bierzemy z tablicy miast po tekście lokalizacji.
 */
public final class Docs {

    private Docs() {
    }

    public static Map<String, Object> offer(CatalogClient.Item item) {
        Map<String, Object> card = item.card();
        String location = text(card.get("location"));
        Optional<City> city = Cities.in(location);
        Map<?, ?> rating = card.get("rating") instanceof Map<?, ?> r ? r : Map.of();
        Map<?, ?> channel = card.get("channel") instanceof Map<?, ?> c ? c : Map.of();
        Map<?, ?> price = card.get("price") instanceof Map<?, ?> p ? p : Map.of();

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", item.id().toString());
        doc.put("type", card.get("type"));
        doc.put("title", card.get("title"));
        doc.put("description", item.description());
        doc.put("params", item.params() == null ? "" : item.params().stream().map(p -> p.label() + " " + p.value()).collect(Collectors.joining(" ")));
        doc.put("categoryId", item.categoryId());
        doc.put("categoryIds", item.categoryIds());
        doc.put("categoryPath", item.categoryPath());
        doc.put("price", price.get("amount") instanceof Number n ? n.longValue() : 0L);
        doc.put("condition", card.get("condition"));
        doc.put("locationText", location);
        doc.put("cityKey", city.map(City::key).orElse(null));
        doc.put("nationwide", Cities.NATIONWIDE.equals(Cities.fold(location)));
        city.ifPresent(c -> doc.put("geo", Map.of("lat", c.lat(), "lon", c.lon())));
        doc.put("channelId", channel.get("id"));
        doc.put("channelName", channel.get("name"));
        doc.put("rating", rating.get("average") instanceof Number n ? n.doubleValue() : 0.0);
        doc.put("ratingCount", rating.get("count") instanceof Number n ? n.intValue() : 0);
        doc.put("hasVideo", item.hasVideo());
        doc.put("available", item.available());
        doc.put("createdAt", item.createdAt());
        doc.put("publishedAt", item.publishedAt() != null ? item.publishedAt() : item.createdAt());
        doc.put("card", card);
        return doc;
    }

    /** {@code syncId} oznacza przebieg synchronizacji: kanały, których nie odświeżył, zostają potem usunięte z indeksu. */
    public static Map<String, Object> channel(ChannelClient.Item item, long syncId) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", item.id());
        doc.put("handle", item.handle());
        doc.put("name", item.name());
        doc.put("description", item.description());
        doc.put("verified", item.verified());
        doc.put("followersCount", item.followersCount());
        doc.put("offersCount", item.offersCount());
        doc.put("syncId", syncId);
        return doc;
    }

    private static String text(Object value) {
        return value instanceof String s ? s : "";
    }

}
