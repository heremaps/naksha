import com.here.naksha.lib.jbon.JvmSession
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder
import org.postgresql.Driver
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager

/**
 * Start and shutdown a test container.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
open class JvmTestContainer {
    companion object {
        private val logger = LoggerFactory.getLogger(JvmTestContainer::class.java)
        private lateinit var postgreSQLContainer: PostgreSQLContainer<*>
        private var existingUrl: String? = null
        lateinit var url: String
        lateinit var session: JvmSession

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            existingUrl = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
            if (existingUrl != null) {
                url = existingUrl!!
            } else {
                val image = DockerImageName.parse("greenoag/postgres-plv8-postgis:15.2-3.1.5-3.3")
                        .asCompatibleSubstituteFor("postgres")
                postgreSQLContainer = PostgreSQLContainer(image)
                        .withDatabaseName("unimap")
                        .withUsername("postgres")
                        .withPassword("postgres")
                        .withExposedPorts(5432)

                postgreSQLContainer.start()
                val port = postgreSQLContainer.getMappedPort(5432)
                logger.info("Database listening on port {}", port)
                url = String.format(
                        "%s?&user=%s&password=%s",
                        postgreSQLContainer.getJdbcUrl(),
                        postgreSQLContainer.username,
                        postgreSQLContainer.password)
            }

            session = JvmSession.register()
            session.setConnection(DriverManager.getConnection(url))
            session.installModules()
            session.sql().execute("SELECT commonjs2_init()")
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            session.setConnection(null)
            if (existingUrl == null) {
                // Add a breakpoint here, when you want to query the database after the test.
                val port = postgreSQLContainer.getMappedPort(5432)
                logger.info("Database listening on port {}", port)
                postgreSQLContainer.stop()
            }
        }
    }
}