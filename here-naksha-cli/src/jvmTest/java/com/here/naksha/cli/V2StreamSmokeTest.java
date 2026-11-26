package com.here.naksha.cli;

import com.here.naksha.cli.stream.CliContext;
import com.here.naksha.cli.stream.StorageProviders;
import naksha.base.Platform;
import naksha.model.IStorage;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.objects.NakshaStorage;
import naksha.psql.PgConfig;
import naksha.psql.PgInstanceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end: sync a v2 collection into the null storage through the CLI commands.
 * The v2 tables (head, _hst, _del) are created and filled with plain SQL, the way the v2 hub stores them:
 * <ul>
 *   <li>txn 1: create f0..f14, txn 2: create f15..f29, txn 3: update f0..f4 (old versions move to _hst)</li>
 *   <li>txn 4: create f30..f32, txn 5: delete f30..f32 (create versions and delete rows in _hst, delete rows in _del)</li>
 * </ul>
 */
class V2StreamSmokeTest {
    private static final String COLLECTION = "data";
    private static final int CREATED = 30;
    private static final int UPDATED = 5;
    private static final int DELETED = 3;
    private static final int HEAD = CREATED;
    private static final int ALL = HEAD + UPDATED + DELETED + DELETED;
    private static final long LEGACY_DATE = 20240101L * 100_000_000_000L;
    private static PgInstanceConfig master;

    @TempDir
    Path home;

    private String schema;
    private long nextI;

    private record Run(int exitCode, String out, String err) {}

    @BeforeAll
    static void startDatabase() {
        NakshaContext.currentContext().withAppId("v2-smoke");
        NakshaStorage config = NakshaStorage.fromJSON("{\"id\":\"" + Platform.getTestStorageId() + "\",\"className\":\"naksha.psql.PsqlTestStorage\"}");
        IStorage storage = Naksha.useStorage(config);
        master = Platform.javaProxy(storage.getConfig().platformObject(), PgConfig.class).getMaster();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://" + master.getHost() + ":" + master.getPort() + "/" + master.getDb() + "?sslmode=disable",
                master.getUser(), master.getPassword());
    }

    @BeforeEach
    void seed() throws SQLException {
        schema = "v2smoke_" + UUID.randomUUID().toString().replace("-", "");
        nextI = 1;
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA " + schema);
            for (String suffix : new String[]{"", "_hst", "_del"}) {
                st.execute("CREATE TABLE " + schema + "." + COLLECTION + suffix + " (jsondata jsonb, geo geometry, i int8)");
            }
            long[] firstI = new long[CREATED + DELETED];
            for (int f = 0; f < CREATED; f++) firstI[f] = insert(c, "", f, nextI++, f < 15 ? 1 : 2, "CREATE");
            for (int f = 0; f < UPDATED; f++) {
                insert(c, "_hst", f, firstI[f], 1, "CREATE");
                update(c, f, nextI++);
            }
            for (int f = CREATED; f < CREATED + DELETED; f++) {
                insert(c, "_hst", f, nextI++, 4, "CREATE");
            }
            for (int f = CREATED; f < CREATED + DELETED; f++) {
                long i = nextI++;
                insert(c, "_hst", f, i, 5, "DELETE");
                insert(c, "_del", f, i, 5, "DELETE");
            }
        }
    }

    @AfterEach
    void dropSchema() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private static String jsondata(int feature, long i, long txn, String action) {
        return "{\"id\":\"f" + feature + "\",\"type\":\"Feature\",\"properties\":{\"name\":\"" + action + "\",\"@ns:com:here:xyz\":{"
                + "\"uuid\":\"" + COLLECTION + ":" + i + "\",\"txn\":" + (LEGACY_DATE + txn) + ",\"action\":\"" + action + "\"}}}";
    }

    private long insert(Connection c, String suffix, int feature, long i, long txn, String action) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + schema + "." + COLLECTION + suffix
                + " (jsondata, geo, i) VALUES (?::jsonb, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)")) {
            ps.setString(1, jsondata(feature, i, txn, action));
            ps.setDouble(2, feature % 180);
            ps.setDouble(3, feature % 90);
            ps.setLong(4, i);
            ps.executeUpdate();
        }
        return i;
    }

    private void update(Connection c, int feature, long i) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE " + schema + "." + COLLECTION + " SET jsondata = ?::jsonb, i = ? WHERE jsondata->>'id' = ?")) {
            ps.setString(1, jsondata(feature, i, 3, "UPDATE"));
            ps.setLong(2, i);
            ps.setString(3, "f" + feature);
            assertEquals(1, ps.executeUpdate());
        }
    }

    /** Creates count new features in the head table in one new transaction. */
    private void insertNew(int count, long txn) throws SQLException {
        try (Connection c = connect()) {
            for (int n = 0; n < count; n++) insert(c, "", 1000 + n, nextI++, txn, "CREATE");
        }
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

    private void createStream(boolean history, boolean deleted, boolean keepTransactions) {
        String v2 = String.join("\n", master.getHost(), master.getDb(), String.valueOf(master.getPort()),
                master.getUser(), master.getPassword()) + "\n";
        assertOk(run(v2, "config", "create", "--name", "src", "--provider", "naksha-v2"));
        assertOk(run("", "config", "create", "--name", "null", "--provider", "null"));
        // source storage, catalog, collection, target storage, catalog, collection, another target, history, deleted, transactions, chunk size
        String answers = String.join("\n", "src", schema, COLLECTION, "null", "", "", "",
                history ? "" : "n", deleted ? "" : "n", keepTransactions ? "" : "n", "7") + "\n";
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
    void syncPerTransactionAndContinueWithNewData() throws SQLException {
        createStream(true, true, true);

        assertCopied(ALL, "sync", "smoke");
        assertCopied(0, "sync", "smoke", "--continue");

        insertNew(10, 6);
        assertCopied(10, "sync", "smoke", "--continue");
    }

    @Test
    void syncPerFeatureAndContinueWithNewData() throws SQLException {
        createStream(true, true, false);

        assertCopied(ALL, "sync", "smoke");
        assertCopied(0, "sync", "smoke", "--continue");

        insertNew(10, 6);
        assertCopied(10, "sync", "smoke", "--continue");
    }

    @ParameterizedTest(name = "history={0}, deleted={1}, transactions={2}")
    @CsvSource({
            "true,  true,  true,  " + ALL,
            "true,  false, true,  " + (HEAD + UPDATED + DELETED),
            "false, true,  true,  " + (HEAD + DELETED),
            "false, false, true,  " + HEAD,
            "true,  true,  false, " + ALL,
            "true,  false, false, " + (HEAD + UPDATED + DELETED),
            "false, true,  false, " + (HEAD + DELETED),
            "false, false, false, " + HEAD,
    })
    void syncHistoryAndDeletedOptions(boolean history, boolean deleted, boolean keepTransactions, int expected) {
        createStream(history, deleted, keepTransactions);

        assertCopied(expected, "sync", "smoke");
    }
}
