import com.here.naksha.lib.plv8.Plv8Session
import org.junit.jupiter.api.*
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Plv8Test {
    companion object {
        private lateinit var postgreSQLContainer: PostgreSQLContainer<*>
        private val logger = LoggerFactory.getLogger(Plv8Test::class.java)

        lateinit var url: String

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
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

        @JvmStatic
        @AfterAll
        fun afterAll() {
            // Add a breakpoint here, when you want to query the database after the test.
            val port = postgreSQLContainer.getMappedPort(5432)
            logger.info("Database listening on port {}", port)
            postgreSQLContainer.stop()
        }
    }

    @Order(1)
    @Test
    fun initPlv8() {
        val session = Plv8Session.register()
        val conn: Connection = DriverManager.getConnection(url)
        session.setConnection(conn)
        session.installModules(mapOf("version" to "0"))
    }

    @Order(2)
    @Test
    fun queryVersion() {
        val session = Plv8Session.get()
        val map = session.map()
        val sql = session.sql()
        val result = sql.execute("select naksha_version() as version")
        val rs = result.rows()
        for (raw in rs) {
            val row = raw as HashMap<String, Any?>
            assertEquals(1, row.size)
            assertEquals(0L, row["version"])
            assertEquals(1, map.size(raw))
            assertEquals(0L, map.get(raw, "version"))
        }
    }

    @Order(99)
    @Test
    fun closeConnection() {
        val session = Plv8Session.get()
        session.setConnection(null)
    }
}