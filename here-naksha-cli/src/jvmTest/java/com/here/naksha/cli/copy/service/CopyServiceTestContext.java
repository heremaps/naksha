package com.here.naksha.cli.copy.service;

import naksha.base.fn.Fn1;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.*;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CopyServiceTestContext {
    private final CopyService copyService;
    private final TestCopyElement srcTestCopyElement;
    private final TestCopyElement targetTestCopyElement;
    private final IWriteSession writeSession = mock();
    private final IReadSession readSession = mock();
    private final SessionOptions sessionOptions = mock();
    private final NakshaProvider nakshaProvider = mock();
    private final IStorage srcStorage = mock();
    private final IStorage targetStorage = mock();

    public CopyServiceTestContext(
            TestCopyElement srcTestCopyElement,
            TestCopyElement targetTestCopyElement
    ) {
        this.srcTestCopyElement = srcTestCopyElement;
        this.targetTestCopyElement = targetTestCopyElement;
        copyService = new CopyService(
                nakshaProvider,
                sessionOptions
        );
        setupStorageMocks();
        setupSessionMocks();
    }

    private void setupStorageMocks() {
        when(nakshaProvider.useStorage(srcTestCopyElement.getStorage())).thenReturn(srcStorage);
        when(nakshaProvider.useStorage(targetTestCopyElement.getStorage())).thenReturn(targetStorage);
    }

    private void setupSessionMocks() {
        mockWriteSession();
        mockReadSession();
    }

    public void copy() throws CopyServiceException {
        copyService.copy(
                srcTestCopyElement.getCopyElement(),
                targetTestCopyElement.getCopyElement()
        );
    }

    public void mockSrcStorageResponseWithSuccess(
            List<NakshaFeature> featureList
    ) {
        SuccessResponse response = mock();
        when(
                response.getFeatures()
        ).thenReturn(
                NakshaFeatureList.fromList(featureList)
        );
        when(
                readSession.execute(any())
        ).thenReturn(response);
    }

    public void mockResponse(ISession session, Response response) {
        when(
                session.execute(any())
        ).thenReturn(response);
    }

    private void mockReadSession() {
        when(srcStorage.useReadSession(eq(sessionOptions), any()))
                .thenAnswer(invocation -> {
                    Fn1<Response, IReadSession> lambda = invocation.getArgument(1);
                    return lambda.call(readSession);
                });
    }

    private void mockWriteSession() {
        when(targetStorage.useWriteSession(eq(sessionOptions), any()))
                .thenAnswer(invocation -> {
                    Fn1<Response, IWriteSession> lambda = invocation.getArgument(1);
                    return lambda.call(writeSession);
                });
    }

    private <T extends Request> List<T> captureRequestsOfType(ISession session, Class<T> type) {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(session, atLeastOnce()).execute(captor.capture());
        return captor.getAllValues().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private List<ReadFeatures> getReadSessionReadFeatures() {
        return captureRequestsOfType(readSession, ReadFeatures.class);
    }

    private List<Write> getWriteSessionWrites() {
        return captureRequestsOfType(writeSession, WriteRequest.class).stream()
                .flatMap(wr -> wr.getWrites().stream())
                .toList();
    }

    public void assertReadRequests() {
        List<ReadFeatures> readFeaturesList = getReadSessionReadFeatures();
        assertThat(readFeaturesList).hasSize(1);
        ReadFeatures readFeatures = readFeaturesList.getFirst();
        assertThat(readFeatures.getCollectionIds())
                .hasSize(1);
        assertThat(readFeatures.getCollectionIds().getFirst())
                .isEqualTo(srcTestCopyElement.getCollectionId());
        assertThat(readFeatures.getMapId())
                .isEqualTo(srcTestCopyElement.getMapId());
    }

    public void assertWriteRequests(List<NakshaFeature> featureList) {
        List<Write> writes = getWriteSessionWrites();
        assertThat(writes)
                .allMatch(w -> w.getOp()
                        .equals(WriteOp.CREATE)
                )
                .allMatch(w -> w.getCollectionId()
                        .equals(targetTestCopyElement.getCollectionId())
                )
                .allMatch(w -> Objects.equals(w.getMapId(), targetTestCopyElement.getMapId()));

        Stream<NakshaFeature> actualNakshaFeature = writes.stream()
                .map(w -> w.getFeature());
        assertThat(actualNakshaFeature)
                .containsExactlyInAnyOrderElementsOf(featureList);
    }

    public IWriteSession getWriteSession() {
        return writeSession;
    }

    public IReadSession getReadSession() {
        return readSession;
    }

    public NakshaProvider getNakshaProvider() {
        return nakshaProvider;
    }

    public TestCopyElement getSrcTestCopyElement() {
        return srcTestCopyElement;
    }

    public TestCopyElement getTargetTestCopyElement() {
        return targetTestCopyElement;
    }
}
