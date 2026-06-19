package com.here.naksha.cli.copy.service;

import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorException;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorInfo;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static com.here.naksha.cli.copy.service.CopyServiceTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
    @Mock
    private FeaturesWriteExecutor featuresWriteExecutor;
    private static SessionOptions sessionOptions;

    @BeforeAll
    static void beforeAll() {
        NakshaContext nakshaContext = NakshaContext.currentContext().withAppId("testAppId");
        sessionOptions = SessionOptions.from(nakshaContext);
    }

    @Test
    void shouldSucceedWithExistingTargetMapAndCollection() throws FeaturesWriteExecutorException {
        // Given: target storage
        IStorage targetStorage = mock();

        // And: valid source storage with read session
        List<NakshaFeature> featuresToWrite = List.of(
                new NakshaFeature("id1"),
                new NakshaFeature("id2")
        );

        // And
        validFeaturesWriteExecutorReturningGivenInfo(targetStorage, new FeaturesWriteExecutorInfo(featuresToWrite.size()));
        IStorage srcStorage = createSourceStorage(sessionOptions);
        IReadSession readSession = createReadSessionForStorageReturningSuccessResponse(srcStorage, featuresToWrite);

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

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
        assertEquals(featuresToWrite.size(), payload.numberOfCopiedElements());

        // And: assert read request
        List<ReadFeatures> readFeaturesList = captureRequestsOfType(readSession, ReadFeatures.class);
        assertReadFeatures(readFeaturesList);

        // And
        assertValidFeaturesPassedToFeaturesWriteExecutor(targetStorage, featuresToWrite);
    }

    @Test
    void shouldSucceedWithAutoCreateTargetAndAbsentTargetMapAndCollection() throws FeaturesWriteExecutorException {
        // Given: valid target storage with write sessions
        IStorage targetStorage = createTargetStorage(sessionOptions);
        IWriteSession createMapWriteSession = createWriteSessionReturningSuccessResponse();
        IWriteSession createCollectionWriteSession = createWriteSessionReturningSuccessResponse();
        when(targetStorage.newWriteSession(sessionOptions))
                .thenReturn(createMapWriteSession)
                .thenReturn(createCollectionWriteSession);

        // And: valid source storage with read session
        List<NakshaFeature> featuresToWrite = List.of(
                new NakshaFeature("id1"),
                new NakshaFeature("id2")
        );
        IStorage srcStorage = createSourceStorage(sessionOptions);
        IReadSession readSession = createReadSessionForStorageReturningSuccessResponse(srcStorage, featuresToWrite);

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        validFeaturesWriteExecutorReturningGivenInfo(targetStorage, new FeaturesWriteExecutorInfo(featuresToWrite.size()));

        // And
        CopyService copyService = buildCopyService(storageProvider);

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
        assertEquals(featuresToWrite.size(), payload.numberOfCopiedElements());

        // And: assert read request
        List<ReadFeatures> readFeaturesList = captureRequestsOfType(readSession, ReadFeatures.class);
        assertReadFeatures(readFeaturesList);

        // And: assert create map write
        captureAndAssertCreateMapWrite(createMapWriteSession);
        verify(createMapWriteSession).commit();

        // And: assert create collection write
        captureAndAssertCreateCollectionWrite(createCollectionWriteSession);
        verify(createCollectionWriteSession).commit();

        // And
        assertValidFeaturesPassedToFeaturesWriteExecutor(targetStorage, featuresToWrite);
    }

    @Test
    void shouldSucceedWithAutoCreateTargetAndAbsentTargetCollection() throws FeaturesWriteExecutorException {
        // Given: valid target storage with write sessions
        IStorage targetStorage = createTargetStorage(sessionOptions);
        IWriteSession createMapWriteSession = createWriteSessionReturningErrorResponse(NakshaError.CATALOG_EXISTS);
        IWriteSession createCollectionWriteSession = createWriteSessionReturningSuccessResponse();
        when(targetStorage.newWriteSession(sessionOptions))
                .thenReturn(createMapWriteSession)
                .thenReturn(createCollectionWriteSession);

        // And: valid source storage with read session
        List<NakshaFeature> featuresToWrite = List.of(
                new NakshaFeature("id1"),
                new NakshaFeature("id2")
        );
        IStorage srcStorage = createSourceStorage(sessionOptions);
        IReadSession readSession = createReadSessionForStorageReturningSuccessResponse(srcStorage, featuresToWrite);

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        validFeaturesWriteExecutorReturningGivenInfo(targetStorage, new FeaturesWriteExecutorInfo(featuresToWrite.size()));

        // And
        CopyService copyService = buildCopyService(storageProvider);

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
        assertEquals(featuresToWrite.size(), payload.numberOfCopiedElements());

        // And: assert read request
        List<ReadFeatures> readFeaturesList = captureRequestsOfType(readSession, ReadFeatures.class);
        assertReadFeatures(readFeaturesList);

        // And: assert create map write
        captureAndAssertCreateMapWrite(createMapWriteSession);
        verify(createMapWriteSession).rollback();

        // And: assert create collection write
        captureAndAssertCreateCollectionWrite(createCollectionWriteSession);
        verify(createCollectionWriteSession).commit();

        // And
        assertValidFeaturesPassedToFeaturesWriteExecutor(targetStorage, featuresToWrite);
    }

    @Test
    void shouldSucceedWithAutoCreateTargetAndExistingTargetMapAndCollection() throws FeaturesWriteExecutorException {
        // Given: valid target storage with write sessions
        IStorage targetStorage = createTargetStorage(sessionOptions);
        IWriteSession createMapWriteSession = createWriteSessionReturningErrorResponse(NakshaError.CATALOG_EXISTS);
        IWriteSession createCollectionWriteSession = createWriteSessionReturningErrorResponse(NakshaError.COLLECTION_EXISTS);
        when(targetStorage.newWriteSession(sessionOptions))
                .thenReturn(createMapWriteSession)
                .thenReturn(createCollectionWriteSession);

        // And: valid source storage with read session
        List<NakshaFeature> featuresToWrite = List.of(
                new NakshaFeature("id1"),
                new NakshaFeature("id2")
        );
        IStorage srcStorage = createSourceStorage(sessionOptions);
        IReadSession readSession = createReadSessionForStorageReturningSuccessResponse(srcStorage, featuresToWrite);

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        validFeaturesWriteExecutorReturningGivenInfo(targetStorage, new FeaturesWriteExecutorInfo(featuresToWrite.size()));

        // And
        CopyService copyService = buildCopyService(storageProvider);

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
        assertEquals(featuresToWrite.size(), payload.numberOfCopiedElements());

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
        assertValidFeaturesPassedToFeaturesWriteExecutor(targetStorage, featuresToWrite);
    }

    @Test
    void shouldFailWhenReadingFromSourceFailsAndAutoCreateTarget() {
        // Given: failing source storage
        IStorage srcStorage = createFailingSrcStorage();

        // And
        IStorage targetStorage = createValidTargetStorage();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Problem with reading from source!");
    }

    @Test
    void shouldFailWhenReadingFromSourceFailsAndWithoutAutoCreateTarget() {
        // Given: failing source storage
        IStorage srcStorage = createFailingSrcStorage();

        // And
        IStorage targetStorage = mock();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                false
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Problem with reading from source!");
    }

    @Test
    void shouldFailWhenReadSessionFailsAndAutoCreateTarget() {
        // Given: storage with failing read session
        IStorage srcStorage = createStorageWithFailingReadSession();

        // And
        IStorage targetStorage = createValidTargetStorage();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Problem while reading features from source!");
    }

    @Test
    void shouldFailWhenReadSessionFailsAndWithoutAutoCreateTarget() {
        // Given: storage with failing read session
        IStorage srcStorage = createStorageWithFailingReadSession();

        // And
        IStorage targetStorage = mock();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                false
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Problem while reading features from source!");
    }

    @Test
    void shouldFailWhenCanNotGetSourceStorageAndAutoCreateTarget() {
        // Given: failing storage provider on using src storage
        StorageProvider storageProvider = createFailingStorageProvider(srcNakshaStorage);

        // And: valid on using target storage
        IStorage targetStorage = createValidTargetStorage();
        when(storageProvider.useStorage(targetNakshaStorage)).thenReturn(targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Can not get source storage!");
    }

    @Test
    void shouldFailWhenCanNotGetSourceStorageAndWithoutAutoCreateTarget() {
        // Given: failing storage provider on using src storage
        StorageProvider storageProvider = createFailingStorageProvider(srcNakshaStorage);

        // And: valid on using target storage
        IStorage targetStorage = mock();
        when(storageProvider.useStorage(targetNakshaStorage)).thenReturn(targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                false
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Can not get source storage!");
    }

    @Test
    void shouldFailOnUnexpectedResponseFromSourceAndAutoCreateTarget() {
        // Given: unexpected response from source storage
        IStorage srcStorage = createSrcStorageWithUnexpectedResponse();

        // And
        IStorage targetStorage = createValidTargetStorage();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Unexpected response from source!");
    }

    @Test
    void shouldFailOnUnexpectedResponseFromSourceAndWithoutAutoCreateTarget() {
        // Given: unexpected response from source storage
        IStorage srcStorage = createSrcStorageWithUnexpectedResponse();

        // And
        IStorage targetStorage = mock();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                false
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Unexpected response from source!");
    }

    @Test
    void shouldFailWhenWriteSessionFailsAndAutoCreateTarget() {
        // Given: target storage with failing write session
        IStorage targetStorage = createStorageWithFailingWriteSession();

        // And
        StorageProvider storageProvider = storageProviderWithTargetStorage(targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Problem while writing features to target!");
    }


    @Test
    void shouldFailWhenWritingToTargetFails() throws FeaturesWriteExecutorException {
        // Given: failing target storage with write session
        IStorage targetStorage = mock();
        throwingFeaturesWriteExecutor(targetStorage);

        // And: valid source storage
        IStorage srcStorage = createValidSrcStorage();

        // And
        StorageProvider storageProvider = createStorageProvider(srcStorage, targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                false
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Problem with writing to target!");
    }


    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldFailWhenUnableToUseTarget(boolean autoCreateTarget) {
        // Given: failing storage provider
        StorageProvider storageProvider = createFailingStorageProvider(targetNakshaStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

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
    void shouldFailOnUnexpectedResponseFromTargetWhileCreatingMap() {
        // Given: valid target storage with write sessions
        IStorage targetStorage = createTargetStorage(sessionOptions);
        IWriteSession createMapWriteSession = createWriteSessionReturningUnexpectedResponse();
        when(targetStorage.newWriteSession(sessionOptions))
                .thenReturn(createMapWriteSession);

        // And
        StorageProvider storageProvider = storageProviderWithTargetStorage(targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

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
        IStorage targetStorage = createTargetStorage(sessionOptions);
        IWriteSession createMapWriteSession = createWriteSessionReturningErrorResponse(NakshaError.EXCEPTION);
        when(targetStorage.newWriteSession(sessionOptions))
                .thenReturn(createMapWriteSession);

        // And
        StorageProvider storageProvider = storageProviderWithTargetStorage(targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

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
        IStorage targetStorage = createTargetStorage(sessionOptions);
        IWriteSession createMapWriteSession = createWriteSessionReturningSuccessResponse();
        IWriteSession createCollectionWriteSession = createWriteSessionReturningUnexpectedResponse();
        when(targetStorage.newWriteSession(sessionOptions))
                .thenReturn(createMapWriteSession)
                .thenReturn(createCollectionWriteSession);

        // And
        StorageProvider storageProvider = storageProviderWithTargetStorage(targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

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
        IStorage targetStorage = createTargetStorage(sessionOptions);
        IWriteSession createMapWriteSession = createWriteSessionReturningSuccessResponse();
        IWriteSession createCollectionWriteSession = createWriteSessionReturningErrorResponse(NakshaError.EXCEPTION);
        when(targetStorage.newWriteSession(sessionOptions))
                .thenReturn(createMapWriteSession)
                .thenReturn(createCollectionWriteSession);

        // And
        StorageProvider storageProvider = storageProviderWithTargetStorage(targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

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
        IStorage targetStorage = mock();
        StorageProvider storageProvider = storageProviderWithTargetStorage(targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

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
        IStorage targetStorage = mock();
        StorageProvider storageProvider = storageProviderWithTargetStorage(targetStorage);

        // And
        CopyService copyService = buildCopyService(storageProvider);

        // When
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copyService.copy(
                srcCopyElement,
                targetCopyElement,
                true
        );

        // Then
        assertIsErrorResultWithGivenMessage(copyResult, "Target's collectionId should not be null!");
    }

    private StorageProvider storageProviderWithTargetStorage(IStorage targetStorage) {
        StorageProvider storageProvider = mock();
        when(storageProvider.useStorage(targetNakshaStorage)).thenReturn(targetStorage);
        return storageProvider;
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

    private List<NakshaFeature> captureFeaturesWriteExecutorsFeatureTupleArg(
            IStorage storage
    ) throws FeaturesWriteExecutorException {
        ArgumentCaptor<FeatureTupleList> captor = ArgumentCaptor.forClass(FeatureTupleList.class);
        verify(featuresWriteExecutor, atLeastOnce()).write(
                eq(storage), eq(targetCopyElement), captor.capture(), eq(sessionOptions)
        );
        return captor.getAllValues().stream()
                .flatMap(Collection::stream)
                .map(FeatureTuple::getFeature)
                .toList();
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

    private void assertReadFeatures(List<ReadFeatures> readFeaturesList) {
        assertEquals(1, readFeaturesList.size());
        ReadFeatures readFeatures = readFeaturesList.getFirst();
        assertEquals(1, readFeatures.getCollectionIds().getSize());
        assertEquals(srcCopyElement.getCollectionId(), readFeatures.getCollectionIds().getFirst());
        assertEquals(srcCopyElement.getMapId(), readFeatures.getCatalogId());
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
        assertEquals(Naksha.ADMIN_CATALOG_ID, write.getMapId());
        assertEquals(Naksha.CATALOGS_COL_ID, write.getCollectionId());
        assertEquals(WriteOp.CREATE, write.getOp());
        assertNotNull(write.getFeature());
        assertEquals(targetCopyElement.getMapId(), write.getFeature().getId());
    }

    private void assertCreateCollectionWrite(Write write) {
        assertEquals(targetCopyElement.getMapId(), write.getMapId());
        assertEquals(Naksha.COLLECTIONS_COL_ID, write.getCollectionId());
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


    private CopyService buildCopyService(StorageProvider storageProvider) {
        return new CopyService(
                featuresWriteExecutor,
                storageProvider,
                sessionOptions
        );
    }

    private void assertValidFeaturesPassedToFeaturesWriteExecutor(
            IStorage targetStorage, List<NakshaFeature> featuresToWrite
    ) throws FeaturesWriteExecutorException {
        List<NakshaFeature> featuresPassedToFeaturesWriteExecutor = captureFeaturesWriteExecutorsFeatureTupleArg(targetStorage);
        assertTrue(
                featuresToWrite.containsAll(featuresPassedToFeaturesWriteExecutor) &&
                        featuresPassedToFeaturesWriteExecutor.containsAll(featuresToWrite),
                "Features passed to FeaturesWriteExecutor should be the same as read from the source"
        );
    }

    private void validFeaturesWriteExecutorReturningGivenInfo(
            IStorage targetStorage, FeaturesWriteExecutorInfo info
    ) throws FeaturesWriteExecutorException {
        when(
                featuresWriteExecutor.write(eq(targetStorage), eq(targetCopyElement), any(), eq(sessionOptions))
        ).thenReturn(info);
    }

    private void throwingFeaturesWriteExecutor(IStorage targetStorage) throws FeaturesWriteExecutorException {
        when(featuresWriteExecutor.write(eq(targetStorage), eq(targetCopyElement), any(), eq(sessionOptions)))
                .thenThrow(new FeaturesWriteExecutorException(""));

    }
}