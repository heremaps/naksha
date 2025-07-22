package com.here.naksha.cli.copy;

import com.here.naksha.cli.CliTestCase;
import com.here.naksha.cli.TestCommandLine;
import com.here.naksha.cli.copy.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;

import static com.here.naksha.cli.TestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CopyCliTest {
    @Mock
    private CopyServiceFactory copyServiceFactory;
    @Mock
    private StorageProvider storageProvider;
    private TestCommandLine commandLine;

    @BeforeEach
    void beforeEach() {
        CopyCommand copyCommand = new CopyCommand(
                copyServiceFactory,
                storageProvider
        );
        commandLine = new TestCommandLine(copyCommand);
    }

    @Test
    void shouldCopy() throws CopyServiceException, NakshaStorageParserException {
        // Given
        File srcStorageConfig = new File(validStorageConfigPath);

        // And
        File targetStorageConfig = new File(validStorageConfigPath);

        // And
        NakshaStorageParser nakshaStorageParser = new NakshaStorageParser();

        // And
        CopyElement srcCopyElement = new CopyElement.Builder(nakshaStorageParser.get(srcStorageConfig), "srcc")
                .setMapId("srcm")
                .build();

        // And
        CopyElement targetCopyElement = new CopyElement.Builder(nakshaStorageParser.get(targetStorageConfig), "targetc")
                .setMapId("targetm")
                .build();

        // And
        CliTestCase testCase = new CliTestCase(
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
        when(copyServiceFactory.create(eq(storageProvider), any())).thenReturn(copyService);

        // When: command executed with given args
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        assertCopyServiceParams(copyService, srcCopyElement, targetCopyElement);

        // And
        testCase.assertMatches(result);
    }

    @Test
    void shouldFailWithBadSrcNakshaStorage() {
        // Given
        File file = new File(invalidStorageConfigPath);
        String exceptionMessage = "Test message";

        // And
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(file.getPath()),
                        "--targetStorageConfig=target"
                },
                EXECUTION_EXCEPTION_EXIT_CODE,
                "",
                nakshaStorageParserErrorMessage.formatted(file.getPath())
        );

        // When
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        testCase.assertMatches(result);
    }

    @Test
    void shouldFailWithBadTargetNakshaStorage() {
        // Given
        File targetFile = new File(invalidStorageConfigPath);

        // And: valid src
        File srcFile = new File(validStorageConfigPath);

        // And
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(srcFile.getPath()),
                        "--targetStorageConfig=%s".formatted(targetFile.getPath())
                },
                EXECUTION_EXCEPTION_EXIT_CODE,
                "",
                nakshaStorageParserErrorMessage.formatted(targetFile.getPath())
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
        when(copyServiceFactory.create(eq(storageProvider), any())).thenReturn(copyService);

        // And
        File validStorageConfig = new File(validStorageConfigPath);

        // And
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(validStorageConfig.getPath()),
                        "--targetStorageConfig=%s".formatted(validStorageConfig.getPath())
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

    private final String storageConfigsPath = "storage_configs/";
    private final String validStorageConfigPath = getAbsolutePathOfResource(storageConfigsPath + "valid");
    private final String invalidStorageConfigPath = getAbsolutePathOfResource(storageConfigsPath + "invalid");
    private final String nakshaStorageParserErrorMessage = """
            Problem with json parsing! file: %s
            Unexpected character ('c' (code 99)): was expecting double-quote to start field name
             at [Source: (String)"{
              "id": "test_generating_storage",
              className": "naksha.cli.GeneratingStorage",
              "properties": {
                "count": 1000,
                "templateFile": "/some/dir/sample_topology.json",
                "tileIds": ["23621667", "23621664"]
            
            }"; line: 3, column: 4]
            """;

    private CopyCliTest() throws IOException {
    }

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
}