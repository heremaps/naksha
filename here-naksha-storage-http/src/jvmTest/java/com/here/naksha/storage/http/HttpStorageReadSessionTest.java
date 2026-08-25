package com.here.naksha.storage.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper;
import java.util.UUID;
import naksha.base.Action;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.base.Version;
import naksha.model.Naksha;
import naksha.model.XyzNs;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.objects.NakshaProperties;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.ErrorResponse;
import naksha.model.request.FeatureTuple;
import naksha.model.request.FeatureTupleList;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HttpStorageReadSessionTest {
    private static final String STORAGE_ID = "http-" + UUID.randomUUID();
    private static NakshaStorage storageConfig;
    private static HttpStorage storage;

    @BeforeAll
    static void setupStorage() {
        final HttpStorageProperties properties = new HttpStorageProperties();
        properties.setUrl("http://localhost:9090");
        storageConfig = new NakshaStorage(STORAGE_ID, HttpStorage.class.getName()).withProperties(properties);
        storage = (HttpStorage) Naksha.useStorage(storageConfig);
    }

    @AfterAll
    static void removeStorage() {
        Naksha.removeStorage(storageConfig);
    }

    @Test
    void attachesVirtualTuplesWithoutChangingFeatures() {
        final NakshaFeature first = new NakshaFeature("feature-1");
        first.getProperties().setRaw("speedLimit", "60");
        final NakshaFeature second = new NakshaFeature("feature-2");
        final XyzNs remoteMetadata = new XyzNs();
        remoteMetadata.setRaw(XyzNs.UUID, "remote-opaque-token");
        second.getProperties().setRaw(NakshaProperties.XYZ_KEY, remoteMetadata);
        final NakshaFeatureList features = NakshaFeatureList.of(first, second);
        final ReadFeaturesProxyWrapper request = request("collection");
        final HttpStorageReadSession session = session();

        final Response response = session.attachVirtualTupleNumbers(new SuccessResponse(features), request);

        final FeatureTupleList tuples = ((SuccessResponse) response).getFeatureTupleList();
        assertEquals(2, tuples.size());
        assertSame(first, tuples.get(0).getFeature());
        assertSame(second, tuples.get(1).getFeature());
        assertNull(tuples.get(0).tuple);
        assertNull(tuples.get(1).tuple);
        assertEquals(Naksha.databaseNumber(STORAGE_ID), tuples.get(0).tupleNumber.databaseNumber);
        assertEquals(0, tuples.get(0).tupleNumber.catalogNumber);
        assertEquals(Naksha.collectionNumber("collection"), tuples.get(0).tupleNumber.collectionNumber);
        assertEquals(Naksha.featureNumber("feature-1"), tuples.get(0).tupleNumber.featureNumber);
        assertEquals(tuples.get(0).tupleNumber.version, tuples.get(1).tupleNumber.version);
        final Version version = new Version(tuples.get(0).tupleNumber.version);
        assertTrue(version.isDated());
        assertEquals(Action.VERSION, version.action());
        assertEquals("60", first.getProperties().getRaw("speedLimit"));
        assertNull(first.getProperties().getRaw(NakshaProperties.XYZ_KEY));
        assertEquals("remote-opaque-token", second.getProperties().getXyz().getUuid());
    }

    @Test
    void assignsDifferentVersionsToSeparateResponses() {
        final ReadFeaturesProxyWrapper request = request("collection");
        final HttpStorageReadSession firstSession = session();
        final HttpStorageReadSession secondSession = session();
        final SuccessResponse firstResponse = new SuccessResponse(NakshaFeatureList.of(new NakshaFeature("feature-1")));
        final SuccessResponse secondResponse = new SuccessResponse(NakshaFeatureList.of(new NakshaFeature("feature-1")));

        final FeatureTuple first = ((SuccessResponse) firstSession.attachVirtualTupleNumbers(firstResponse, request))
                .getFeatureTupleList().get(0);
        final FeatureTuple second = ((SuccessResponse) secondSession.attachVirtualTupleNumbers(secondResponse, request))
                .getFeatureTupleList().get(0);

        assertNotEquals(first.tupleNumber.version, second.tupleNumber.version);
    }

    @Test
    void derivesNonNullCatalogNumberFromRequest() {
        final NakshaFeature feature = new NakshaFeature("feature-1");

        final FeatureTuple tuple = ((SuccessResponse) session().attachVirtualTupleNumbers(
                new SuccessResponse(NakshaFeatureList.of(feature)), request("catalog", "collection")))
                .getFeatureTupleList().get(0);

        assertEquals(Naksha.catalogNumber("catalog"), tuple.tupleNumber.catalogNumber);
    }

    @Test
    void keepsEmptyAndErrorResponsesSuccessfulAndUnchanged() {
        final HttpStorageReadSession session = session();
        final ReadFeaturesProxyWrapper request = request("collection");
        final SuccessResponse empty = new SuccessResponse(new NakshaFeatureList());
        final ErrorResponse error = new ErrorResponse(NakshaError.NOT_FOUND, "missing");

        final Response emptyResponse = session.attachVirtualTupleNumbers(empty, request);
        final Response errorResponse = session.attachVirtualTupleNumbers(error, request);

        assertTrue(emptyResponse instanceof SuccessResponse);
        assertTrue(((SuccessResponse) emptyResponse).getFeatureTupleList().isEmpty());
        assertSame(error, errorResponse);
    }

    @Test
    void rejectsFeaturesWithoutRawIds() {
        final HttpStorageReadSession session = session();
        final ReadFeaturesProxyWrapper request = request("collection");

        final NakshaException exception = assertThrows(
                NakshaException.class,
                () -> session.attachVirtualTupleNumbers(
                        new SuccessResponse(NakshaFeatureList.of(new NakshaFeature())), request));

        assertEquals(NakshaError.ILLEGAL_ARGUMENT, exception.getError().getCode());
    }

    @Test
    void rejectsBlankFeatureIds() {
        final NakshaFeature feature = new NakshaFeature();
        feature.setRaw(NakshaFeature.ID_KEY, " ");

        final NakshaException exception = assertThrows(
                NakshaException.class,
                () -> session().attachVirtualTupleNumbers(
                        new SuccessResponse(NakshaFeatureList.of(feature)), request("collection")));

        assertEquals(NakshaError.ILLEGAL_ARGUMENT, exception.getError().getCode());
    }

    @Test
    void rejectsBlankCollectionIds() {
        final NakshaFeature feature = new NakshaFeature("feature-1");

        final NakshaException exception = assertThrows(
                NakshaException.class,
                () -> session().attachVirtualTupleNumbers(
                        new SuccessResponse(NakshaFeatureList.of(feature)), request(" ")));

        assertEquals(NakshaError.ILLEGAL_ARGUMENT, exception.getError().getCode());
    }

    @Test
    void rejectsMissingCollectionIds() {
        final NakshaFeature feature = new NakshaFeature("feature-1");

        final NakshaException exception = assertThrows(
                NakshaException.class,
                () -> session().attachVirtualTupleNumbers(
                        new SuccessResponse(NakshaFeatureList.of(feature)), request(null)));

        assertEquals(NakshaError.ILLEGAL_ARGUMENT, exception.getError().getCode());
    }

    private static ReadFeaturesProxyWrapper request(String collectionId) {
        return request(null, collectionId);
    }

    private static ReadFeaturesProxyWrapper request(String catalogId, String collectionId) {
        final ReadFeaturesProxyWrapper request = new ReadFeaturesProxyWrapper();
        request.withCatalogId(catalogId);
        request.withCollection(collectionId);
        return request;
    }

    private static HttpStorageReadSession session() {
        return new HttpStorageReadSession(null, storage, mock(RequestSender.class), HttpInterface.ffwAdapter);
    }
}
