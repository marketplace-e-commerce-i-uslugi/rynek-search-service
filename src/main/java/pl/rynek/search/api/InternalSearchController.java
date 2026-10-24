package pl.rynek.search.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static pl.rynek.search.common.NotImplemented.notImplemented;

@RestController
public class InternalSearchController {

    /**
     * Pełna reindeksacja. Dostęp: int/admin.
     */
    @PostMapping("/internal/search/reindex")
    public void reindex() {
        throw notImplemented();
    }
}
