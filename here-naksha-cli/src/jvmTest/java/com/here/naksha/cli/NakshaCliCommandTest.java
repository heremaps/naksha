package com.here.naksha.cli;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static com.here.naksha.cli.TestUtils.INVALID_INPUT_EXIT_CODE;
import static com.here.naksha.cli.TestUtils.readStringFromResource;

class NakshaCliCommandTest {
    @ParameterizedTest
    @MethodSource("properMessageAndExitCodeTestCases")
    void shouldGiveProperMessageAndExitCode(ProperMessageAndExitCodeTestCase testCase) {
        // Given
        TestCommandLine cmd = new TestCommandLine(new NakshaCliCommand());

        // When: command executed with given args
        TestCommandLine.CommandResult result = cmd.execute(testCase.args());

        // Then: Output and exit code are checked
        testCase.assertMatches(result);
    }

    private static Stream<Named<ProperMessageAndExitCodeTestCase>> properMessageAndExitCodeTestCases() throws IOException {
        String basePath = "unit_test_data/NakshaCLICommandTest/shouldGiveProperMessageAndExitCode/";
        return Stream.of(
                Named.named(
                        "Empty command",
                        new ProperMessageAndExitCodeTestCase(
                                new String[]{},
                                INVALID_INPUT_EXIT_CODE,
                                readStringFromResource(basePath + "empty_command/stdout.txt"),
                                readStringFromResource(basePath + "empty_command/stderr.txt")
                        )
                ),
                Named.named(
                        "Copy command without options",
                        new ProperMessageAndExitCodeTestCase(
                                new String[]{"copy"},
                                INVALID_INPUT_EXIT_CODE,
                                readStringFromResource(basePath + "copy_command_without_options/stdout.txt"),
                                readStringFromResource(basePath + "copy_command_without_options/stderr.txt")
                        )
                )
        );
    }

}