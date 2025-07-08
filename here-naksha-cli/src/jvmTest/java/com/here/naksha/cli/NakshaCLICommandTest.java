package com.here.naksha.cli;

import com.here.naksha.cli.copy.ShortErrorMessageHandler;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static com.here.naksha.cli.TestUtils.*;

class NakshaCLICommandTest {
    @ParameterizedTest
    @MethodSource("properMessageAndExitCodeTestCases")
    void shouldGiveProperMessageAndExitCode(ProperMessageAndExitCodeTestCase testCase) {
        // Given
        CommandLine cmd = new CommandLine(new NakshaCLICommand());
        cmd.setParameterExceptionHandler(new ShortErrorMessageHandler());
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        // When: command executed with given args
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
        String basePath = "unit_test_data/NakshaCLICommandTest/shouldGiveProperMessageAndExitCode/";
        return Stream.of(
                Named.named(
                        "Empty command",
                        new ProperMessageAndExitCodeTestCase(
                                new String[]{},
                                INVALID_INPUT_EXIT_CODE,
                                readLinesFromResource(basePath + "empty_command/stdout.txt"),
                                readLinesFromResource(basePath + "empty_command/stderr.txt")
                        )
                ),
                Named.named(
                        "Copy command without options",
                        new ProperMessageAndExitCodeTestCase(
                                new String[]{"copy"},
                                INVALID_INPUT_EXIT_CODE,
                                readLinesFromResource(basePath + "copy_command_without_options/stdout.txt"),
                                readLinesFromResource(basePath + "copy_command_without_options/stderr.txt")
                        )
                )
        );
    }

}