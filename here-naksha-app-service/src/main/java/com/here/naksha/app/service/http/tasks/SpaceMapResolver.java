package com.here.naksha.app.service.http.tasks;

import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.naksha.SpaceProperties;
import com.here.naksha.lib.hub.storages.NHAdminStorage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import naksha.base.AtomicRef;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.objects.NakshaCollection;
import org.jetbrains.annotations.NotNull;

public class SpaceMapResolver {

  private static AtomicRef<SpaceMapResolver> instance;
  private final Map<String, String> mapIdBySpaceId = new ConcurrentHashMap<>();

  private SpaceMapResolver() {}

  private static class Holder {
    private static final SpaceMapResolver INSTANCE = new SpaceMapResolver();
  }

  // TODO
  public static SpaceMapResolver getInstance(NHAdminStorage adminStorage) {
    return Holder.INSTANCE;
  }

  public String getMapIdForSpace(String spaceId) {
    return mapIdBySpaceId.computeIfAbsent(spaceId, id -> spaceId);
  }

  public void updateMapDataFor(Space space) {
    mapIdBySpaceId.put(space.getId(), getMapIdOrFail(space));
  }

  private @NotNull String fetchSpaceAndRetrieveMapId(String spaceId) {

  }

  private @NotNull String getMapIdOrFail(Space space) {
    NakshaCollection collection = space.getProperties().getCollection();
    if(collection == null){
      throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Missing collection in Space: '" + space.getId() + "'");
    }
    String mapId = collection.getMapId();
    if(mapId == null){
      throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Missing 'mapId' for collection: '" + collection.getId() + "' defined in Space: '" + space.getId() + "'");
    }
    return mapId;
  }

  public V get(K key) {
    return cache.get(key);
  }

  public void remove(K key) {
    cache.remove(key);
  }
}
