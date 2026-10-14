package com.here.naksha.cli;

import com.here.naksha.cli.stream.CliContext;
import com.here.naksha.cli.stream.StorageProviders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StreamCommandsTest {
    @TempDir
    Path home;

    private record Run(int exitCode, String out, String err) {}

    private Run run(String input, String... args) {
        CliContext ctx = new CliContext(StorageProviders.load(), home, new BufferedReader(new StringReader(input)), null);
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = new CommandLine(new NakshaCliCommand(), new CommandFactory(() -> ctx));
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        int code = cmd.execute(args);
        return new Run(code, out.toString(), err.toString());
    }

    private void createRandomToNull() {
        assertEquals(0, run("", "config", "create", "--name", "random", "--provider", "random").exitCode());
        assertEquals(0, run("", "config", "create", "--name", "null", "--provider", "null").exitCode());
        // source storage, catalog, collection, target storage, catalog, collection, another target, history, deleted, transactions, chunk size
        Run r = run("random\n\n\nnull\n\n\n\n\n\n\n250\n", "stream", "create", "--name", "r2n");
        assertEquals(0, r.exitCode(), r.out() + r.err());
    }

    @Test
    void providerListShowsBuiltInProviders() {
        Run r = run("", "provider", "list");
        assertEquals(0, r.exitCode());
        assertTrue(r.out().contains("null") && r.out().contains("random"), r.out());
    }

    @Test
    void configCreateAsksForNameAndProvider() {
        Run r = run("gen\nv9\nrandom\n", "config", "create");

        assertEquals(0, r.exitCode(), r.out() + r.err());
        assertTrue(r.out().contains("Please answer one of: null, random"), r.out());
        assertTrue(r.out().contains("Saved gen."), r.out());
        assertTrue(Files.isRegularFile(home.resolve("config/gen.json")));
        Run list = run("", "config", "list");
        assertTrue(list.out().matches("(?s).*gen\\s+random.*"), list.out());
    }

    @Test
    void configCreateRejectsUnknownProvider() {
        Run r = run("", "config", "create", "--name", "x", "--provider", "v9");

        assertEquals(CommandLine.ExitCode.USAGE, r.exitCode());
        assertTrue(r.err().contains("Unknown storage provider 'v9'"), r.err());
    }

    @Test
    void streamCreateNeedsConfiguredStorages() {
        Run r = run("", "stream", "create");

        assertEquals(CommandLine.ExitCode.USAGE, r.exitCode());
        assertTrue(r.err().contains("No storages configured"), r.err());
    }

    @Test
    void syncCopiesTheStreamAndContinueReadsOnlyNewData() throws Exception {
        createRandomToNull();

        Run first = run("", "sync", "r2n");
        assertEquals(0, first.exitCode(), first.out() + first.err());
        assertTrue(first.out().contains("Success! Copied 3000 tuples."), first.out());
        String file = Files.readString(home.resolve("stream/r2n.json"));
        assertTrue(file.contains("\"status\": \"Done\""), file);
        assertTrue(file.contains("\"randomCursor\": 3000"), file);

        Run again = run("", "sync", "r2n", "--continue");
        assertEquals(0, again.exitCode(), again.out() + again.err());
        assertTrue(again.out().contains("Success! Copied 0 tuples."), again.out());

        Run list = run("", "stream", "list");
        assertTrue(list.out().matches("(?s).*r2n\\s+random:default/default\\s+null:default/default\\s+Done\\s+3000.*"), list.out());
    }

    @Test
    void continueWithoutStateIsAUsageError() {
        createRandomToNull();

        Run r = run("", "sync", "r2n", "--continue");

        assertEquals(CommandLine.ExitCode.USAGE, r.exitCode());
        assertTrue(r.err().contains("has no saved state"), r.err());
    }

    @Test
    void unknownStreamIsAUsageError() {
        Run r = run("", "sync", "nope");

        assertEquals(CommandLine.ExitCode.USAGE, r.exitCode());
        assertTrue(r.err().contains("Unknown stream 'nope'"), r.err());
    }

    @Test
    void deleteRemovesTheConfiguration() {
        createRandomToNull();

        assertEquals(0, run("", "stream", "delete", "r2n").exitCode());
        assertEquals(0, run("", "config", "delete", "null").exitCode());
        assertFalse(Files.exists(home.resolve("config/null.json")));
        assertEquals(CommandLine.ExitCode.USAGE, run("", "config", "delete", "null").exitCode());
    }

    @Test
    void groupWithoutSubcommandIsAUsageError() {
        assertEquals(CommandLine.ExitCode.USAGE, run("", "config").exitCode());
    }
}
