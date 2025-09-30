package com.here.naksha.storage.http.connector;

import com.here.naksha.lib.core.models.payload.Event;
import com.here.naksha.lib.core.models.payload.events.feature.ModifyFeaturesEvent;
import com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.storage.http.PrepareResult;
import com.here.naksha.storage.http.RequestSender;
import com.here.naksha.storage.http.ffw.FfwInterfaceReadExecute;
import naksha.base.Int64;
import naksha.base.Platform;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.XyzNs;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteList;
import naksha.model.request.WriteOp;
import naksha.model.request.WriteRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.here.naksha.common.http.apis.ApiParamsConst.FEATURE_IDS;
import static com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper.ReadRequestType.GET_BY_IDS;

public class ConnectorInterfaceWriteExecute {
    private final NakshaContext context;
    private final WriteRequest request;
    private final RequestSender sender;
    private final String endpoint;
    private final Map<String, NakshaFeature> databaseFeaturesCache = new HashMap<>();

    public ConnectorInterfaceWriteExecute(NakshaContext context, WriteRequest request, RequestSender sender) {
        this.context = context;
        this.request = request;
        this.sender = sender;
        this.endpoint = "/" + singleCollectionIdFrom(request);
    }

    private static void setCreatedAt(NakshaFeature feature, Int64 creationTime) {
        feature.getProperties().getXyz().setRaw("createdAt", creationTime);
    }

    private static void setUpdatedAt(NakshaFeature feature, Int64 creationTime) {
        feature.getProperties().getXyz().setRaw("updatedAt", creationTime);
    }

    private static void setRandomUuid(NakshaFeature feature) {
        feature.getProperties().getXyz().setRaw("uuid", UUID.randomUUID().toString());
    }

    @NotNull
    public Response execute() {
        String streamId = context.getStreamId();
        Event event = createModifyFeaturesEvent();

        event.setStreamId(streamId);

        String jsonEvent = JsonSerializable.serialize(event);
        HttpResponse<byte[]> httpResponse = sender.post(endpoint, jsonEvent);

        return PrepareResult.prepareResult(httpResponse, PrepareResult.collectionMapper);
    }

    private ModifyFeaturesEvent createModifyFeaturesEvent() {
        ModifyFeaturesEvent event = new ModifyFeaturesEvent();

        List<NakshaFeature> featuresToInsert = new LinkedList<>();
        List<NakshaFeature> featuresToUpdate = new LinkedList<>();
        Map<String, String> featuresToDelete = new HashMap<>(); // Format enforced by connector API

        populateDbCache(request.getWrites());

        for (Write write : request.getWrites().asList()) {
            NakshaFeature feature = write.getFeature();
            if (write.getOp().equals(WriteOp.CREATE) || write.getOp().equals(WriteOp.UPDATE) || write.getOp().equals(WriteOp.UPSERT)) {
                if (!existsInDb(feature)) {
                    featuresToInsert.add(feature);
                } else {
                    featuresToUpdate.add(feature);
                }
            } else if (write.getOp().equals(WriteOp.DELETE)) {
                // Connector docs requires map entry value to be null,
                // but in reality, doesn't matter what is the value
                // and map with null is ignored by JsonSerializable.serialize(),
                // so empty string is used instead.
                featuresToDelete.put(write.getId(), "");
            } else {
                throw new UnsupportedOperationException("Unsupported feature codec OP: " + write.getOp());
            }
        }

        Int64 currentTime = Platform.currentMillis();
        featuresToInsert.forEach(feature -> {
            assertNoUuid(feature);
            setRandomUuid(feature);
            setCreatedAt(feature, currentTime);
            setUpdatedAt(feature, currentTime);
        });
        featuresToUpdate.forEach(feature -> {
            assertUuidMatch(feature);
            setPuuidToUuidFromDb(feature);
            setRandomUuid(feature);
            setCreatedAtToCreateAtFromDb(feature);
            setUpdatedAt(feature, currentTime);
        });

        event.setInsertFeatures(featuresToInsert);
        event.setUpdateFeatures(featuresToUpdate);
        event.setDeleteFeatures(featuresToDelete);

        return event;
    }

