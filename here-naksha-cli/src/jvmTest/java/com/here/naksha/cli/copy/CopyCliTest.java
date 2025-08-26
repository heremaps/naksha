package com.here.naksha.cli.copy;

import com.here.naksha.cli.CliTestCase;
import com.here.naksha.cli.TestCommandLine;
import com.here.naksha.cli.copy.service.*;
import com.here.naksha.cli.parsers.JsonFileParser;
import com.here.naksha.cli.results.CommandFailure;
import com.here.naksha.cli.results.CommandSuccess;
import naksha.model.objects.NakshaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static com.here.naksha.cli.TestUtils.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    void shouldCopyWithoutAutoCreateTarget() {
        // Given
        Path srcStorageConfig = Path.of(validStorageConfigPath);

        // And
        Path targetStorageConfig = Path.of(validStorageConfigPath);

        // And
        CopyElement srcCopyElement = new CopyElement.Builder(loadStorage(srcStorageConfig))
                .setMapId("srcm")
                .setCollectionId("srcc")
                .build();

        // And
        CopyElement targetCopyElement = new CopyElement.Builder(loadStorage(targetStorageConfig))
                .setMapId("targetm")
                .setCollectionId("targetc")
                .build();

        // And: copy service returns success result
        int numberOfCopiedElement = 10;
        CopyService copyService = copyServiceReturningSuccessResult(numberOfCopiedElement);

        // And: factory returns the copy service
        when(copyServiceFactory.create(eq(storageProvider), any())).thenReturn(copyService);

        // And
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(srcStorageConfig),
                        "--targetStorageConfig=%s".formatted(targetStorageConfig),
                        "--srcMapId=%s".formatted(srcCopyElement.getMapId()),
                        "--srcCollectionId=%s".formatted(srcCopyElement.getCollectionId()),
                        "--targetMapId=%s".formatted(targetCopyElement.getMapId()),
                        "--targetCollectionId=%s".formatted(targetCopyElement.getCollectionId())
                },
                SUCCESS_EXIT_CODE,
                "Success! Copied %d features from %s to %s.".formatted(
                        numberOfCopiedElement,
                        srcCopyElement,
                        targetCopyElement
                ),
                ""
        );

        // When: command executed with given args
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        assertCopyServiceParams(copyService, srcCopyElement, targetCopyElement, false);

        // And
        testCase.assertMatches(result);
    }

    @Test
    void shouldCopyWithAutoCreateTarget() {
        // Given
        Path srcStorageConfig = Path.of(validStorageConfigPath);

        // And
        Path targetStorageConfig = Path.of(validStorageConfigPath);

        // And
        CopyElement srcCopyElement = new CopyElement.Builder(loadStorage(srcStorageConfig))
                .setMapId("srcm")
                .setCollectionId("srcc")
                .build();

        // And
        CopyElement targetCopyElement = new CopyElement.Builder(loadStorage(targetStorageConfig))
                .setMapId("targetm")
                .setCollectionId("targetc")
                .build();

        // And: copy service returns success result
        int numberOfCopiedElement = 10;
        CopyService copyService = copyServiceReturningSuccessResult(numberOfCopiedElement);

        // And: factory returns the copy service
        when(copyServiceFactory.create(eq(storageProvider), any())).thenReturn(copyService);

        // And
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(srcStorageConfig),
                        "--targetStorageConfig=%s".formatted(targetStorageConfig),
                        "--srcMapId=%s".formatted(srcCopyElement.getMapId()),
                        "--srcCollectionId=%s".formatted(srcCopyElement.getCollectionId()),
                        "--targetMapId=%s".formatted(targetCopyElement.getMapId()),
                        "--targetCollectionId=%s".formatted(targetCopyElement.getCollectionId()),
                        "--autoCreateTarget"
                },
                SUCCESS_EXIT_CODE,
                "Success! Copied %d features from %s to %s.".formatted(
                        numberOfCopiedElement,
                        srcCopyElement,
                        targetCopyElement
                ),
                ""
        );

        // When: command executed with given args
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        assertCopyServiceParams(copyService, srcCopyElement, targetCopyElement, true);

        // And
        testCase.assertMatches(result);
    }

    @Test
    void shouldFailWithInvalidSrcNakshaStorage() {
        // Given
        Path filePath = Path.of(invalidStorageConfigPath);

        // And
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(filePath),
                        "--targetStorageConfig=target"
                },
                EXECUTION_EXCEPTION_EXIT_CODE,
                "",
                nakshaStorageParserErrorMessage.formatted(filePath)
        );

        // When
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        testCase.assertMatches(result);
    }

    @Test
    void shouldFailWithInvalidTargetNakshaStorage() {
        // Given
        Path targetFilePath = Path.of(invalidStorageConfigPath);

        // And: valid src
        Path srcFilePath = Path.of(validStorageConfigPath);

        // And
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(srcFilePath),
                        "--targetStorageConfig=%s".formatted(targetFilePath)
                },
                EXECUTION_EXCEPTION_EXIT_CODE,
                "",
                nakshaStorageParserErrorMessage.formatted(targetFilePath)
        );

        // When
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        testCase.assertMatches(result);
    }

    @Test
    void shouldFailWhenCopyFail() {
        // Given: copy service returns error result
        String exceptionMessage = "Test message";
        CopyService copyService = copyServiceReturningErrorResult(exceptionMessage);

        // And: factory returns the copy service
        when(copyServiceFactory.create(eq(storageProvider), any())).thenReturn(copyService);

        // And
        Path validStorageConfig = Path.of(validStorageConfigPath);

        // And
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(validStorageConfig),
                        "--targetStorageConfig=%s".formatted(validStorageConfig)
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

    private CopyService copyServiceReturningErrorResult(String exceptionMessage) {
        CopyService copyService = mock();
        when(copyService.copy(any(), any(), anyBoolean())).thenReturn(
                new CommandFailure<>(new CopyServiceException(exceptionMessage))
        );
        return copyService;
    }

    private NakshaStorage loadStorage(Path storageConfig) {
        JsonFileParser jsonFileParser = new JsonFileParser();
        return assertDoesNotThrow(() -> jsonFileParser.parse(storageConfig, NakshaStorage.class));
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
            CopyElement targetCopyElement,
            boolean autoCreateTarget
    ) {
        ArgumentCaptor<CopyElement> actualSrcCopyElements = ArgumentCaptor.forClass(CopyElement.class);
        ArgumentCaptor<CopyElement> actualTargetCopyElements = ArgumentCaptor.forClass(CopyElement.class);
        verify(copyService, only()).copy(actualSrcCopyElements.capture(), actualTargetCopyElements.capture(), eq(autoCreateTarget));
        assertCopyElement(
                srcCopyElement,
                actualSrcCopyElements.getValue()
        );
        assertCopyElement(
                targetCopyElement,
                actualTargetCopyElements.getValue()
        );
    }

    private CopyService copyServiceReturningSuccessResult(int numberOfCopiedElements) {
        CopyService copyService = mock();
        when(copyService.copy(any(), any(), anyBoolean())).thenReturn(
                new CommandSuccess<>(
                        new CopyServiceSuccessResultPayload(numberOfCopiedElements)
                )
        );
        return copyService;
    }
}