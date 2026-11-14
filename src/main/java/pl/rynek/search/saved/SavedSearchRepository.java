package pl.rynek.search.saved;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, UUID> {

    List<SavedSearch> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserId(UUID userId);

    /** 0: nie ma takiego zapisanego wyszukiwania u tego użytkownika (cudze wygląda jak nieistniejące). */
    @Modifying
    @Query("delete from SavedSearch s where s.id = :id and s.userId = :userId")
    int deleteOwned(UUID id, UUID userId);
}
