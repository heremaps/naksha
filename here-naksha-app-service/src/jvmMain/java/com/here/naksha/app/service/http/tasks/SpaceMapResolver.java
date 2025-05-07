package com.here.naksha.app.service.http.tasks;

import static com.here.naksha.lib.core.HubInternalIdentifiers.SPACES;

import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.Space;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.util.RequestHelper;
import naksha.model.util.ResultHelper;
import org.jetbrains.annotations.NotNull;

/**
 * Class responsible for establishing which {@link naksha.model.objects.NakshaMap} should be used for given {@link Space}.
 *
 * Each {@link Space} contains a reference to {@link NakshaCollection} and each collection references the {@link naksha.model.objects.NakshaMap} it belongs to.
 * The API layer very often knows the `spaceId` but it lacks the `mapId` which is necessary for Read/Write operations on {@link naksha.model.IStorage}.
 * {@link SpaceMapResolver} acts like a cache, providing `mapId` for given `spaceId`. If the cache for given `spaceId` is empty, it executes {@link ReadFeatures} request against the Naksha Hub Admin Storage to populate it.
 */
public class SpaceMapResolver {

  private final Map<String, String> mapIdBySpaceId = new ConcurrentHashMap<>();
  private final INaksha naksha;

  public SpaceMapResolver (INaksha naksha) {
    this.naksha = naksha;
  }

  public String getMapIdForSpace(String spaceId) {
    return mapIdBySpaceId.computeIfAbsent(spaceId, this::fetchSpaceAndRetrieveMapId);
  }

  public void updateMapDataFor(Space space) {
    mapIdBySpaceId.put(space.getId(), getMapIdOrFail(space));
  }

  public void removeMapEntryFor(String spaceId) {
    mapIdBySpaceId.remove(spaceId);
  }

  private @NotNull String fetchSpaceAndRetrieveMapId(String spaceId) {
    ReadFeatures readSpace = RequestHelper.readFeaturesByIdRequest(naksha.getAdminMapId(), SPACES, spaceId);
    Response spaceResp = naksha.getAdminStorage().useReadSession(SessionOptions.from(NakshaContext.currentContext()), session -> session.execute(readSpace));
    if(spaceResp instanceof SuccessResponse successResponse){
      Space space = ResultHelper.readFeatureFromResponse(successResponse, Space.class);
      if(space == null){
        throw new NakshaException(NakshaError.NOT_FOUND, "Unable to fetch mapId for non existing space: '" + spaceId + "' (NotFound)");
      }
      return getMapIdOrFail(space);
    } else if (spaceResp instanceof ErrorResponse er) {
      throw new NakshaException(er.getError());
    } else {
      throw new NakshaException(NakshaError.EXCEPTION, "Unable to retrieve map id for space: '" + spaceId + "'. Unexpected result: " + spaceResp);
    }
  }

  private @NotNull String getMapIdOrFail(@NotNull Space space) {
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
}
