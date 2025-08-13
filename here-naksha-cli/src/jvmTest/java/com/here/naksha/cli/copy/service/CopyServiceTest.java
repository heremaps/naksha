package com.here.naksha.cli.copy.service;

import naksha.base.NakshaException;
import naksha.base.fn.Fn1;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
    private static SessionOptions sessionOptions;

    @BeforeAll
    static void beforeAll() {
        NakshaContext nakshaContext = NakshaContext.currentContext().withAppId("testAppId");
        sessionOptions = SessionOptions.from(nakshaContext);
    }

    @Test
    void shouldSucceed() {
        // Given: valid target storage with write session
        IStorage targetStorage = mock();
        IWriteSession writeSession = createWriteSessionForStorageReturningSuccessResponse(targetStorage);

        // And: valid source storage with read session
        List<NakshaFeature> features = List.of(
                new NakshaFeature("id1"),
                new NakshaFeature("id2")
        );
        IStorage srcStorage = mock();
        IReadSession readSession = createReadSessionForStorageReturningSuccessResponse(srcStorage, features);

        // And
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(srcNakshaStorage)).thenReturn(srcStorage);
        when(storageProvider.useStorage(targetNakshaStorage)).thenReturn(targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        assertDoesNotThrow(() -> copyService.copy(
                srcCopyElement,
                targetCopyElement
        ));

        // Then: assert read request
        List<ReadFeatures> readFeaturesList = captureRequestsOfType(readSession, ReadFeatures.class);
        assertReadFeatures(readFeaturesList);

        // And: assert writes
        List<Write> writes = captureWrites(writeSession);
        assertWrites(writes, features);

        // And: assert commit
        verify(writeSession).commit();
    }

    @Test
    void shouldFailWhenReadingFromSourceFails() {
        // Given: failing source storage
        IStorage srcStorage = createFailingSrcStorage();

        // And
        StorageProvider storageProvider = createStorageProviderReturningSrcStorage(srcStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        CopyServiceException exception = assertThrows(CopyServiceException.class, () -> copyService.copy(
                srcCopyElement,
                targetCopyElement
        ));

        assertEquals("Problem with reading from source!", exception.getMessage());
    }

    @Test
    void shouldFailWhenReadSessionFails() {
        // Given: storage with failing read session
        IStorage srcStorage = createStorageWithFailingReadSession();

        // And
        StorageProvider storageProvider = createStorageProviderReturningSrcStorage(srcStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        CopyServiceException exception = assertThrows(CopyServiceException.class, () -> copyService.copy(
                srcCopyElement,
                targetCopyElement
        ));

        assertEquals("Problem while reading features from source!", exception.getMessage());
    }

    @Test
    void shouldFailWhenCanNotGetSourceStorage() {
        // Given: failing storage provider
        StorageProvider storageProvider = createFailingStorageProvider(srcNakshaStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        CopyServiceException exception = assertThrows(CopyServiceException.class, () -> copyService.copy(
                srcCopyElement,
                targetCopyElement
        ));

        assertEquals("Can not get source storage!", exception.getMessage());
    }

    @Test
    void shouldFailOnUnexpectedResponseFromSource() {
        // Given: unexpected response from source storage
        IStorage srcStorage = createSrcStorageWithUnexpectedResponse();

        // And
        StorageProvider storageProvider = createStorageProviderReturningSrcStorage(srcStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        CopyServiceException exception = assertThrows(CopyServiceException.class, () -> copyService.copy(
                srcCopyElement,
                targetCopyElement
        ));

        assertEquals("Unexpected response from source!", exception.getMessage());
    }

    @Test
    void shouldFailWhenWriteSessionFails() {
        // Given: target storage with failing write session
        IStorage targetStorage = createStorageWithFailingWriteSession();

        // And: valid source storage
        IStorage srcStorage = createValidSrcStorage();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        CopyServiceException exception = assertThrows(CopyServiceException.class, () -> copyService.copy(
                srcCopyElement,
                targetCopyElement
        ));
        assertEquals("Problem while writing features to target!", exception.getMessage());
    }

    @Test
    void shouldFailWhenWritingToTargetFails() {
        // Given: failing target storage with write session
        IStorage targetStorage = mock();
        IWriteSession writeSession = createWriteSessionForStorageReturningErrorResponse(targetStorage);

        // And: valid source storage
        IStorage srcStorage = createValidSrcStorage();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        CopyServiceException exception = assertThrows(CopyServiceException.class, () -> copyService.copy(
                srcCopyElement,
                targetCopyElement
        ));
        assertEquals("Problem with writing to target!", exception.getMessage());

        // And
        verify(writeSession).rollback();
    }


    @Test
    void shouldFailWhenUnableToUseTarget() {
        // Given: failing storage provider
        StorageProvider storageProvider = createFailingStorageProvider(targetNakshaStorage);

        // And: valid source storage
        IStorage srcStorage = createValidSrcStorage();
        when(storageProvider.useStorage(srcNakshaStorage)).thenReturn(srcStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        CopyServiceException exception = assertThrows(CopyServiceException.class, () -> copyService.copy(
                srcCopyElement,
                targetCopyElement
        ));

        assertEquals("Can not get target storage!", exception.getMessage());
    }

    @Test
    void shouldFailOnUnexpectedResponseFromTarget() {
        // Given: unexpected response from target storage
        IStorage targetStorage = mock();
        IWriteSession writeSession = createWriteSessionForStorageReturningUnexpectedResponse(targetStorage);

        // And: valid source storage
        IStorage srcStorage = createValidSrcStorage();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When & Then
        CopyServiceException exception = assertThrows(CopyServiceException.class, () -> copyService.copy(
                srcCopyElement,
                targetCopyElement
        ));
        assertEquals("Unexpected response from target!", exception.getMessage());

        // And
        verify(writeSession).rollback();
    }

    private <T extends Request> List<T> captureRequestsOfType(ISession session, Class<T> type) {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(session, atLeastOnce()).execute(captor.capture());
        return captor.getAllValues().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private IWriteSession createWriteSession(IStorage storage) {
        IWriteSession writeSession = mock();
        when(storage.useWriteSession(eq(sessionOptions), any()))
                .thenAnswer(invocation -> {
                    Fn1<Response, IWriteSession> lambda = invocation.getArgument(1);
                    return lambda.call(writeSession);
                });
        return writeSession;
    }

    private IWriteSession createWriteSessionForStorageReturningErrorResponse(IStorage storage) {
        IWriteSession writeSession = createWriteSession(storage);
        when(writeSession.execute(any())).thenReturn(new ErrorResponse());
        return writeSession;
    }

    private IWriteSession createWriteSessionForStorageReturningUnexpectedResponse(IStorage storage) {
        IWriteSession writeSession = createWriteSession(storage);
        when(writeSession.execute(any())).thenReturn(new Response());
        return writeSession;
    }

    private IWriteSession createWriteSessionForStorageReturningSuccessResponse(IStorage storage) {
        IWriteSession writeSession = createWriteSession(storage);
        when(writeSession.execute(any())).thenReturn(new SuccessResponse());
        return writeSession;
    }

    private IReadSession createReadSessionForStorageReturningSuccessResponse(IStorage storage, List<NakshaFeature> features) {
        IReadSession readSession = mock();
        when(storage.useReadSession(eq(sessionOptions), any()))
                .thenAnswer(invocation -> {
                    Fn1<Response, IReadSession> lambda = invocation.getArgument(1);
                    return lambda.call(readSession);
                });
        when(readSession.execute(any())).thenReturn(
                new SuccessResponse(
                        NakshaFeatureList.fromList(features)
                )
        );
        return readSession;
    }

    private IStorage createStorageWithFailingWriteSession() {
        IStorage storage = mock();
        when(storage.newWriteSession(any())).thenThrow(new RuntimeException());
        when(storage.useWriteSession(any(), any())).thenCallRealMethod();
        return storage;
    }

    private IStorage createStorageWithFailingReadSession() {
        IStorage storage = mock();
        when(storage.newReadSession(any())).thenThrow(new RuntimeException());
        when(storage.useReadSession(any(), any())).thenCallRealMethod();
        return storage;
    }

    private List<Write> captureWrites(IWriteSession writeSession) {
        return captureRequestsOfType(writeSession, WriteRequest.class).stream()
                .flatMap(wr -> wr.getWrites().stream())
                .toList();
    }

    private void assertWrites(List<Write> writes, List<NakshaFeature> expectedFeatures) {
        List<NakshaFeature> actualFeatures = writes.stream()
                .map(w -> {
                    assertWrite(w);
                    return w.getFeature();
                })
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

    private void assertWrite(Write w) {
        assertEquals(WriteOp.CREATE, w.getOp(), "Every write operation should be CREATE");
        assertEquals(targetCopyElement.getCollectionId(), w.getCollectionId(),
                "Every write Collection ID should match target Collection ID"
        );
        assertEquals(targetCopyElement.getMapId(), w.getMapId(),
                "Every write Map ID should match target Map ID"
        );
    }

    private void assertReadFeatures(List<ReadFeatures> readFeaturesList) {
        assertEquals(1, readFeaturesList.size());
        ReadFeatures readFeatures = readFeaturesList.getFirst();
        assertEquals(1, readFeatures.getCollectionIds().getSize());
        assertEquals(srcCopyElement.getCollectionId(), readFeatures.getCollectionIds().getFirst());
        assertEquals(srcCopyElement.getMapId(), readFeatures.getMapId());
    }

    private IStorage createFailingSrcStorage() {
        IStorage srcStorage = mock();
        when(srcStorage.useReadSession(eq(sessionOptions), any())).thenReturn(new ErrorResponse());
        return srcStorage;
    }

    private StorageProvider createStorageProviderReturningSrcStorage(IStorage srcStorage) {
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(srcNakshaStorage)).thenReturn(srcStorage);
        return storageProvider;
    }

    private StorageProvider createFailingStorageProvider(NakshaStorage nakshaStorage) {
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(nakshaStorage)).thenThrow(new NakshaException("", ""));
        return storageProvider;
    }

    private IStorage createSrcStorageWithUnexpectedResponse() {
        IStorage srcStorage = mock();
        when(srcStorage.useReadSession(eq(sessionOptions), any())).thenReturn(new Response());
        return srcStorage;
    }

    private IStorage createValidSrcStorage() {
        IStorage srcStorage = mock();
        when(srcStorage.useReadSession(eq(sessionOptions), any())).thenReturn(new SuccessResponse());
        return srcStorage;
    }

    private StorageProvider createStorageProvider(IStorage srcStorage, IStorage targetStorage) {
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(srcNakshaStorage)).thenReturn(srcStorage);
        when(storageProvider.useStorage(targetNakshaStorage)).thenReturn(targetStorage);

        return storageProvider;
    }
}