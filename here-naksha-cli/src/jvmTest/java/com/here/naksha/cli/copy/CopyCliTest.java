package com.here.naksha.cli.copy;

import com.here.naksha.cli.CliTestCase;
import com.here.naksha.cli.TestCommandLine;
import com.here.naksha.cli.copy.service.*;
import com.here.naksha.cli.copy.service.factory.CopyServiceFactory;
import com.here.naksha.cli.copy.service.factory.CopyServiceFactory.FeaturesWriteExecutors;
import com.here.naksha.cli.parsers.JsonFileParser;
import com.here.naksha.cli.results.CommandFailure;
import com.here.naksha.cli.results.CommandSuccess;
import naksha.model.objects.NakshaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    private final String storageConfigsPath = "storage_configs/";
    private final String validStorageConfigPath = getAbsolutePathOfResource(storageConfigsPath + "valid");
    private final String invalidStorageConfigPath = getAbsolutePathOfResource(storageConfigsPath + "invalid");
    private final Path pathToValidStorageConfig = Path.of(validStorageConfigPath);
    private final Path pathToInvalidStorageConfig = Path.of(invalidStorageConfigPath);
    private final CopyElement validSrcCopyElement = new CopyElement.Builder(loadStorage(pathToValidStorageConfig))
            .setMapId("srcm")
            .setCollectionId("srcc")
            .build();
    private final CopyElement validTargetCopyElement = new CopyElement.Builder(loadStorage(pathToValidStorageConfig))
            .setMapId("targetm")
            .setCollectionId("targetc")
            .build();

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
        // Given: copy service returns success result
        int numberOfCopiedElement = 10;
        CopyService copyService = copyServiceReturningSuccessResult(numberOfCopiedElement);

        // And: factory returns the copy service
        copyServiceFactoryReturningGivenCopyService(
                copyService,
                FeaturesWriteExecutors.PARALLEL,
                null,
                null,
                null
        );

        // And
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(pathToValidStorageConfig),
                        "--targetStorageConfig=%s".formatted(pathToValidStorageConfig),
                        "--srcMapId=%s".formatted(validSrcCopyElement.getMapId()),
                        "--srcCollectionId=%s".formatted(validSrcCopyElement.getCollectionId()),
                        "--targetMapId=%s".formatted(validTargetCopyElement.getMapId()),
                        "--targetCollectionId=%s".formatted(validTargetCopyElement.getCollectionId())
                },
                SUCCESS_EXIT_CODE,
                "Success! Copied %d features from %s to %s.".formatted(
                        numberOfCopiedElement,
                        validSrcCopyElement,
                        validTargetCopyElement
                ),
                ""
        );

        // When: command executed with given args
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        assertCopyServiceParams(copyService, validSrcCopyElement, validTargetCopyElement, false);

        // And
        testCase.assertMatches(result);
    }

    @Test
    void shouldCopyWithAutoCreateTarget() {
        // Given: copy service returns success result
        int numberOfCopiedElement = 10;
        CopyService copyService = copyServiceReturningSuccessResult(numberOfCopiedElement);

        // And: factory returns the copy service
        copyServiceFactoryReturningGivenCopyService(
                copyService,
                FeaturesWriteExecutors.PARALLEL,
                null,
                null,
                null
        );

        // And
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(pathToValidStorageConfig),
                        "--targetStorageConfig=%s".formatted(pathToValidStorageConfig),
                        "--srcMapId=%s".formatted(validSrcCopyElement.getMapId()),
                        "--srcCollectionId=%s".formatted(validSrcCopyElement.getCollectionId()),
                        "--targetMapId=%s".formatted(validTargetCopyElement.getMapId()),
                        "--targetCollectionId=%s".formatted(validTargetCopyElement.getCollectionId()),
                        "--autoCreateTarget"
                },
                SUCCESS_EXIT_CODE,
                "Success! Copied %d features from %s to %s.".formatted(
                        numberOfCopiedElement,
                        validSrcCopyElement,
                        validTargetCopyElement
                ),
                ""
        );

        // When: command executed with given args
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        assertCopyServiceParams(copyService, validSrcCopyElement, validTargetCopyElement, true);

        // And
        testCase.assertMatches(result);
    }

    @ParameterizedTest
    @EnumSource(FeaturesWriteExecutors.class)
    void shouldCopyWithGivenFeaturesWriteExecutor(FeaturesWriteExecutors featuresWriteExecutorsBuilder) {
        // Given: copy service returns success result
        int numberOfCopiedElement = 10;
        CopyService copyService = copyServiceReturningSuccessResult(numberOfCopiedElement);

        // And: factory returns the copy service
        copyServiceFactoryReturningGivenCopyService(
                copyService,
                featuresWriteExecutorsBuilder,
                null,
                null,
                null
        );

        // And
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(pathToValidStorageConfig),
                        "--targetStorageConfig=%s".formatted(pathToValidStorageConfig),
                        "--srcMapId=%s".formatted(validSrcCopyElement.getMapId()),
                        "--srcCollectionId=%s".formatted(validSrcCopyElement.getCollectionId()),
                        "--targetMapId=%s".formatted(validTargetCopyElement.getMapId()),
                        "--targetCollectionId=%s".formatted(validTargetCopyElement.getCollectionId()),
                        "--featuresWriteExecutor=%s".formatted(featuresWriteExecutorsBuilder.name())
                },
                SUCCESS_EXIT_CODE,
                "Success! Copied %d features from %s to %s.".formatted(
                        numberOfCopiedElement,
                        validSrcCopyElement,
                        validTargetCopyElement
                ),
                ""
        );

        // When: command executed with given args
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        assertCopyServiceParams(copyService, validSrcCopyElement, validTargetCopyElement, false);

        // And
        testCase.assertMatches(result);
    }

    @Test
    void shouldCopyWithGivenParams() {
        // Given: copy service returns success result
        int numberOfCopiedElement = 10;
        CopyService copyService = copyServiceReturningSuccessResult(numberOfCopiedElement);

        // And: factory returns the copy service
        int threads = 10;
        int queueMulti = 6;
        int maxBatchSize = 1024;
        copyServiceFactoryReturningGivenCopyService(
                copyService,
                FeaturesWriteExecutors.PARALLEL,
                threads,
                queueMulti,
                maxBatchSize
        );

        // And
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(pathToValidStorageConfig),
                        "--targetStorageConfig=%s".formatted(pathToValidStorageConfig),
                        "--srcMapId=%s".formatted(validSrcCopyElement.getMapId()),
                        "--srcCollectionId=%s".formatted(validSrcCopyElement.getCollectionId()),
                        "--targetMapId=%s".formatted(validTargetCopyElement.getMapId()),
                        "--targetCollectionId=%s".formatted(validTargetCopyElement.getCollectionId()),
                        "--threads=%s".formatted(threads),
                        "--queueMulti=%s".formatted(queueMulti),
                        "--maxBatchSize=%s".formatted(maxBatchSize)
                },
                SUCCESS_EXIT_CODE,
                "Success! Copied %d features from %s to %s.".formatted(
                        numberOfCopiedElement,
                        validSrcCopyElement,
                        validTargetCopyElement
                ),
                ""
        );

        // When: command executed with given args
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        assertCopyServiceParams(copyService, validSrcCopyElement, validTargetCopyElement, false);

        // And
        testCase.assertMatches(result);
    }

    @Test
    void shouldFailWithInvalidSrcNakshaStorage() {
        // Given
        Path filePath = pathToInvalidStorageConfig;

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
        Path targetFilePath = pathToInvalidStorageConfig;

        // And: valid src
        Path srcFilePath = pathToValidStorageConfig;

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
        copyServiceFactoryReturningGivenCopyService(
                copyService,
                FeaturesWriteExecutors.PARALLEL,
                null,
                null,
                null
        );

        // And
        Path validStorageConfig = pathToValidStorageConfig;

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

    @ParameterizedTest
    @ValueSource(ints = {-10, -1, 0})
    void shouldFailWhenNonPositiveValueForThreadsOption(int nonPositiveThreads) {
        // Given
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(pathToValidStorageConfig),
                        "--targetStorageConfig=%s".formatted(pathToValidStorageConfig),
                        "--threads=%s".formatted(nonPositiveThreads)
                },
                INVALID_INPUT_EXIT_CODE,
                "",
                """
                        Invalid value '%s' for option '--threads': value should be a positive integer
                        Try 'copy --help' for more information.
                        """.formatted(nonPositiveThreads)
        );

        // When
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        testCase.assertMatches(result);
    }

    @ParameterizedTest
    @ValueSource(ints = {-10, -1, 0})
    void shouldFailWhenNonPositiveValueForMaxBatchSizeOption(int nonPositiveMaxBatchSize) {
        // Given
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(pathToValidStorageConfig),
                        "--targetStorageConfig=%s".formatted(pathToValidStorageConfig),
                        "--maxBatchSize=%s".formatted(nonPositiveMaxBatchSize)
                },
                INVALID_INPUT_EXIT_CODE,
                "",
                """
                        Invalid value '%s' for option '--maxBatchSize': value should be a positive integer
                        Try 'copy --help' for more information.
                        """.formatted(nonPositiveMaxBatchSize)
        );

        // When
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        testCase.assertMatches(result);
    }

    @ParameterizedTest
    @ValueSource(ints = {-10, -1, 0})
    void shouldFailWhenNonPositiveValueForQueueMultiOption(int nonPositiveQueueMulti) {
        // Given
        CliTestCase testCase = new CliTestCase(
                new String[]{
                        "--srcStorageConfig=%s".formatted(pathToValidStorageConfig),
                        "--targetStorageConfig=%s".formatted(pathToValidStorageConfig),
                        "--queueMulti=%s".formatted(nonPositiveQueueMulti)
                },
                INVALID_INPUT_EXIT_CODE,
                "",
                """
                        Invalid value '%s' for option '--queueMulti': value should be a positive integer
                        Try 'copy --help' for more information.
                        """.formatted(nonPositiveQueueMulti)
        );

        // When
        TestCommandLine.CommandResult result = commandLine.execute(testCase.args());

        // Then
        testCase.assertMatches(result);
    }

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

    private void copyServiceFactoryReturningGivenCopyService(
            CopyService copyService,
            FeaturesWriteExecutors featuresWriteExecutor,
            Integer threads,
            Integer queueMulti,
            Integer maxBatchSize
    ) {
        when(copyServiceFactory.create(
                eq(storageProvider),
                any(),
                eq(featuresWriteExecutor),
                eq(threads),
                eq(queueMulti),
                eq(maxBatchSize)
        )).thenReturn(copyService);
    }
}