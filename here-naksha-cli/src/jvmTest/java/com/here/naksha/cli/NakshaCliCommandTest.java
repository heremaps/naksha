package com.here.naksha.cli;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.here.naksha.cli.TestUtils.INVALID_INPUT_EXIT_CODE;

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

    private static Stream<Named<ProperMessageAndExitCodeTestCase>> properMessageAndExitCodeTestCases() {
        return Stream.of(
                Named.named(
                        "Empty command",
                        new ProperMessageAndExitCodeTestCase(
                                new String[]{},
                                INVALID_INPUT_EXIT_CODE,
                                "",
                                """
                                        Missing required subcommand
                                        Usage: naksha-cli [-hV] [COMMAND]
                                        Try 'naksha-cli --help' for more information.
                                        """
                        )
                ),
                Named.named(
                        "Copy command without options",
                        new ProperMessageAndExitCodeTestCase(
                                new String[]{"copy"},
                                INVALID_INPUT_EXIT_CODE,
                                "",
                                """
                                        Missing required options: '--srcStorageConfig=<srcStorageConfig>', '--targetStorageConfig=<targetStorageConfig>'
                                        Usage: naksha-cli copy [-hV] --srcStorageConfig=<srcStorageConfig>
                                                               [--srcMapId=<srcMapId>]
                                                               [--srcCollectionId=<srcCollectionId>]
                                                               --targetStorageConfig=<targetStorageConfig>
                                                               [--targetMapId=<targetMapId>]
                                                               [--targetCollectionId=<targetCollectionId>]
                                        Try 'naksha-cli copy --help' for more information.
                                        """
                        )
                )
        );
    }

}