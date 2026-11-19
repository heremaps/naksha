package com.here.naksha.cli;

import com.here.naksha.cli.stream.CliContext;
import com.here.naksha.cli.stream.StorageProviders;
import naksha.base.Platform;
import naksha.model.IStorage;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCatalog;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import naksha.psql.PgConfig;
import naksha.psql.PgInstanceConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static naksha.model.RandomFeatures.randomFeatures;
import static org.junit.jupiter.api.Assertions.*;

/** End-to-end: sync a v3 collection into the null storage through the CLI commands. */
class V3StreamSmokeTest {
    private static final int CREATED = 30;
    private static final int UPDATED = 5;
    private static final SessionOptions options = SessionOptions.from(NakshaContext.currentContext().withAppId("v3-smoke"));
    private static IStorage storage;
    private static PgInstanceConfig master;

    @TempDir
    Path home;

    private record Run(int exitCode, String out, String err) {}

    @BeforeAll
    static void startDatabase() {
        NakshaStorage config = NakshaStorage.fromJSON("{\"id\":\"" + Platform.getTestStorageId() + "\",\"className\":\"naksha.psql.PsqlTestStorage\"}");
        storage = Naksha.useStorage(config);
        master = Platform.javaProxy(storage.getConfig().platformObject(), PgConfig.class).getMaster();
    }

    private static void write(Write... writes) {
        WriteRequest request = new WriteRequest();
        for (Write w : writes) request.add(w);
        storage.useWriteSession(options, session -> {
            Response response = session.execute(request);
            assertInstanceOf(SuccessResponse.class, response, () -> Platform.toJSON(response));
            session.commit();
            return null;
        });
    }

    private static List<NakshaFeature> insert(String catalog, String collection, int count) {
        List<NakshaFeature> features = randomFeatures(count);
        write(features.stream().map(f -> new Write().createFeature(catalog, collection, f)).toArray(Write[]::new));
        return features;
    }

    /** A fresh catalog with one collection: CREATED features in one transaction, then UPDATED of them updated in another one. */
    private static String[] seed() {
        String catalog = "smoke-" + UUID.randomUUID();
        String collection = "data";
        write(new Write().createMap(new NakshaCatalog().withId(catalog)));
        write(new Write().createCollection(new NakshaCollection(collection, catalog)));
        List<NakshaFeature> features = insert(catalog, collection, CREATED);
        write(features.subList(0, UPDATED).stream().map(f -> {
            NakshaFeature update = new NakshaFeature();
            update.setId(f.getId());
            update.getProperties().put("alias", "updated");
            return new Write().updateFeature(catalog, collection, update, false);
        }).toArray(Write[]::new));
        return new String[]{catalog, collection};
    }

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

    private void createStream(String catalog, String collection, boolean keepTransactions) {
        String v3 = String.join("\n", Platform.getTestStorageId(), master.getHost(), String.valueOf(master.getPort()),
                master.getDb(), master.getUser(), master.getPassword()) + "\n";
        assertOk(run(v3, "config", "create", "--name", "src", "--provider", "v3"));
        assertOk(run("", "config", "create", "--name", "null", "--provider", "null"));
        // source storage, catalog, collection, target storage, catalog, collection, another target, history, deleted, transactions, chunk size
        String answers = String.join("\n", "src", catalog, collection, "null", "", "", "", "", "", keepTransactions ? "" : "n", "7") + "\n";
        assertOk(run(answers, "stream", "create", "--name", "smoke"));
    }

    private static void assertOk(Run r) {
        assertEquals(0, r.exitCode(), r.out() + r.err());
    }

    private void assertCopied(int tuples, String... args) {
        Run r = run("", args);
        assertOk(r);
        assertTrue(r.out().contains("Success! Copied " + tuples + " tuples."), r.out());
    }

    @Test
    void syncPerTransactionAndContinueWithNewData() {
        String[] c = seed();
        createStream(c[0], c[1], true);

        assertCopied(CREATED + UPDATED, "sync", "smoke");
        assertCopied(0, "sync", "smoke", "--continue");

        insert(c[0], c[1], 10);
        assertCopied(10, "sync", "smoke", "--continue");
    }

    @Test
    void syncPerFeature() {
        String[] c = seed();
        createStream(c[0], c[1], false);

        assertCopied(CREATED + UPDATED, "sync", "smoke");
    }
}
