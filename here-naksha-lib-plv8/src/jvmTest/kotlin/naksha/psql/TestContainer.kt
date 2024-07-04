import naksha.model.NakshaContext
import naksha.psql.*
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.postgresql.jdbc.PgConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Start and shutdown a test container.
 */
class TestContainer : BeforeAllCallback, ExtensionContext.Store.CloseableResource {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(TestContainer::class.java)
        private val _storage = AtomicReference<PsqlStorage?>()
        private var _session: PsqlSession? = null

        /**
         * The default schema to be used. Is overridden when an
         */
        val DEFAULT_SCHEMA = "unit-tests-schema"

        /**
         * If a docker container is started, the reference to it.
         */
        var postgreSQLContainer: GenericContainer<*>? = null

        /**
         * The database URL, auto-generated from the local docker, except overridden via `NAKSHA_TEST_PSQL_DB_URL` environment variable.
         *
         * Example:
         * `NAKSHA_TEST_PSQL_DB_URL=jdbc:postgresql://localhost:5400/postgres?user=postgres&password=password&schema=test_schema`
         */
        lateinit var url: String

        /**
         * The actual schema to be used for testing. Either [DEFAULT_SCHEMA] or the overridden one provided via `NAKSHA_TEST_PSQL_DB_URL`
         * environment variable.
         */
        lateinit var schema: String

        /**
         * If the test container is initialized.
         */
        val initialized = AtomicBoolean(false)

        /**
         * The context to be use for testing.
         */
        val context = NakshaContext.newInstance("psql_test_app_id", "psql_author", su = true)

        /**
         * The storage to use for testing.
         */
        val storage: PsqlStorage
            get() {
                var s = _storage.get()
                if (s == null) {
                    s = PsqlStorage("test", PsqlCluster(PsqlInstance.get(url)), PgSessionOptions("psql_test", schema))
                    if (!_storage.compareAndSet(null, s)) {
                        s = _storage.get()
                        check(s != null)
                    }
                }
                return s
            }

        /**
         * The PostgresQL admin session (backed by a JDBC connection).
         */
        val adminSession: PsqlSession
            get() {
                var s = _session
                if (s == null) {
                    s = storage.openSession(context, storage.options.copy(readOnly = false, useMaster = true))
                    _session = s
                }
                return s
            }

        /**
         * The underlying JDBC connection of the [adminSession].
         */
        val adminConnection: PgConnection
            get() = adminSession.jdbcConnection
    }

    private fun architecture(): String {
        val os = System.getProperty("os.name")
        if (os == "Mac OS X") {
            return "arm64"
        }

        val arch = System.getProperty("os.arch")
        return if (arch == "x86_64" || arch == "amd64") {
            "amd64"
        } else {
            "arm64"
        }
    }

    override fun beforeAll(context: ExtensionContext?) {
        if (initialized.compareAndSet(false, true)) {
            // Run docker locally with env parameter: POSTGRES_PASSWORD=password
            // NAKSHA_TEST_PSQL_DB_URL=jdbc:postgresql://localhost:5432/postgres?user=postgres&password=password&schema=test_schema
            val existingUrl = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
            if (existingUrl != null) {
                url = existingUrl
                val params = url.substring(url.indexOf("?"), url.length)
                    .split("&")
                    .map { it.split("=") }
                    .groupBy({ it[0] }, { it[1] })
                schema = params["schema"]?.get(0) ?: DEFAULT_SCHEMA
            } else {
                val password = "password"
                val container = GenericContainer("hcr.data.here.com/naksha/postgres:${architecture()}-latest")
                    .withExposedPorts(5432)
                container.addEnv("PGPASSWORD", password)
                container.setWaitStrategy(
                    LogMessageWaitStrategy()
                        .withRegEx("Start postgres.*")
                        .withTimes(2)
                        .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS))
                )
                container.start()
                Thread.sleep(1000) // give it 1s more before connect

                val port = container.getMappedPort(5432)
                logger.info("Database listening on port {}", port)
                url = "jdbc:postgresql://localhost:$port/postgres?user=postgres&password=$password&schema="
                schema = DEFAULT_SCHEMA
                postgreSQLContainer = container
            }
        }
    }

    override fun close() {
        val session = _session
        if (session != null) {
            _session = null
            try {
                session.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val container = postgreSQLContainer
        if (container != null) {
            // Add a breakpoint here, when you want to query the database after the test.
            postgreSQLContainer = null
            val port = container.getMappedPort(5432)
            logger.info("Close database listening on port {}", port)
            container.stop()
        }
    }
}
