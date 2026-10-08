package com.here.naksha.lib.core.models.naksha;

import naksha.base.Platform;
import naksha.base.Proxy;
import naksha.model.objects.NakshaCollection;
import org.jetbrains.annotations.Nullable;

/** Mappings are stored in Space/Handler configuration and removed from outgoing native definitions. */
public final class CustomIndexMappings {
  private CustomIndexMappings() {}

  public static @Nullable CustomIndexMappingList getCustomIndexMapping(NakshaCollection collection) {
    return Proxy.box(collection.getRaw("customIndexMapping"), Platform.klassFor(CustomIndexMappingList.class), null, null);
  }

  public static void setCustomIndexMapping(NakshaCollection collection, @Nullable CustomIndexMappingList mappings) {
    collection.put("customIndexMapping", mappings);
  }
}
