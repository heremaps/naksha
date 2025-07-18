package com.here.naksha.cli.copy.service;

import naksha.base.fn.Fn1;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CopyServiceTest {
    private final NakshaStorage srcNakshaStorage = new NakshaStorage("src", "srcclassname");
    private final NakshaStorage targetNakshaStorage = new NakshaStorage("target", "targetclassname");
    private final CopyElement srcCopyElement = new CopyElement.Builder(srcNakshaStorage, "srccol")
            .setMapId("srcmap")
            .build();
    private final CopyElement targetCopyElement = new CopyElement.Builder(targetNakshaStorage, "targetcol")
            .setMapId("targetmap")
            .build();
    private final SessionOptions sessionOptions = mock();

    private <T extends Request> List<T> captureRequestsOfType(ISession session, Class<T> type) {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(session, atLeastOnce()).execute(captor.capture());
        return captor.getAllValues().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private IStorage createStorageReturningSuccessResponseOnRead(List<NakshaFeature> features) {
        IStorage storage = mock();
        when(storage.useReadSession(eq(sessionOptions), any()))
                .thenReturn(
                        new SuccessResponse(
                                NakshaFeatureList.fromList(features)
                        )
                );
        return storage;
    }

    private IStorage createStorageReturningSuccessResponseOnWrite() {
        IStorage storage = mock();
        when(storage.useWriteSession(eq(sessionOptions), any())).thenReturn(new SuccessResponse());
        return storage;
    }

    private IWriteSession createWriteSessionForStorageReturningSuccessResponse(IStorage storage) {
        IWriteSession writeSession = mock();
        when(storage.useWriteSession(eq(sessionOptions), any()))
                .thenAnswer(invocation -> {
                    Fn1<Response, IWriteSession> lambda = invocation.getArgument(1);
                    return lambda.call(writeSession);
                });
        when(writeSession.execute(any())).thenReturn(new SuccessResponse());
        return writeSession;
    }

    private IReadSession createReadSessionForStorageReturningSuccessResponse(IStorage storage) {
        IReadSession readSession = mock();
        when(storage.useReadSession(eq(sessionOptions), any()))
                .thenAnswer(invocation -> {
                    Fn1<Response, IReadSession> lambda = invocation.getArgument(1);
                    return lambda.call(readSession);
                });
        when(readSession.execute(any())).thenReturn(new SuccessResponse());
        return readSession;
    }

    private List<Write> captureWrites(IWriteSession writeSession) {
        return captureRequestsOfType(writeSession, WriteRequest.class).stream()
                .flatMap(wr -> wr.getWrites().stream())
                .toList();
    }

    private void assertWrites(List<Write> writes, List<NakshaFeature> expectedFeatures) {
        for (Write w : writes) {
            assertEquals(WriteOp.CREATE, w.getOp(), "Every write operation should be CREATE");
        }

        for (Write w : writes) {
            assertEquals(targetCopyElement.getCollectionId(), w.getCollectionId(),
                    "Every write Collection ID should match target Collection ID"
            );
        }

        for (Write w : writes) {
            assertEquals(targetCopyElement.getMapId(), w.getMapId(),
                    "Every write Map ID should match target Collection ID"
            );
        }

        List<NakshaFeature> actualFeatures = writes.stream()
                .map(Write::getFeature)
                .toList();

        assertEquals(
                expectedFeatures.size(), actualFeatures.size(),
                "Features sizes do not match"
        );
        assertTrue(
                actualFeatures.containsAll(expectedFeatures) && expectedFeatures.containsAll(actualFeatures),
                "Feature lists do not contain the same elements"
        );
    }

    private void assertReadFeatures(List<ReadFeatures> readFeaturesList) {
        assertEquals(1, readFeaturesList.size());
        ReadFeatures readFeatures = readFeaturesList.getFirst();
        assertEquals(1, readFeatures.getCollectionIds().getSize());
        assertEquals(srcCopyElement.getCollectionId(), readFeatures.getCollectionIds().getFirst());
        assertEquals(srcCopyElement.getMapId(), readFeatures.getMapId());
    }

    @Test
    void shouldNotFail() {
        // Given: good target storage
        IStorage targetStorage = createStorageReturningSuccessResponseOnWrite();

        // And: good source storage
        IStorage srcStorage = createStorageReturningSuccessResponseOnRead(Collections.emptyList());

        // And
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(eq(srcNakshaStorage))).thenReturn(srcStorage);
        when(storageProvider.useStorage(eq(targetNakshaStorage))).thenReturn(targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        assertDoesNotThrow(() -> {
            copyService.copy(
                    srcCopyElement,
                    targetCopyElement
            );
        });
    }

    @Test
    void shouldExecuteGoodWriteRequest() {
        // Given: good target storage with write session
        IStorage targetStorage = mock();
        IWriteSession writeSession = createWriteSessionForStorageReturningSuccessResponse(targetStorage);

        // And: good source storage
        List<NakshaFeature> features = List.of(
                new NakshaFeature("id1"),
                new NakshaFeature("id2")
        );
        IStorage srcStorage = createStorageReturningSuccessResponseOnRead(features);

        // And
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(eq(srcNakshaStorage))).thenReturn(srcStorage);
        when(storageProvider.useStorage(eq(targetNakshaStorage))).thenReturn(targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        assertDoesNotThrow(() -> {
            copyService.copy(
                    srcCopyElement,
                    targetCopyElement
            );
        });

        // Then
        List<Write> writes = captureWrites(writeSession);
        assertWrites(writes, features);
    }

    @Test
    void shouldExecuteGoodReadRequest() {
        // Given: good target storage
        IStorage targetStorage = createStorageReturningSuccessResponseOnWrite();

        // And: good source storage with read session
        IStorage srcStorage = mock();
        IReadSession readSession = createReadSessionForStorageReturningSuccessResponse(srcStorage);

        // And
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(eq(srcNakshaStorage))).thenReturn(srcStorage);
        when(storageProvider.useStorage(eq(targetNakshaStorage))).thenReturn(targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        assertDoesNotThrow(() -> {
            copyService.copy(
                    srcCopyElement,
                    targetCopyElement
            );
        });

        // Then
        List<ReadFeatures> readFeaturesList = captureRequestsOfType(readSession, ReadFeatures.class);
        assertReadFeatures(readFeaturesList);
    }

    @Test
    void shouldFailWhenCopyingFromInvalidSourceStorage() {
        // Given: failing source storage
        IStorage srcStorage = mock();
        when(srcStorage.useReadSession(eq(sessionOptions), any())).thenReturn(new ErrorResponse());

        // And
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(srcNakshaStorage)).thenReturn(srcStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        CopyServiceException exception = assertThrows(CopyServiceException.class, () -> {
            copyService.copy(
                    srcCopyElement,
                    targetCopyElement
            );
        });

        assertEquals("Problem with reading from source!", exception.getMessage());
        assertInstanceOf(NakshaException.class, exception.getCause());
    }

    @Test
    void shouldFailWhenCanNotGetSourceStorage() {
        // Given: failing storage provider
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(srcNakshaStorage)).thenThrow(new NakshaException("", ""));

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        CopyServiceException exception = assertThrows(CopyServiceException.class, () -> {
            copyService.copy(
                    srcCopyElement,
                    targetCopyElement
            );
        });

        assertEquals("Can not get source storage!", exception.getMessage());
        assertInstanceOf(NakshaException.class, exception.getCause());
    }

    @Test
    void shouldFailWhenGetUnexpectedResponseFromSource() {
        // Given: unexpected response from source storage
        IStorage srcStorage = mock();
        when(srcStorage.useReadSession(eq(sessionOptions), any())).thenReturn(new Response());

        // And
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(srcNakshaStorage)).thenReturn(srcStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        CopyServiceException exception = assertThrows(CopyServiceException.class, () -> {
            copyService.copy(
                    srcCopyElement,
                    targetCopyElement
            );
        });

        assertEquals("Unexpected response from source!", exception.getMessage());
    }

    @Test
    void shouldFailWhenCopingToInvalidTargetStorage() {
        // Given: failing target storage
        IStorage targetStorage = mock();
        when(targetStorage.useWriteSession(eq(sessionOptions), any())).thenReturn(new ErrorResponse());

        // And: good source storage
        IStorage srcStorage = mock();
        when(srcStorage.useReadSession(eq(sessionOptions), any())).thenReturn(new SuccessResponse());

        // And
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(eq(srcNakshaStorage))).thenReturn(srcStorage);
        when(storageProvider.useStorage(eq(targetNakshaStorage))).thenReturn(targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        CopyServiceException exception = assertThrows(CopyServiceException.class, () -> {
            copyService.copy(
                    srcCopyElement,
                    targetCopyElement
            );
        });

        assertEquals("Problem with writing to target!", exception.getMessage());
        assertInstanceOf(NakshaException.class, exception.getCause());
    }

    @Test
    void shouldFailWhenCanNotGetTargetStorage() {
        // Given: failing storage provider
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(eq(targetNakshaStorage))).thenThrow(new NakshaException("", ""));

        // And: good source storage
        IStorage srcStorage = mock();
        when(srcStorage.useReadSession(eq(sessionOptions), any())).thenReturn(new SuccessResponse());
        when(storageProvider.useStorage(eq(srcNakshaStorage))).thenReturn(srcStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        CopyServiceException exception = assertThrows(CopyServiceException.class, () -> {
            copyService.copy(
                    srcCopyElement,
                    targetCopyElement
            );
        });

        assertEquals("Can not get target storage!", exception.getMessage());
        assertInstanceOf(NakshaException.class, exception.getCause());
    }

    @Test
    void shouldFailWhenGetUnexpectedResponseFromTarget() {
        // Given: unexpected response from target storage
        IStorage targetStorage = mock();
        when(targetStorage.useWriteSession(eq(sessionOptions), any())).thenReturn(new Response());

        // And: good source storage
        IStorage srcStorage = mock();
        when(srcStorage.useReadSession(eq(sessionOptions), any())).thenReturn(new SuccessResponse());

        // And
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(eq(srcNakshaStorage))).thenReturn(srcStorage);
        when(storageProvider.useStorage(eq(targetNakshaStorage))).thenReturn(targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        CopyServiceException exception = assertThrows(CopyServiceException.class, () -> {
            copyService.copy(
                    srcCopyElement,
                    targetCopyElement
            );
        });

        assertEquals("Unexpected response from target!", exception.getMessage());
    }
}