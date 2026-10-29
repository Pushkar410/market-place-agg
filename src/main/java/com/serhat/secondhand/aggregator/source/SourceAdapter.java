package com.serhat.secondhand.aggregator.source;

import com.serhat.secondhand.aggregator.domain.Category;
import com.serhat.secondhand.aggregator.domain.RawListing;
import com.serhat.secondhand.aggregator.domain.SearchIntent;
import com.serhat.secondhand.aggregator.domain.SourceInfo;

import java.util.List;
import java.util.Set;

/**
 * Plug-in point for a marketplace / community source. Implementations must only use
 * permitted access paths (official APIs, partner feeds, user-owned data, or data the
 * source explicitly allows to be indexed). Add a new Spring {@code @Component}
 * implementing this interface and it is picked up automatically.
 */
public interface SourceAdapter {

    SourceInfo info();

    /**
     * Categories this source can serve, used by the planner to skip sources that cannot help
     * (e.g. no housing on a bicycle board). An empty set means "unknown / everything" and is never skipped.
     */
    default Set<Category> coverage() {
        return Set.of();
    }

    /** Return candidate listings for the intent. Coarse filtering only - ranking happens later. */
    List<RawListing> search(SearchIntent intent);
}
