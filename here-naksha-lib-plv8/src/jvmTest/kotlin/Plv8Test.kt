import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.util.regex.Matcher

class Plv8Test {
    companion object {
        private lateinit var postgreSQLContainer: PostgreSQLContainer<*>

        lateinit var url: String

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            val image = DockerImageName.parse("greenoag/postgres-plv8-postgis:15.2-3.1.5-3.3").asCompatibleSubstituteFor("postgres")
            postgreSQLContainer = PostgreSQLContainer(image)
                    .withDatabaseName("plv8-db")
                    .withUsername("user")
                    .withPassword("password")

            postgreSQLContainer.start()

            url = String.format(
                    "%s?&user=%s&password=%s",
                    postgreSQLContainer.getJdbcUrl(),
                    postgreSQLContainer.username,
                    postgreSQLContainer.password)


            initPlv8()
        }

        private fun initPlv8() {
            val conn: Connection = DriverManager.getConnection(url)

            val sb = StringBuilder()
            val nakshaPlv8File = this::class.java.getResourceAsStream("/plv8.sql")?.bufferedReader()?.readText()
            val lz4JsFile = this::class.java.getResourceAsStream("/lz4.js")?.bufferedReader()?.readText()
            val nakshaPlv8JsFile = this::class.java.getResourceAsStream("/here-naksha-lib-plv8.js")?.bufferedReader()?.readText()

            val script = nakshaPlv8File?.replace("\\$\\{plv8\\.js}".toRegex(), Matcher.quoteReplacement(nakshaPlv8JsFile))
            ?.replace("\\$\\{lz4\\.js}".toRegex(), Matcher.quoteReplacement(lz4JsFile))

            sb.append(script)

            val st: Statement = conn.createStatement()
            st.execute(sb.toString())
            st.close()
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            postgreSQLContainer.stop()
        }
    }

    @Test
    fun testUsingPlv8() {
        val conn: Connection = DriverManager.getConnection(url)

        val sb = StringBuilder()

        sb.append("select naksha_start_session(), 'hello world'")

        val st: Statement = conn.createStatement()
        val rs: ResultSet = st.executeQuery(sb.toString())
        rs.next()
        Assertions.assertEquals("hello world", rs.getString(2))
        rs.close()
        st.close()
    }
}