package com.here.naksha.cli;

import naksha.model.NakshaContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.here.naksha.cli.TestUtils.INVALID_INPUT_EXIT_CODE;

class NakshaCliCommandTest {
    @BeforeEach
    void beforeEach() {
        NakshaContext.currentContext().withAppId("testAppId");
    }

    @ParameterizedTest
    @MethodSource("properMessageAndExitCodeTestCases")
    void shouldGiveProperMessageAndExitCode(CliTestCase testCase) {
        // Given
        TestCommandLine cmd = new TestCommandLine(new NakshaCliCommand());

        // When: command executed with given args
        TestCommandLine.CommandResult result = cmd.execute(testCase.args());

        // Then: Output and exit code are checked
        testCase.assertMatches(result);
    }

    private static Stream<Named<CliTestCase>> properMessageAndExitCodeTestCases() {
        return Stream.of(
                Named.named(
                        "Empty command",
                        new CliTestCase(
                                new String[]{},
                                INVALID_INPUT_EXIT_CODE,
                                "",
                                """
                                        Missing required subcommand
                                        Try 'naksha-cli --help' for more information.
                                        """
                        )
                ),
                Named.named(
                        "Copy command without options",
                        new CliTestCase(
                                new String[]{"copy"},
                                INVALID_INPUT_EXIT_CODE,
                                "",
                                """
                                        Missing required options: '--srcStorageConfig=<srcStorageConfig>', '--targetStorageConfig=<targetStorageConfig>'
                                        Try 'naksha-cli copy --help' for more information.
                                        """
                        )
                )
        );
    }

}