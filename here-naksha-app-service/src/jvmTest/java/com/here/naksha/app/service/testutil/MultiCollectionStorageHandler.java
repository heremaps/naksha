package com.here.naksha.app.service.testutil;

import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.handlers.DefaultStorageHandler;
import java.util.ArrayList;
import java.util.List;
import naksha.base.AnyList;
import naksha.base.Platform;
import naksha.base.Proxy;
import naksha.model.objects.NakshaCollection;
import org.jetbrains.annotations.NotNull;

/** Test subclass that, like extensions with nested collections, defines its collections in its own property. */
public final class MultiCollectionStorageHandler extends DefaultStorageHandler {
  public MultiCollectionStorageHandler(EventHandlerConfig config, INaksha hub, EventTarget<?> target) {
    super(config, hub, target);
  }

  @Override
  public @NotNull List<NakshaCollection> configuredCollections() {
    AnyList collections = Proxy.box(eventHandlerConfig.getProperties().getRaw("collections"), Platform.klassFor(AnyList.class), null, null);
    List<NakshaCollection> definitions = new ArrayList<>();
    if (collections != null) {
      for (Object value : collections) {
        NakshaCollection collection = Proxy.box(value, Platform.klassFor(NakshaCollection.class), null, null);
        if (collection != null) definitions.add(collection);
      }
    }
    return definitions;
  }
}
