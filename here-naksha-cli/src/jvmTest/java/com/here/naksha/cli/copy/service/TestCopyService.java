package com.here.naksha.cli.copy.service;

import naksha.base.fn.Fn1;
import naksha.model.IReadSession;
import naksha.model.ISession;
import naksha.model.IWriteSession;
import naksha.model.SessionOptions;
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

class TestCopyService {
    private final SessionOptions sessionOptions = mock();
    private final CopyService copyService;
    private final TestCopyElement srcTestCopyElement;
    private final TestCopyElement targetTestCopyElement;
    private final SuccessResponse response = mock();
    private final IWriteSession writeSession = mock();
    private final IReadSession readSession = mock();

    public TestCopyService(
            TestCopyElement srcTestCopyElement,
            TestCopyElement targetTestCopyElement
    ) {
        this.srcTestCopyElement = srcTestCopyElement;
        this.targetTestCopyElement = targetTestCopyElement;
        copyService = new CopyService(
                srcTestCopyElement.getCopyElement(),
                targetTestCopyElement.getCopyElement(),
                sessionOptions
        );
        mockWriteSession();
        mockReadSession();
    }

    public void copy() throws CopyServiceException {
        copyService.copy();
    }

    public void mockSrcStorageResponseWithSuccess(
            List<NakshaFeature> featureList
    ) {
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
        when(srcTestCopyElement.getStorage().useReadSession(eq(sessionOptions), any()))
                .thenAnswer(invocation -> {
                    Fn1<Response, IReadSession> lambda = invocation.getArgument(1);
                    return lambda.call(readSession);
                });
    }

    private void mockWriteSession() {
        when(targetTestCopyElement.getStorage().useWriteSession(eq(sessionOptions), any()))
                .thenAnswer(invocation -> {
                    Fn1<Response, IWriteSession> lambda = invocation.getArgument(1);
                    return lambda.call(writeSession);
                });
    }

    public List<ReadFeatures> getReadSessionReadFeatures() {
        return captureRequests(readSession)
                .stream()
                .filter(r -> r instanceof ReadFeatures)
                .map(r -> (ReadFeatures) r)
                .toList();
    }

    public List<Write> getWriteSessionWrites() {
        return captureRequests(writeSession)
                .stream()
                .filter(r -> r instanceof WriteRequest)
                .flatMap(r -> ((WriteRequest) r).getWrites().stream())
                .toList();
    }

    private List<Request> captureRequests(ISession session) {
        ArgumentCaptor<Request> requestArgumentCaptor = ArgumentCaptor.forClass(Request.class);
        verify(session, atLeastOnce()).execute(requestArgumentCaptor.capture());
        return requestArgumentCaptor.getAllValues();
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
}