    /**
     * Fills cache with writes from database that will be needed.
     * Only the writes with PUT op are needed, therefore only they are fetched.
     */
    private void populateDbCache(WriteList writes) {
        List<String> idsList = writes.stream()
                .filter(feature -> feature.getOp().equals(WriteOp.CREATE) || feature.getOp().equals(WriteOp.UPDATE) || feature.getOp().equals(WriteOp.UPSERT))
                .map(feature -> feature.getFeature().getId())
                .toList();
        NakshaFeatureList nakshaFeatureList = getFeaturesFromDb(idsList);
        for (NakshaFeature nakshaFeature : nakshaFeatureList.asList()) {
            databaseFeaturesCache.put(nakshaFeature.getId(), nakshaFeature);
        }
    }

    private void assertNoUuid(NakshaFeature feature) {
        String id = feature.getId();
        if (feature.getProperties().getXyz().getUuid() != null) {
            throw new IllegalArgumentException("The feature with id " + id + " cannot be created. "
                    + "Property UUID should not be provided as input.");
        }
    }

    private void assertUuidMatch(NakshaFeature feature) {
        String uuid = feature.getProperties().getXyz().getUuid();
        if (uuid != null) {
            String uuidFromDb = getXyzNamespaceFromDbCache(feature).getUuid();
            if (!uuid.equals(uuidFromDb)) {
                throw new ConflictException(
                        "The feature with id %s cannot be replaced. The provided UUID doesn't match the UUID of the head state: %s"
                                .formatted(feature.getId(), uuidFromDb));
            }
        }
    }

    private void setCreatedAtToCreateAtFromDb(NakshaFeature feature) {
        XyzNs xyzNamespaceFromRequest = feature.getProperties().getXyz();
        XyzNs xyzNamespaceFromDb = getXyzNamespaceFromDbCache(feature);
        xyzNamespaceFromRequest.setRaw("createdAt", xyzNamespaceFromDb.getCreatedAt());
    }

    private void setPuuidToUuidFromDb(NakshaFeature feature) {
        XyzNs xyzNamespaceFromRequest = feature.getProperties().getXyz();
        XyzNs xyzNamespaceFromDb = getXyzNamespaceFromDbCache(feature);
        xyzNamespaceFromRequest.setRaw("puuid", xyzNamespaceFromDb.getUuid());
    }

    private boolean existsInDb(NakshaFeature feature) {
        return databaseFeaturesCache.containsKey(feature.getId());
    }

    private XyzNs getXyzNamespaceFromDbCache(NakshaFeature feature) {
        return getFeatureFromDbCache(feature).getProperties().getXyz();
    }

    private @Nullable NakshaFeature getFeatureFromDbCache(NakshaFeature feature) {
        if (databaseFeaturesCache.containsKey(feature.getId())) {
            return databaseFeaturesCache.get(feature.getId());
        } else {
            throw new IllegalStateException("Feature with id " + feature.getId() + " not present in cache");
        }
    }

    private NakshaFeatureList getFeaturesFromDb(List<String> featureIds) {
        ReadFeaturesProxyWrapper getFeaturesRequest = new ReadFeaturesProxyWrapper().withReadRequestType(GET_BY_IDS);
        getFeaturesRequest.addQueryParameter(FEATURE_IDS, featureIds);
        getFeaturesRequest.withCollection(endpoint);

        Response response = ConnectorInterfaceReadExecute.execute(context, getFeaturesRequest, sender);
        if (response instanceof SuccessResponse successResponse) {
            return successResponse.getFeatures();
        } else if (response instanceof ErrorResponse errorResponse) {
            throw new NakshaException(errorResponse.getError());
        } else {
            throw new NakshaException(NakshaError.EXCEPTION, "Unexpected response while reading features from storage");
        }
    }

    public static class ConflictException extends IllegalStateException {
        public ConflictException(String message) {
            super(message);
        }
    }

    private String singleCollectionIdFrom(WriteRequest writeRequest) {
        List<String> distinctCollectionIds = writeRequest.getWrites().stream().map(Write::getCollectionId).distinct().toList();
        if (distinctCollectionIds.size() != 1) {
            throw new IllegalArgumentException(
                    "Expected Writes of WriteRequest to indicate single collection, got multiple: " + distinctCollectionIds);
        }
        return Objects.requireNonNull(distinctCollectionIds.get(0), "Got empty (null) Write instruction within WriteRequest");
    }
}
