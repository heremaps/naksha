import com.here.naksha.lib.jbon.Jb
import com.here.naksha.lib.plv8.JvmPlv8Env
import org.junit.jupiter.api.*
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager

/**
 * Start and shutdown a test container.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
open class Plv8TestContainer {
    val env = Jb.env

    companion object {
        private val logger = LoggerFactory.getLogger(Plv8TestContainer::class.java)
        private lateinit var postgreSQLContainer: PostgreSQLContainer<*>
        private var existingUrl: String? = null
        lateinit var url: String

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

            JvmPlv8Env.initialize()
            val env = JvmPlv8Env.get()
            val conn = DriverManager.getConnection(url)
            // TODO: Parse the url to extract the schema!
            env.install(conn, 0, "test_schema", "test_storage")
            env.startSession(
                    conn,
                    "plv8_test",
                    env.randomString(),
                    "plv8_test_app",
                    "plv8_test_user"
            )
            conn.commit()
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            JvmPlv8Env.get().endSession()
            if (existingUrl == null) {
                // Add a breakpoint here, when you want to query the database after the test.
                val port = postgreSQLContainer.getMappedPort(5432)
                logger.info("Database listening on port {}", port)
                postgreSQLContainer.stop()
            }
        }
    }
}