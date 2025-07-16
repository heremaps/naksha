package com.here.naksha.cli.copy.service;

import naksha.model.ISession;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.ErrorResponse;
import naksha.model.request.SuccessResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CopyServiceTest {
    @ParameterizedTest
    @MethodSource("shouldCopyTestCases")
    void shouldCopy(
            TestCopyElement src,
            TestCopyElement target,
            List<NakshaFeature> featureList
    ) throws Exception {
        // Given
        CopyServiceTestContext copyService = new CopyServiceTestContext(
                src,
                target
        );
        copyService.mockSrcStorageResponseWithSuccess(featureList);
        copyService.mockResponse(copyService.getWriteSession(), new SuccessResponse());

        // When
        copyService.copy();

        // Then
        copyService.assertReadRequests();
        copyService.assertWriteRequests(featureList);
    }

    private TestCopyElement getCopyElement() {
        return new TestCopyElement.Builder("colid")
                .setMapId("mapid").build();
    }

    @ParameterizedTest
    @MethodSource("shouldCopyFailDueToStorageErrorTestCases")
    void shouldCopyFailDueToStorageError(String errorMessage, Function<CopyServiceTestContext, NakshaStorage> nakshaStorageFunction) {
        // Given
        CopyServiceTestContext copyService = new CopyServiceTestContext(
                getCopyElement(),
                getCopyElement()
        );
        NakshaException ex = mock();
        copyService.mockSrcStorageResponseWithSuccess(Collections.emptyList());
        when(copyService.getNakshaProvider().useStorage(nakshaStorageFunction.apply(copyService))).thenThrow(ex);

        // When & Then
        assertThatThrownBy(copyService::copy)
                .isInstanceOf(CopyServiceException.class)
                .hasMessage(errorMessage)
                .hasRootCauseInstanceOf(NakshaException.class);
    }

    private static Stream<Arguments> shouldCopyFailDueToStorageErrorTestCases() {
        return Stream.of(
                Arguments.of(
                        "Can not get source storage!",
                        (Function<CopyServiceTestContext, NakshaStorage>) cs -> cs.getSrcTestCopyElement().getStorage()
                ),
                Arguments.of(
                        "Can not get target storage!",
                        (Function<CopyServiceTestContext, NakshaStorage>) cs -> cs.getTargetTestCopyElement().getStorage()
                )
        );
    }

    @ParameterizedTest
    @MethodSource("shouldCopyFailDueToSessionErrorTestCases")
    void shouldCopyFailDueToSessionError(String errorMessage, Function<CopyServiceTestContext, ISession> sessionFunction) {
        // Given
        CopyServiceTestContext copyService = new CopyServiceTestContext(
                getCopyElement(),
                getCopyElement()
        );
        ErrorResponse response = mock();
        when(response.getError()).thenReturn(new NakshaError());
        copyService.mockSrcStorageResponseWithSuccess(Collections.emptyList());
        copyService.mockResponse(sessionFunction.apply(copyService), response);

        // When & Then
        assertThatThrownBy(copyService::copy)
                .isInstanceOf(CopyServiceException.class)
                .hasMessage(errorMessage)
                .hasRootCauseInstanceOf(NakshaException.class);
    }

    private static Stream<Arguments> shouldCopyFailDueToSessionErrorTestCases() {
        return Stream.of(
                Arguments.of(
                        "Problem with reading from source!",
                        (Function<CopyServiceTestContext, ISession>) CopyServiceTestContext::getReadSession
                ),
                Arguments.of(
                        "Problem with writing to target!",
                        (Function<CopyServiceTestContext, ISession>) CopyServiceTestContext::getWriteSession
                )
        );
    }

    private static Stream<Arguments> shouldCopyTestCases() {
        List<TestCopyElement.Builder> copyElementBuilders = List.of(
                new TestCopyElement.Builder("colid")
                        .setMapId("mapid"),
                new TestCopyElement.Builder("colid")
        );
        List<TestCopyElement> srcCopyElements = copyElementBuilders.stream()
                .map(TestCopyElement.Builder::build)
                .toList();
        List<TestCopyElement> targetCopyElements = copyElementBuilders.stream()
                .map(TestCopyElement.Builder::build)
                .toList();
        List<List<NakshaFeature>> features = List.of(
                List.of(
                        new NakshaFeature("1"),
                        new NakshaFeature("2")
                ),
                List.of(
                        new NakshaFeature("1")
                ),
                Collections.emptyList()
        );

        return srcCopyElements.stream()
                .flatMap(src ->
                        targetCopyElements.stream()
                                .flatMap(target ->
                                        features.stream()
                                                .map(f ->
                                                        Arguments.of(src, target, f)
                                                )
                                )
                );
    }
}