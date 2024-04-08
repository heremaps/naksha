import com.here.naksha.lib.plv8.JvmPlv8Env
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.sql.DriverManager
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Start and shutdown a test container.
 */

class Plv8TestContainer : BeforeAllCallback, ExtensionContext.Store.CloseableResource {

    companion object {
        private val logger = LoggerFactory.getLogger(Plv8TestContainer::class.java)
        private lateinit var postgreSQLContainer: GenericContainer<*>
        private var existingUrl: String? = null
        lateinit var url: String
        lateinit var schema: String
        private var initialized = false
    }

    private fun architecture(): String {
        val arch = System.getProperty("os.arch")
        return if (arch == "x86_64" || arch == "amd64") {
            "amd64"
        } else {
            "arm64"
        }
    }

    override fun beforeAll(context: ExtensionContext?) {
        if (initialized) return

        // Run docker locally with env parameter: POSTGRES_PASSWORD=password
        // NAKSHA_TEST_PSQL_DB_URL=jdbc:postgresql://localhost:5400/postgres?user=postgres&password=password&schema=test_schema
        existingUrl = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
        schema = "test_schema"
        if (existingUrl != null) {
            url = existingUrl!!
            // TODO: Parse the url to extract the schema!
        } else {
            val password = "password"
            postgreSQLContainer = GenericContainer("hcr.data.here.com/naksha-devops/naksha-postgres:${architecture()}-v16.2-r1")
                    .withExposedPorts(5432)
            postgreSQLContainer.addEnv("PGPASSWORD", password)
            postgreSQLContainer.setWaitStrategy(LogMessageWaitStrategy()
                    .withRegEx("Start postgres.*")
                    .withTimes(2)
                    .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS)))
            postgreSQLContainer.start()
            Thread.sleep(1000) // give it 1s more before connect

            val port = postgreSQLContainer.getMappedPort(5432)
            logger.info("Database listening on port {}", port)
            url = "jdbc:postgresql://localhost:$port/postgres?user=postgres&password=$password"
        }

        JvmPlv8Env.initialize()
        val env = JvmPlv8Env.get()
        val conn = DriverManager.getConnection(url)
        // TODO: Parse the url to extract the schema!
        env.install(conn, 0, schema, "test_storage")
        env.startSession(
                conn,
                schema,
                "plv8_test",
                env.randomString(),
                "plv8_test_app",
                "plv8_test_user"
        )
        conn.commit()
        initialized = true
    }

    override fun close() {
        JvmPlv8Env.get().endSession()
        if (existingUrl == null) {
            // Add a breakpoint here, when you want to query the database after the test.
            val port = postgreSQLContainer.getMappedPort(5432)
            logger.info("Database listening on port {}", port)
            postgreSQLContainer.stop()
        }
    }
}
