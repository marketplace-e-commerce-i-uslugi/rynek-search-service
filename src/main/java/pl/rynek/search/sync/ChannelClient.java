package pl.rynek.search.sync;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Wszystkie kanały po kolei z channel-service ({@code GET /internal/channels/search-sync}) zamiast zdarzeń {@code channel.*}. */
@Component
public class ChannelClient {

    private final RestClient http;

    public ChannelClient(@Value("${rynek.channel.url}") String channelUrl) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder().baseUrl(channelUrl).requestFactory(requests).build();
    }

    /** Strona kanałów po id rosnąco; {@code afterId} pusty na pierwszej stronie. */
    public Page page(String afterId, int limit) {
        Page page = http.get().uri(uri -> {
            uri.path("/internal/channels/search-sync").queryParam("limit", limit);
            if (afterId != null) {
                uri.queryParam("afterId", afterId);
            }
            return uri.build();
        }).retrieve().body(Page.class);
        if (page == null) {
            throw new IllegalStateException("Pusta odpowiedź channel-service.");
        }
        return page;
    }

    public record Page(List<Item> items, String nextAfterId) {
    }

    public record Item(String id, String handle, String name, String avatarUrl, boolean verified, String description, int offersCount,
                       long followersCount) {
    }
}
