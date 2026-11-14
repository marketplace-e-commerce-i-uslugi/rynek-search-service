package pl.rynek.search.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, UUID> {

    /** Zapisuje zapytanie albo odświeża jego czas, gdy użytkownik już je wyszukiwał. */
    @Modifying
    @Query(value = "insert into search_history (id, user_id, query, query_key, searched_at) values (:id, :userId, :query, :queryKey, :now) "
            + "on conflict (user_id, query_key) do update set query = excluded.query, searched_at = excluded.searched_at", nativeQuery = true)
    int record(UUID id, UUID userId, String query, String queryKey, Instant now);

    /** Zostawia {@code keep} najnowszych zapytań użytkownika. */
    @Modifying
    @Query(value = "delete from search_history where user_id = :userId and id not in "
            + "(select id from search_history where user_id = :userId order by searched_at desc limit :keep)", nativeQuery = true)
    int trim(UUID userId, int keep);

    @Query(value = "select query from search_history where user_id = :userId order by searched_at desc limit :limit", nativeQuery = true)
    List<String> recent(UUID userId, int limit);

    @Modifying
    @Query("delete from SearchHistory h where h.userId = :userId")
    int clear(UUID userId);
}
