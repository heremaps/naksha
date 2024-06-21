package naksha.plv8

import Plv8TestContainer
import com.here.naksha.lib.plv8.naksha.plv8.JvmPlv8Storage
import naksha.model.IReadSession
import naksha.model.IStorage
import naksha.model.NakshaContext
import org.junit.Before
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith
import java.sql.Connection
import java.sql.DriverManager

/**
 * Abstract class for all tests using connection to db.
 */
@ExtendWith(Plv8TestContainer::class)
abstract class DbTest {

    protected fun sessionRead(): IReadSession =
        storage.openReadSession(defaultNakshaContext, false)

    companion object {
        @JvmStatic
        protected lateinit var connection: Connection

        @JvmStatic
        protected lateinit var storage: IStorage

        @JvmStatic
        protected var SCHEMA = "unit-tests-schema"

        @JvmStatic
        protected val defaultNakshaContext = NakshaContext(appId = "unit-test-app", su = false, author = "kotlin")

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            val envUrl = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
            val url = if (envUrl == null) {
                "jdbc:postgresql://localhost:5432/postgres?user=postgres&password=password&schema=$SCHEMA"
            } else {
                val params = envUrl.substring(envUrl.indexOf("?"), envUrl.length)
                    .split("&")
                    .map { it.split("=") }
                    .groupBy({ it[0] }, { it[1] })

                SCHEMA = params["schema"]!![0]
                envUrl
            }

            connection = DriverManager.getConnection(url)

            storage = JvmPlv8Storage("test_storage", connection, SCHEMA)
            storage.initStorage()
        }
    }
}