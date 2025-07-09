package com.here.naksha.cli.copy;

import com.here.naksha.cli.ProperMessageAndExitCodeTestCase;
import com.here.naksha.cli.TestCommandLine;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static com.here.naksha.cli.TestUtils.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopyCommandTest {
    @Test
    void shouldFailWithUnreadableFile(@TempDir Path dir) throws IOException {
        // Given
        TestCommandLine cmd = new TestCommandLine(new CopyCommand());

        Path unreadableFile = dir.resolve("bad");
        Files.writeString(unreadableFile, "{}");
        File file = unreadableFile.toFile();

        assertTrue(file.setReadable(false));

        String basePath = "unit_test_data/copy.CopyCommandTest/shouldFailWithUnreadableFile/";
        ProperMessageAndExitCodeTestCase testCase = new ProperMessageAndExitCodeTestCase(
                new String[]{
                    "--srcStorageConfig=" + unreadableFile.toAbsolutePath(),
                    "--targetStorageConfig=" + unreadableFile.toAbsolutePath()
                },
                INVALID_INPUT_EXIT_CODE,
                readLinesFromResource(basePath + "stdout.txt"),
                readLinesFromResource(basePath + "stderr.txt")
        );

        //When: command executed with given args
        TestCommandLine.CommandResult result = cmd.execute(testCase.args());

        // Then: Output and exit code are checked
        testCase.assertMatches(result);
    }

    @ParameterizedTest
    @MethodSource("properMessageAndExitCodeTestCases")
    void shouldGiveProperMessageAndExitCode(ProperMessageAndExitCodeTestCase testCase) {
        // Given
        TestCommandLine cmd = new TestCommandLine(new CopyCommand());

        // When: command executed with given args
        TestCommandLine.CommandResult result = cmd.execute(testCase.args());

        // Then: Output and exit code are checked
        testCase.assertMatches(result);
    }

    private static Stream<Named<ProperMessageAndExitCodeTestCase>> properMessageAndExitCodeTestCases() throws IOException {
        String pathToNoExistingFile = "/no/exists/file.json";
        String basePath = "unit_test_data/copy.CopyCommandTest/shouldGiveProperMessageAndExitCode/";
        return Stream.of(
                Named.named(
                        "No existing storage configs",
                        new ProperMessageAndExitCodeTestCase(
                                new String[]{
                                        "--srcStorageConfig=" + pathToNoExistingFile,
                                        "--targetStorageConfig=" + pathToNoExistingFile
                                },
                                INVALID_INPUT_EXIT_CODE,
                                readLinesFromResource(basePath + "no_existing_storage_configs/stdout.txt"),
                                readLinesFromResource(basePath + "no_existing_storage_configs/stderr.txt")
                        )
                ),
                Named.named(
                        "No existing src storage config",
                        new ProperMessageAndExitCodeTestCase(
                                new String[]{
                                        "--srcStorageConfig=" + pathToNoExistingFile,
                                        "--targetStorageConfig=" + getAbsolutePathOfResource(
                                                basePath + "storage_configs/good"
                                        )
                                },
                                INVALID_INPUT_EXIT_CODE,
                                readLinesFromResource(basePath + "no_existing_src_storage_config/stdout.txt"),
                                readLinesFromResource(basePath + "no_existing_src_storage_config/stderr.txt")
                        )
                ),
                Named.named(
                        "No existing target storage config",
                        new ProperMessageAndExitCodeTestCase(
                                new String[]{
                                        "--targetStorageConfig=" + pathToNoExistingFile,
                                        "--srcStorageConfig=" + getAbsolutePathOfResource(
                                                basePath + "storage_configs/good"
                                        )
                                },
                                INVALID_INPUT_EXIT_CODE,
                                readLinesFromResource(basePath + "no_existing_target_storage_config/stdout.txt"),
                                readLinesFromResource(basePath + "no_existing_target_storage_config/stderr.txt")
                        )
                ),
                Named.named(
                        "Bad storage config format",
                        new ProperMessageAndExitCodeTestCase(
                                new String[]{
                                        "--targetStorageConfig=" + getAbsolutePathOfResource(
                                                basePath + "storage_configs/bad"
                                        ),
                                        "--srcStorageConfig=" + getAbsolutePathOfResource(
                                                basePath + "storage_configs/good"
                                        )
                                },
                                INVALID_INPUT_EXIT_CODE,
                                readLinesFromResource(basePath + "bad_storage_config_format/stdout.txt"),
                                readLinesFromResource(basePath + "bad_storage_config_format/stderr.txt")
                        )
                ),
                Named.named(
                        "Storage config is not a file",
                        new ProperMessageAndExitCodeTestCase(
                                new String[]{
                                        "--targetStorageConfig=" + getAbsolutePathOfResource(
                                                basePath + "storage_configs/"
                                        ),
                                        "--srcStorageConfig=" + getAbsolutePathOfResource(
                                                basePath + "storage_configs/good"
                                        )
                                },
                                INVALID_INPUT_EXIT_CODE,
                                readLinesFromResource(basePath + "storage_config_is_not_a_file/stdout.txt"),
                                readLinesFromResource(basePath + "storage_config_is_not_a_file/stderr.txt")
                        )
                ),
                Named.named(
                        "Correct storage configs",
                        new ProperMessageAndExitCodeTestCase(
                                new String[]{
                                        "--targetStorageConfig=" + getAbsolutePathOfResource(
                                                basePath + "storage_configs/good"
                                        ),
                                        "--srcStorageConfig=" + getAbsolutePathOfResource(
                                                basePath + "storage_configs/good"
                                        )
                                },
                                SUCCESS_EXIT_CODE,
                                readLinesFromResource(basePath + "correct_storage_configs/stdout.txt"),
                                readLinesFromResource(basePath + "correct_storage_configs/stderr.txt")
                        )
                ),
                Named.named(
                        "Correct storage configs with all optional parameters",
                        new ProperMessageAndExitCodeTestCase(
                                new String[]{
                                        "--targetStorageConfig=" + getAbsolutePathOfResource(
                                                basePath + "storage_configs/good"
                                        ),
                                        "--srcStorageConfig=" + getAbsolutePathOfResource(
                                                basePath + "storage_configs/good"
                                        ),
                                        "--srcMapId=1",
                                        "--srcCollectionId=1",
                                        "--targetMapId=1",
                                        "--targetCollectionId=1"
                                },
                                SUCCESS_EXIT_CODE,
                                readLinesFromResource(basePath + "correct_storage_configs/stdout.txt"),
                                readLinesFromResource(basePath + "correct_storage_configs/stderr.txt")
                        )
                )
        );
    }
}