package com.here.naksha.cli;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

public record ProperMessageAndExitCodeTestCase(
        String[] args,
        int expectedExitCode,
        List<String> expectedStdoutPatterns,
        List<String> expectedStderrPatterns
) {
    public void assertMatches(
            TestCommandLine.CommandResult result
    ) {
        assertEquals(expectedExitCode(), result.exitCode(), "Unexpected exit code");
        assertLinesMatch(expectedStdoutPatterns(), result.stdOut());
        assertLinesMatch(expectedStderrPatterns(), result.stdErr());
    }
}
