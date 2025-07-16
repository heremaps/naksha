package com.here.naksha.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public record ProperMessageAndExitCodeTestCase(
        String[] args,
        int expectedExitCode,
        String expectedStdoutPatterns,
        String expectedStderrPatterns
) {
    public void assertMatches(
            TestCommandLine.CommandResult result
    ) {
        assertEquals(expectedExitCode(), result.exitCode(), "Unexpected exit code");
        assertThat(result.stdOut().lines().toList())
                .containsExactlyElementsOf(expectedStdoutPatterns.lines().toList());
        assertThat(result.stdErr().lines().toList())
                .containsExactlyElementsOf(expectedStderrPatterns.lines().toList());
    }
}
