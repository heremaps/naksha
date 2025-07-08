package com.here.naksha.cli.copy;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.here.naksha.cli.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class CopyCommandTest {
    @Test
    void shouldFailWithUnreadableFile(@TempDir Path dir) throws IOException {
        // Given
        CommandLine cmd = new CommandLine(new CopyCommand());
        cmd.setParameterExceptionHandler(new ShortErrorMessageHandler());
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        Path unreadableFile = dir.resolve("bad");
        Files.writeString(unreadableFile, "{}");
        File file = unreadableFile.toFile();

        if(file.setReadable(false)) {
            String[] args = {
                    "--srcStorageConfig=" + unreadableFile.toAbsolutePath(),
                    "--targetStorageConfig=" + unreadableFile.toAbsolutePath()
            };

            //When: command executed with given args
            int exitCode = cmd.execute(args);

            List<String> stdoutLines = Arrays.asList(out.toString().split("\\R"));
            List<String> stderrLines = Arrays.asList(err.toString().split("\\R"));
            String basePath = "unit_test_data/copy.CopyCommandTest/shouldFailWithUnreadableFile/";
            List<String> expectedStdoutPatterns = readLinesFromResource(basePath + "stdout.txt");
            List<String> expectedStderrPatterns = readLinesFromResource(basePath + "stderr.txt");

            // Then: Output and exit code are checked
            assertEquals(INVALID_INPUT_EXIT_CODE, exitCode, "Unexpected exit code");
            if (!expectedStdoutPatterns.isEmpty()) {
                assertLinesMatch(expectedStdoutPatterns, stdoutLines);
            }

            if (!expectedStderrPatterns.isEmpty()) {
                assertLinesMatch(expectedStderrPatterns, stderrLines);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("properMessageAndExitCodeTestCases")
    void shouldGiveProperMessageAndExitCode(ProperMessageAndExitCodeTestCase testCase) {
        // Given
        CommandLine cmd = new CommandLine(new CopyCommand());
        cmd.setParameterExceptionHandler(new ShortErrorMessageHandler());
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        //When: command executed with given args
        int exitCode = cmd.execute(testCase.args);

        List<String> stdoutLines = Arrays.asList(out.toString().split("\\R"));
        List<String> stderrLines = Arrays.asList(err.toString().split("\\R"));

        // Then: Output and exit code are checked
        assertEquals(testCase.expectedExitCode, exitCode, "Unexpected exit code");

        if (!testCase.expectedStdoutPatterns.isEmpty()) {
            assertLinesMatch(testCase.expectedStdoutPatterns, stdoutLines);
        }

        if (!testCase.expectedStderrPatterns.isEmpty()) {
            assertLinesMatch(testCase.expectedStderrPatterns, stderrLines);
        }
    }


    private record ProperMessageAndExitCodeTestCase(
            String[] args,
            int expectedExitCode,
            List<String> expectedStdoutPatterns,
            List<String> expectedStderrPatterns
    ) {
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