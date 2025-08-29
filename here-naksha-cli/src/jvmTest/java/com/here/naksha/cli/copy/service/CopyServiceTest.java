package com.here.naksha.cli.copy.service;

import com.here.naksha.cli.results.CommandFailure;
import com.here.naksha.cli.results.CommandResult;
import com.here.naksha.cli.results.CommandSuccess;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CopyServiceTest {
    private final NakshaStorage srcNakshaStorage = new NakshaStorage("src", "srcclassname");
    private final NakshaStorage targetNakshaStorage = new NakshaStorage("target", "targetclassname");
    private final CopyElement srcCopyElement = new CopyElement.Builder(srcNakshaStorage)
            .setMapId("srcmap")
            .setCollectionId("srccol")
            .build();
    private final CopyElement targetCopyElement = new CopyElement.Builder(targetNakshaStorage)
            .setMapId("targetmap")
            .setCollectionId("targetcol")
            .build();
    private static SessionOptions sessionOptions;

    @BeforeAll
    static void beforeAll() {
        NakshaContext nakshaContext = NakshaContext.currentContext().withAppId("testAppId");
        sessionOptions = SessionOptions.from(nakshaContext);
    }

    @Test
    void shouldSucceedWithExistingTargetMapAndCollection() {
        // Given: valid target storage with write session
        IStorage targetStorage = createTargetStorage();
        IWriteSession writeSession = createWriteSessionForStorageReturningSuccessResponse(targetStorage);

        // And: valid source storage with read session
        List<NakshaFeature> features = List.of(
                new NakshaFeature("id1"),
                new NakshaFeature("id2")
        );
        IStorage srcStorage = createSourceStorage();
        IReadSession readSession = createReadSessionForStorageReturningSuccessResponse(srcStorage, features);

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                false
        );

        // Then: assert success result
        CommandSuccess<CopyServiceSuccessResultPayload, CopyServiceException> commandSuccess = assertInstanceOf(
                CommandSuccess.class, copyResult
        );

        // And: assert result payload
        CopyServiceSuccessResultPayload payload = commandSuccess.payload();
        assertEquals(features.size(), payload.numberOfCopiedElements());

        // And: assert read request
        List<ReadFeatures> readFeaturesList = captureRequestsOfType(readSession, ReadFeatures.class);
        assertReadFeatures(readFeaturesList);

        // And: assert writes
        List<Write> writes = captureWrites(writeSession);
        assertCreateFeaturesWrites(writes, features);

        // And: assert commit
        verify(writeSession).commit();
    }

    @Test
    void shouldSucceedWithAutoCreateTargetAndAbsentTargetMapAndCollection() {
        // Given: valid target storage with write sessions
        IStorage targetStorage = createTargetStorage();
        IWriteSession createMapWriteSession = createWriteSessionReturningSuccessResponse();
        IWriteSession createCollectionWriteSession = createWriteSessionReturningSuccessResponse();
        IWriteSession createFeaturesWriteSession = createWriteSessionReturningSuccessResponse();
        when(targetStorage.newWriteSession(sessionOptions))
                .thenReturn(createMapWriteSession)
                .thenReturn(createCollectionWriteSession)
                .thenReturn(createFeaturesWriteSession);

        // And: valid source storage with read session
        List<NakshaFeature> features = List.of(
                new NakshaFeature("id1"),
                new NakshaFeature("id2")
        );
        IStorage srcStorage = createSourceStorage();
        IReadSession readSession = createReadSessionForStorageReturningSuccessResponse(srcStorage, features);

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then: assert success result
        CommandSuccess<CopyServiceSuccessResultPayload, CopyServiceException> commandSuccess = assertInstanceOf(
                CommandSuccess.class, copyResult
        );


        // And: assert result payload
        CopyServiceSuccessResultPayload payload = commandSuccess.payload();
        assertEquals(features.size(), payload.numberOfCopiedElements());

        // And: assert read request
        List<ReadFeatures> readFeaturesList = captureRequestsOfType(readSession, ReadFeatures.class);
        assertReadFeatures(readFeaturesList);

        // And: assert create map write
        captureAndAssertCreateMapWrite(createMapWriteSession);
        verify(createMapWriteSession).commit();

        // And: assert create collection write
        captureAndAssertCreateCollectionWrite(createCollectionWriteSession);
        verify(createCollectionWriteSession).commit();

        // And: assert create features writes
        captureAndAssertCreateFeaturesWrites(createFeaturesWriteSession, features);
        verify(createFeaturesWriteSession).commit();
    }

    @Test
    void shouldSucceedWithAutoCreateTargetAndAbsentTargetCollection() {
        // Given: valid target storage with write sessions
        IStorage targetStorage = createTargetStorage();
        IWriteSession createMapWriteSession = createWriteSessionReturningErrorResponse(NakshaError.MAP_EXISTS);
        IWriteSession createCollectionWriteSession = createWriteSessionReturningSuccessResponse();
        IWriteSession createFeaturesWriteSession = createWriteSessionReturningSuccessResponse();
        when(targetStorage.newWriteSession(sessionOptions))
                .thenReturn(createMapWriteSession)
                .thenReturn(createCollectionWriteSession)
                .thenReturn(createFeaturesWriteSession);

        // And: valid source storage with read session
        List<NakshaFeature> features = List.of(
                new NakshaFeature("id1"),
                new NakshaFeature("id2")
        );
        IStorage srcStorage = createSourceStorage();
        IReadSession readSession = createReadSessionForStorageReturningSuccessResponse(srcStorage, features);

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);
        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then: assert success result
        CommandSuccess<CopyServiceSuccessResultPayload, CopyServiceException> commandSuccess = assertInstanceOf(
                CommandSuccess.class, copyResult
        );


        // And: assert result payload
        CopyServiceSuccessResultPayload payload = commandSuccess.payload();
        assertEquals(features.size(), payload.numberOfCopiedElements());

        // And: assert read request
        List<ReadFeatures> readFeaturesList = captureRequestsOfType(readSession, ReadFeatures.class);
        assertReadFeatures(readFeaturesList);

        // And: assert create map write
        captureAndAssertCreateMapWrite(createMapWriteSession);
        verify(createMapWriteSession).rollback();

        // And: assert create collection write
        captureAndAssertCreateCollectionWrite(createCollectionWriteSession);
        verify(createCollectionWriteSession).commit();

        // And: assert create features writes
        captureAndAssertCreateFeaturesWrites(createFeaturesWriteSession, features);
        verify(createFeaturesWriteSession).commit();
    }

    @Test
    void shouldSucceedWithAutoCreateTargetAndExistingTargetMapAndCollection() {
        // Given: valid target storage with write sessions
        IStorage targetStorage = createTargetStorage();
        IWriteSession createMapWriteSession = createWriteSessionReturningErrorResponse(NakshaError.MAP_EXISTS);
        IWriteSession createCollectionWriteSession = createWriteSessionReturningErrorResponse(NakshaError.COLLECTION_EXISTS);
        IWriteSession createFeaturesWriteSession = createWriteSessionReturningSuccessResponse();
        when(targetStorage.newWriteSession(sessionOptions))
                .thenReturn(createMapWriteSession)
                .thenReturn(createCollectionWriteSession)
                .thenReturn(createFeaturesWriteSession);

        // And: valid source storage with read session
        List<NakshaFeature> features = List.of(
                new NakshaFeature("id1"),
                new NakshaFeature("id2")
        );
        IStorage srcStorage = createSourceStorage();
        IReadSession readSession = createReadSessionForStorageReturningSuccessResponse(srcStorage, features);

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);
        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then: assert success result
        CommandSuccess<CopyServiceSuccessResultPayload, CopyServiceException> commandSuccess = assertInstanceOf(
                CommandSuccess.class, copyResult
        );


        // And: assert result payload
        CopyServiceSuccessResultPayload payload = commandSuccess.payload();
        assertEquals(features.size(), payload.numberOfCopiedElements());

        // And: assert read request
        List<ReadFeatures> readFeaturesList = captureRequestsOfType(readSession, ReadFeatures.class);
        assertReadFeatures(readFeaturesList);

        // And: assert create map write
        captureAndAssertCreateMapWrite(createMapWriteSession);
        verify(createMapWriteSession).rollback();

        // And: assert create collection write
        captureAndAssertCreateCollectionWrite(createCollectionWriteSession);
        verify(createCollectionWriteSession).rollback();

        // And: assert create features writes
        captureAndAssertCreateFeaturesWrites(createFeaturesWriteSession, features);
        verify(createFeaturesWriteSession).commit();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldFailWhenReadingFromSourceFails(boolean autoCreateTarget) {
        // Given: failing source storage
        IStorage srcStorage = createFailingSrcStorage();

        // And
        IStorage targetStorage = createValidTargetStorage();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                autoCreateTarget
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Problem with reading from source!");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldFailWhenReadSessionFails(boolean autoCreateTarget) {
        // Given: storage with failing read session
        IStorage srcStorage = createStorageWithFailingReadSession();

        // And
        IStorage targetStorage = createValidTargetStorage();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                autoCreateTarget
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Problem while reading features from source!");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldFailWhenCanNotGetSourceStorage(boolean autoCreateTarget) {
        // Given: failing storage provider on using src storage
        StorageProvider storageProvider = createFailingStorageProvider(srcNakshaStorage);

        // And: valid on using target storage
        IStorage targetStorage = createValidTargetStorage();
        when(storageProvider.useStorage(targetNakshaStorage)).thenReturn(targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                autoCreateTarget
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Can not get source storage!");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldFailOnUnexpectedResponseFromSource(boolean autoCreateTarget) {
        // Given: unexpected response from source storage
        IStorage srcStorage = createSrcStorageWithUnexpectedResponse();

        // And
        IStorage targetStorage = createValidTargetStorage();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                autoCreateTarget
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Unexpected response from source!");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldFailWhenWriteSessionFails(boolean autoCreateTarget) {
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

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                autoCreateTarget
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Problem while writing features to target!");
    }


    @Test
    void shouldFailWhenWritingToTargetFails() {
        // Given: failing target storage with write session
        IStorage targetStorage = createTargetStorage();
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

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                false
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Problem with writing to target!");

        // And
        verify(writeSession).rollback();
    }


    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldFailWhenUnableToUseTarget(boolean autoCreateTarget) {
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

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                autoCreateTarget
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Can not get target storage!");
    }

    @Test
    void shouldFailOnUnexpectedResponseFromTargetWhileWritingFeatures() {
        // Given: unexpected response from target storage
        IStorage targetStorage = createTargetStorage();
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

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                false
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Unexpected response from target!");

        // And
        verify(writeSession).rollback();
    }

    @Test
    void shouldFailOnUnexpectedResponseFromTargetWhileCreatingMap() {
        // Given: valid target storage with write sessions
        IStorage targetStorage = createTargetStorage();
        IWriteSession createMapWriteSession = createWriteSessionReturningUnexpectedResponse();
        when(targetStorage.newWriteSession(sessionOptions))
                .thenReturn(createMapWriteSession);

        // And: valid source storage
        IStorage srcStorage = createValidSrcStorage();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Unexpected response while creating map!");

        // And
        verify(createMapWriteSession).rollback();
    }

    @Test
    void shouldFailOnErrorResponseFromTargetWhileCreatingMap() {
        // Given: valid target storage with write sessions
        IStorage targetStorage = createTargetStorage();
        IWriteSession createMapWriteSession = createWriteSessionReturningErrorResponse(NakshaError.EXCEPTION);
        when(targetStorage.newWriteSession(sessionOptions))
                .thenReturn(createMapWriteSession);

        // And: valid source storage
        IStorage srcStorage = createValidSrcStorage();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Problem with creating map!");

        // And
        verify(createMapWriteSession).rollback();
    }

    @Test
    void shouldFailOnUnexpectedResponseFromTargetWhileCreatingCollection() {
        // Given: valid target storage with write sessions
        IStorage targetStorage = createTargetStorage();
        IWriteSession createMapWriteSession = createWriteSessionReturningSuccessResponse();
        IWriteSession createCollectionWriteSession = createWriteSessionReturningUnexpectedResponse();
        when(targetStorage.newWriteSession(sessionOptions))
                .thenReturn(createMapWriteSession)
                .thenReturn(createCollectionWriteSession);

        // And: valid source storage
        IStorage srcStorage = createValidSrcStorage();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Unexpected response while creating collection!");

        // And
        verify(createCollectionWriteSession).rollback();
    }

    @Test
    void shouldFailOnErrorResponseFromTargetWhileCreatingCollection() {
        // Given: valid target storage with write sessions
        IStorage targetStorage = createTargetStorage();
        IWriteSession createMapWriteSession = createWriteSessionReturningSuccessResponse();
        IWriteSession createCollectionWriteSession = createWriteSessionReturningErrorResponse(NakshaError.EXCEPTION);
        when(targetStorage.newWriteSession(sessionOptions))
                .thenReturn(createMapWriteSession)
                .thenReturn(createCollectionWriteSession);

        // And: valid source storage
        IStorage srcStorage = createValidSrcStorage();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Problem with creating collection!");

        // And
        verify(createCollectionWriteSession).rollback();
    }

    @Test
    void shouldFailWhenAutoCreateTargetAndTargetsMapIdNull() {
        // Given: target copy element without mapId
        CopyElement targetCopyElement = targetCopyElementWithoutMapId();

        // And
        IStorage targetStorage = createValidTargetStorage();
        IStorage srcStorage = createValidSrcStorage();
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Target's mapId should not be null!");
    }

    @Test
    void shouldFailWhenAutoCreateTargetAndTargetsCollectionIdNull() {
        // Given: target copy element without collectionId
        CopyElement targetCopyElement = targetCopyElementWithoutCollectionId();

        // And
        IStorage targetStorage = createValidTargetStorage();
        IStorage srcStorage = createValidSrcStorage();
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = new CopyService(
                storageProvider,
                sessionOptions
        );

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Target's collectionId should not be null!");
    }

    private IStorage createTargetStorage() {
        IStorage targetStorage = mock();
        when(targetStorage.useWriteSession(eq(sessionOptions), any())).thenCallRealMethod();
        doCallRealMethod().when(targetStorage).runInWriteSession(eq(sessionOptions), any());
        return targetStorage;
    }

    private IStorage createSourceStorage() {
        IStorage sourceStorage = mock();
        when(sourceStorage.useReadSession(eq(sessionOptions), any())).thenCallRealMethod();
        doCallRealMethod().when(sourceStorage).runInReadSession(eq(sessionOptions), any());
        return sourceStorage;
    }

    private CopyElement targetCopyElementWithoutMapId() {
        return new CopyElement.Builder(targetNakshaStorage)
                .setCollectionId("col")
                .build();
    }

    private CopyElement targetCopyElementWithoutCollectionId() {
        return new CopyElement.Builder(targetNakshaStorage)
                .setMapId("map")
                .build();
    }

    private void assertIsErrorResultWithGivenMessage(
            CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult,
            String errorMessage
    ) {
        CommandFailure<CopyServiceSuccessResultPayload, CopyServiceException> commandFailure = assertInstanceOf(
                CommandFailure.class, copyResult
        );

        // And: assert result payload
        CopyServiceException exception = commandFailure.payload();
        assertEquals(errorMessage, exception.getMessage());
    }

    private <T extends Request> List<T> captureRequestsOfType(ISession session, Class<T> type) {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(session, atLeastOnce()).execute(captor.capture());
        return captor.getAllValues().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private IWriteSession createWriteSessionForStorage(IStorage storage) {
        IWriteSession writeSession = mock();
        when(storage.newWriteSession(sessionOptions)).thenReturn(writeSession);
        return writeSession;
    }

    private IWriteSession createWriteSessionForStorageReturningErrorResponse(IStorage storage) {
        IWriteSession writeSession = createWriteSessionForStorage(storage);
        when(writeSession.execute(any())).thenReturn(new ErrorResponse());
        return writeSession;
    }

    private IWriteSession createWriteSessionForStorageReturningUnexpectedResponse(IStorage storage) {
        IWriteSession writeSession = createWriteSessionForStorage(storage);
        when(writeSession.execute(any())).thenReturn(new Response());
        return writeSession;
    }

    private IWriteSession createWriteSessionForStorageReturningSuccessResponse(IStorage storage) {
        IWriteSession writeSession = createWriteSessionForStorage(storage);
        when(writeSession.execute(any())).thenReturn(new SuccessResponse());
        return writeSession;
    }

    private IWriteSession createWriteSessionReturningSuccessResponse() {
        IWriteSession writeSession = mock();
        when(writeSession.execute(any())).thenReturn(new SuccessResponse());
        return writeSession;
    }

    private IWriteSession createWriteSessionReturningErrorResponse(String nakshaCode) {
        IWriteSession writeSession = mock();
        NakshaError nakshaError = new NakshaError();
        nakshaError.setCode(nakshaCode);
        when(writeSession.execute(any())).thenReturn(new ErrorResponse(nakshaError));
        return writeSession;
    }

    private IWriteSession createWriteSessionReturningUnexpectedResponse() {
        IWriteSession writeSession = mock();
        when(writeSession.execute(any())).thenReturn(new Response());
        return writeSession;
    }

    private IReadSession createReadSessionForStorageReturningSuccessResponse(IStorage storage, List<NakshaFeature> features) {
        IReadSession readSession = mock();
        when(storage.newReadSession(sessionOptions)).thenReturn(readSession);
        when(readSession.execute(any())).thenReturn(
                new SuccessResponse(
                        NakshaFeatureList.fromList(features)
                )
        );
        return readSession;
    }

    private IStorage createStorageWithFailingWriteSession() {
        IStorage storage = mock();
        when(storage.newWriteSession(sessionOptions)).thenThrow(new RuntimeException());
        when(storage.useWriteSession(eq(sessionOptions), any())).thenCallRealMethod();
        return storage;
    }

    private IStorage createStorageWithFailingReadSession() {
        IStorage storage = mock();
        when(storage.newReadSession(sessionOptions)).thenThrow(new RuntimeException());
        when(storage.useReadSession(eq(sessionOptions), any())).thenCallRealMethod();
        return storage;
    }

    private List<Write> captureWrites(IWriteSession writeSession) {
        return captureRequestsOfType(writeSession, WriteRequest.class).stream()
                .flatMap(wr -> wr.getWrites().stream())
                .toList();
    }

    private void assertCreateFeaturesWrites(List<Write> writes, List<NakshaFeature> expectedFeatures) {
        List<NakshaFeature> actualFeatures = writes.stream()
                .map(w -> {
                    assertCreateWrite(w);
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

    private void assertCreateWrite(Write w) {
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

    private IStorage createValidTargetStorage() {
        IStorage storage = mock();
        when(storage.useWriteSession(eq(sessionOptions), any())).thenReturn(new SuccessResponse());
        return storage;
    }

    private StorageProvider createStorageProvider(IStorage srcStorage, IStorage targetStorage) {
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(srcNakshaStorage)).thenReturn(srcStorage);
        when(storageProvider.useStorage(targetNakshaStorage)).thenReturn(targetStorage);

        return storageProvider;
    }

    private void assertCreateMapWrite(Write write) {
        assertEquals(Naksha.ADMIN_MAP, write.getMapId());
        assertEquals(Naksha.MAPS_COL, write.getCollectionId());
        assertEquals(WriteOp.CREATE, write.getOp());
        assertNotNull(write.getFeature());
        assertEquals(targetCopyElement.getMapId(), write.getFeature().getId());
    }

    private void assertCreateCollectionWrite(Write write) {
        assertEquals(targetCopyElement.getMapId(), write.getMapId());
        assertEquals(Naksha.COLLECTIONS_COL, write.getCollectionId());
        assertEquals(WriteOp.CREATE, write.getOp());
        assertNotNull(write.getFeature());
        assertEquals(targetCopyElement.getCollectionId(), write.getFeature().getId());
    }

    private void captureAndAssertCreateMapWrite(IWriteSession createMapWriteSession) {
        List<Write> createMapWrites = captureWrites(createMapWriteSession);
        assertEquals(1, createMapWrites.size(), "Should be only one create map write");
        Write createMapWrite = createMapWrites.getFirst();
        assertCreateMapWrite(createMapWrite);
    }

    private void captureAndAssertCreateCollectionWrite(IWriteSession createCollectionWriteSession) {
        List<Write> createCollectionWrites = captureWrites(createCollectionWriteSession);
        assertEquals(1, createCollectionWrites.size(), "Should be only one create collection write");
        Write createCollectionWrite = createCollectionWrites.getFirst();
        assertCreateCollectionWrite(createCollectionWrite);
    }

    private void captureAndAssertCreateFeaturesWrites(IWriteSession createFeaturesWriteSession, List<NakshaFeature> features) {
        List<Write> createFeaturesWrites = captureWrites(createFeaturesWriteSession);
        assertCreateFeaturesWrites(createFeaturesWrites, features);
    }
}