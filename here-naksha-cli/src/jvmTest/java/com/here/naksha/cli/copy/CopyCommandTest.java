package com.here.naksha.cli.copy;

import com.here.naksha.cli.TestCommandLine;
import com.here.naksha.cli.copy.service.CopyElement;
import com.here.naksha.cli.copy.service.CopyServiceException;
import naksha.model.objects.NakshaStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.here.naksha.cli.TestUtils.EXECUTION_EXCEPTION_EXIT_CODE;
import static com.here.naksha.cli.TestUtils.SUCCESS_EXIT_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CopyCommandTest {
    private void assertCopyElement(
            CopyElement ce,
            String mapId,
            String collectionId,
            NakshaStorage nakshaStorage
    ) {
        assertThat(ce)
                .matches(e -> Objects.equals(e.getMapId(), mapId))
                .matches(e -> Objects.equals(e.getCollectionId(), collectionId))
                .matches(e -> Objects.equals(e.getNakshaStorage(), nakshaStorage));
    }

    private void assertConsoleOut(
            TestCommandLine.CommandResult result,
            int exitCode,
            String stdOut,
            String stdErr
    ) {
        assertThat(result.exitCode())
                .isEqualTo(exitCode);
        assertThat(result.stdErr())
                .isEqualTo(stdErr);
        assertThat(result.stdOut())
                .isEqualTo(stdOut);
    }

    @Test
    void shouldCopy() throws CopyServiceException, NakshaStorageProviderException {
        // Given
        TestCopyCommand testCopyCommand = new TestCopyCommand();
        TestCommandLine cmd = new TestCommandLine(testCopyCommand.getCopyCommand());
        String srcMapId = "srcm1";
        String targetMapId = "tm1";
        String srcCollectionId = "srcc1";
        String targetCollectionId = "tc1";
        File srcStorageConfig = new File("src");
        File targetStorageConfig = new File("target");

        String[] args = {
                "--srcStorageConfig=%s".formatted(srcStorageConfig.getPath()),
                "--targetStorageConfig=%s".formatted(targetStorageConfig.getPath()),
                "--srcMapId=%s".formatted(srcMapId),
                "--srcCollectionId=%s".formatted(srcCollectionId),
                "--targetMapId=%s".formatted(targetMapId),
                "--targetCollectionId=%s".formatted(targetCollectionId)
        };

        NakshaStorage srcNakshaStorage = mock();
        when(testCopyCommand.getNakshaStorageProvider().get(srcStorageConfig)).thenReturn(srcNakshaStorage);
        NakshaStorage targetNakshaStorage = mock();
        when(testCopyCommand.getNakshaStorageProvider().get(targetStorageConfig)).thenReturn(targetNakshaStorage);

        // When: command executed with given args
        TestCommandLine.CommandResult result = cmd.execute(args);

        // Then: Copy service is used with good params
        ArgumentCaptor<CopyElement> srcCopyElement = ArgumentCaptor.forClass(CopyElement.class);
        ArgumentCaptor<CopyElement> targetCopyElement = ArgumentCaptor.forClass(CopyElement.class);
        verify(testCopyCommand.getCopyServiceFactory(), only()).create(
                eq(testCopyCommand.getNakshaProvider()),
                eq(testCopyCommand.getSessionOptions())
        );
        verify(testCopyCommand.getCopyService(), only()).copy(srcCopyElement.capture(), targetCopyElement.capture());
        assertCopyElement(
                srcCopyElement.getValue(),
                srcMapId,
                srcCollectionId,
                srcNakshaStorage
        );
        assertCopyElement(
                targetCopyElement.getValue(),
                targetMapId,
                targetCollectionId,
                targetNakshaStorage
        );
        assertConsoleOut(
                result,
                SUCCESS_EXIT_CODE,
                "",
                ""
        );
    }

    @ParameterizedTest
    @MethodSource("shouldGiveProperMessageAndExitCodeTestCases")
    void shouldGiveProperMessageAndExitCode(
            Consumer<TestCopyCommand> c,
            int expectedExitCode,
            String expectedStdErr,
            String expectedStdOut
    ) {
        // Given
        TestCopyCommand testCopyCommand = new TestCopyCommand();
        TestCommandLine cmd = new TestCommandLine(testCopyCommand.getCopyCommand());
        String[] args = {
                "--srcStorageConfig=src",
                "--targetStorageConfig=target",
        };
        c.accept(testCopyCommand);

        // When
        TestCommandLine.CommandResult result = cmd.execute(args);

        // Then
        assertConsoleOut(
                result,
                expectedExitCode,
                expectedStdOut,
                expectedStdErr
        );
    }

    private static Stream<Arguments> shouldGiveProperMessageAndExitCodeTestCases() {
        return Stream.of(
                Arguments.of(
                        (Consumer<TestCopyCommand>) (cc) -> {
                            File file = new File("src");
                            Exception ex = new NakshaStorageProviderException("Test", file);
                            try {
                                when(cc.getNakshaStorageProvider().get(eq(file))).thenThrow(ex);
                            } catch (NakshaStorageProviderException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        EXECUTION_EXCEPTION_EXIT_CODE,
                        "Test file: src\n", // std err
                        "" // std out
                ),
                Arguments.of(
                        (Consumer<TestCopyCommand>) (cc) -> {
                            File file = new File("target");
                            Exception ex = new NakshaStorageProviderException("Test", file);
                            try {
                                when(cc.getNakshaStorageProvider().get(eq(file))).thenThrow(ex);
                            } catch (NakshaStorageProviderException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        EXECUTION_EXCEPTION_EXIT_CODE,
                        "Test file: target\n", // std err
                        "" // std out
                ),
                Arguments.of(
                        (Consumer<TestCopyCommand>) (cc) -> {
                            Exception ex = new CopyServiceException("Test");
                            try {
                                doThrow(ex).when(cc.getCopyService()).copy(any(), any());
                            } catch (CopyServiceException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        EXECUTION_EXCEPTION_EXIT_CODE,
                        "Test\n", // std err
                        "" // std out
                ),
                Arguments.of(
                        (Consumer<TestCopyCommand>) (cc) -> {
                        },
                        SUCCESS_EXIT_CODE,
                        "", // std err
                        "" // std out
                )
        );
    }
}