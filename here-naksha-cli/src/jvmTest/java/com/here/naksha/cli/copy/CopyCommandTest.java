package com.here.naksha.cli.copy;

import com.here.naksha.cli.ProperMessageAndExitCodeTestCase;
import com.here.naksha.cli.TestCommandLine;
import com.here.naksha.cli.copy.service.*;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaStorage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;

import static com.here.naksha.cli.TestUtils.EXECUTION_EXCEPTION_EXIT_CODE;
import static com.here.naksha.cli.TestUtils.SUCCESS_EXIT_CODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CopyCommandTest {
    CopyServiceFactory copyServiceFactory = mock();
    NakshaStorageProvider nakshaStorageProvider = mock();
    StorageProvider storageProvider = mock();
    SessionOptions sessionOptions = mock();
    CopyCommand copyCommand = new CopyCommand(
            copyServiceFactory,
            nakshaStorageProvider,
            storageProvider,
            sessionOptions
    );
    TestCommandLine commandLine = new TestCommandLine(copyCommand);

    private void assertCopyElement(
            CopyElement expected,
            CopyElement actual
    ) {
        assertEquals(expected.getMapId(), actual.getMapId());
        assertEquals(expected.getCollectionId(), actual.getCollectionId());
        assertEquals(expected.getNakshaStorage(), actual.getNakshaStorage());
    }

    private void assertCopyServiceParams(
            CopyService copyService,
            CopyElement srcCopyElement,
            CopyElement targetCopyElement
    ) throws CopyServiceException {
        ArgumentCaptor<CopyElement> actualSrcCopyElements = ArgumentCaptor.forClass(CopyElement.class);
        ArgumentCaptor<CopyElement> actualTargetCopyElements = ArgumentCaptor.forClass(CopyElement.class);
        verify(copyService, only()).copy(actualSrcCopyElements.capture(), actualTargetCopyElements.capture());
        assertCopyElement(
                srcCopyElement,
                actualSrcCopyElements.getValue()
        );
        assertCopyElement(
                targetCopyElement,
                actualTargetCopyElements.getValue()
        );
    }

    @Test
    void shouldCopy() throws CopyServiceException, NakshaStorageProviderException {
        // Given
        File srcStorageConfig = new File("src");
        NakshaStorage srcNakshaStorage = mock();
        when(nakshaStorageProvider.get(srcStorageConfig)).thenReturn(srcNakshaStorage);

        // And
        File targetStorageConfig = new File("target");
        NakshaStorage targetNakshaStorage = mock();
        when(nakshaStorageProvider.get(targetStorageConfig)).thenReturn(targetNakshaStorage);

        // And
        CopyElement srcCopyElement = new CopyElement.Builder(srcNakshaStorage, "srcc")
                .setMapId("srcm")
                .build();

        // And
        CopyElement targetCopyElement = new CopyElement.Builder(targetNakshaStorage, "targetc")
                .setMapId("targetm")
                .build();

        // And
        ProperMessageAndExitCodeTestCase testCase = new ProperMessageAndExitCodeTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(srcStorageConfig.getPath()),
                        "--targetStorageConfig=%s".formatted(targetStorageConfig.getPath()),
                        "--srcMapId=%s".formatted(srcCopyElement.getMapId()),
                        "--srcCollectionId=%s".formatted(srcCopyElement.getCollectionId()),
                        "--targetMapId=%s".formatted(targetCopyElement.getMapId()),
                        "--targetCollectionId=%s".formatted(targetCopyElement.getCollectionId())
                },
                SUCCESS_EXIT_CODE,
                "",
                ""
        );

        // And
        CopyService copyService = mock();
        when(copyServiceFactory.create(eq(storageProvider), eq(sessionOptions))).thenReturn(copyService);

        // When: command executed with given args
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        assertCopyServiceParams(copyService, srcCopyElement, targetCopyElement);

        // And
        testCase.assertMatches(result);
    }

    @Test
    void shouldFailWithBadSrcNakshaStorage() throws NakshaStorageProviderException {
        // Given
        File file = new File("src");
        String exceptionMessage = "Test message";
        when(nakshaStorageProvider.get(eq(file))).thenThrow(new NakshaStorageProviderException(exceptionMessage, file));

        // And
        ProperMessageAndExitCodeTestCase testCase = new ProperMessageAndExitCodeTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(file.getPath()),
                        "--targetStorageConfig=target"
                },
                EXECUTION_EXCEPTION_EXIT_CODE,
                "",
                exceptionMessage + " file: " + file.getPath()
        );

        // When
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        testCase.assertMatches(result);
    }

    @Test
    void shouldFailWithBadTargetNakshaStorage() throws NakshaStorageProviderException {
        // Given
        File file = new File("target");
        String exceptionMessage = "Test message";
        when(nakshaStorageProvider.get(eq(file))).thenThrow(new NakshaStorageProviderException(exceptionMessage, file));

        // And
        ProperMessageAndExitCodeTestCase testCase = new ProperMessageAndExitCodeTestCase(
                new String[]{
                        "--srcStorageConfig=src",
                        "--targetStorageConfig=%s".formatted(file.getPath())
                },
                EXECUTION_EXCEPTION_EXIT_CODE,
                "",
                exceptionMessage + " file: " + file.getPath()
        );

        // When
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        testCase.assertMatches(result);
    }

    @Test
    void shouldFailWhenCopyFail() throws CopyServiceException {
        // Given
        CopyService copyService = mock();
        String exceptionMessage = "Test message";
        doThrow(new CopyServiceException(exceptionMessage)).when(copyService).copy(any(), any());

        // And
        when(copyServiceFactory.create(eq(storageProvider), eq(sessionOptions))).thenReturn(copyService);

        // And
        ProperMessageAndExitCodeTestCase testCase = new ProperMessageAndExitCodeTestCase(
                new String[]{
                        "--srcStorageConfig=src",
                        "--targetStorageConfig=target"
                },
                EXECUTION_EXCEPTION_EXIT_CODE,
                "",
                exceptionMessage
        );

        // When
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        testCase.assertMatches(result);
    }
}