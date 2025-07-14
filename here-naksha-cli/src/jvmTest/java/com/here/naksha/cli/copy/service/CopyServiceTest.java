package com.here.naksha.cli.copy.service;

import naksha.model.ISession;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ErrorResponse;
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
        TestCopyService copyService = new TestCopyService(
                src,
                target
        );
        copyService.mockSrcStorageResponseWithSuccess(featureList);

        // When
        copyService.copy();

        // Then
        copyService.assertReadRequests();
        copyService.assertWriteRequests(featureList);
    }

    @ParameterizedTest
    @MethodSource("shouldCopyFailTestCases")
    void shouldCopyFail(String errorMessage, Function<TestCopyService, ISession> sessionFunction) {
        // Given
        TestCopyElement copyElement = new TestCopyElement.Builder("colid")
                .setMapId("mapid")
                .build();
        TestCopyService copyService = new TestCopyService(
                copyElement,
                copyElement
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

    private static Stream<Arguments> shouldCopyFailTestCases() {
        return Stream.of(
                Arguments.of(
                        "Problem with reading from source!",
                        (Function<TestCopyService, ISession>) TestCopyService::getReadSession
                ),
                Arguments.of(
                        "Problem with writing to target!",
                        (Function<TestCopyService, ISession>) TestCopyService::getWriteSession
                )
        );
    }

    private static Stream<Arguments> shouldCopyTestCases() {
        List<TestCopyElement> copyElements = List.of(
                new TestCopyElement.Builder("colid")
                        .setMapId("mapid")
                        .build(),
                new TestCopyElement.Builder("colid")
                        .build()
        );
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

        return copyElements.stream()
                .flatMap(src ->
                        copyElements.stream()
                                .flatMap(target ->
                                        features.stream()
                                                .map(f ->
                                                        Arguments.of(src, target, f)
                                                )
                                )
                );
    }
}