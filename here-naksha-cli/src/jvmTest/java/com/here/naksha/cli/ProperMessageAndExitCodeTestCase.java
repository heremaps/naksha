package com.here.naksha.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

public record ProperMessageAndExitCodeTestCase(
        String[] args,
        int expectedExitCode,
        String expectedStdout,
        String expectedStderr
) {
    public void assertMatches(
            TestCommandLine.CommandResult result
    ) {
        assertEquals(expectedExitCode(), result.exitCode(), "Unexpected exit code");
        assertLinesMatch(expectedStdout.lines().toList(), result.stdOut().lines().toList());
        assertLinesMatch(expectedStderr.lines().toList(), result.stdErr().lines().toList());
    }
}
