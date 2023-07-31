package com.here.naksha.lib.heapcache;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.util.ILike;
import com.here.naksha.lib.core.util.fib.FibMapEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CacheEntryExperimental extends FibMapEntry<String, XyzFeature> {

    /**
     * Create a new entry for a {@link FibSet}.
     *
     * @param key the key.
     */
    public CacheEntryExperimental(@NotNull String key) {
        super(key);
    }

    @Override
    public boolean isLike(@Nullable Object key) {
        // Potentially: We could consider other things, for example bounding boxes or alike.
        return ILike.equals(this.key, key);
    }
}