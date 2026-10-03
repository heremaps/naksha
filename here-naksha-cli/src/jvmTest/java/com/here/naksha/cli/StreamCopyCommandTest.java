package com.here.naksha.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StreamCopyCommandTest {
    @TempDir
    Path dir;

    private record Run(int exitCode, String out, String err) {}

    private static Run run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = new CommandLine(new NakshaCliCommand(), new CommandFactory());
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        int code = cmd.execute(args);
        return new Run(code, out.toString(), err.toString());
    }

    @Test
    void listPrintsBuiltInProviders() {
        Run r = run("stream-copy", "--list");
        assertEquals(0, r.exitCode());
        assertTrue(r.out().contains("null"), r.out());
        assertTrue(r.out().contains("random"), r.out());
    }

    @Test
    void copiesRandomIntoNull() throws Exception {
        Path cfg = dir.resolve("gen.json");
        Files.writeString(cfg, "{\"count\":200,\"statesPerFeature\":2,\"tileIds\":[\"120210233222\"]}");

        Run r = run("stream-copy", "--source", "random", "--sourceConfig", cfg.toString(), "--target", "null", "--chunkSize", "25");

        assertEquals(0, r.exitCode(), r.out() + r.err());
        assertTrue(r.out().contains("Success! Copied 400 tuples."), r.out());
    }

    @Test
    void copiesIntoTwoTargets() {
        Run r = run("stream-copy", "--source", "random", "--target", "null", "--target", "null", "--headOnly");

        assertEquals(0, r.exitCode(), r.out() + r.err());
        assertTrue(r.out().contains("Success! Copied 1000 tuples."), r.out());
    }

    @Test
    void unknownProviderIsAUsageError() {
        Run r = run("stream-copy", "--source", "v9", "--target", "null");

        assertEquals(CommandLine.ExitCode.USAGE, r.exitCode());
        assertTrue(r.err().contains("Unknown stream provider 'v9', available: null, random"), r.err());
    }

    @Test
    void missingRecoveryFileIsAUsageError() {
        Run r = run("stream-copy", "--source", "random", "--target", "null", "--resume", dir.resolve("nope.json").toString());

        assertEquals(CommandLine.ExitCode.USAGE, r.exitCode());
        assertTrue(r.err().contains("Recovery file not found"), r.err());
    }

    @Test
    void targetConfigsMustMatchTargets() {
        Run r = run("stream-copy", "--source", "random", "--target", "null", "--target", "null", "--targetConfig", "a.json");

        assertEquals(CommandLine.ExitCode.USAGE, r.exitCode());
    }
}
