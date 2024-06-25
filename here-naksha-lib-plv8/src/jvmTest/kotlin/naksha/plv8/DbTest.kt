package naksha.plv8

import Plv8TestContainer
import com.here.naksha.lib.plv8.naksha.plv8.JvmPlv8Storage
import naksha.model.IReadSession
import naksha.model.IStorage
import naksha.model.IWriteSession
import naksha.model.NakshaContext
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.function.Executable
import java.sql.Connection
import java.sql.DriverManager

/**
 * Abstract class for all tests using connection to db.
 */
@ExtendWith(Plv8TestContainer::class)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class DbTest {

    protected fun sessionRead(): IReadSession =
        storage.openReadSession(defaultNakshaContext, false)

    protected fun sessionWrite(): IWriteSession =
        storage.openWriteSession(defaultNakshaContext, true)

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
                SCHEMA = Plv8TestContainer.schema
                Plv8TestContainer.url
            } else {
                val params = envUrl.substring(envUrl.indexOf("?"), envUrl.length)
                    .split("&")
                    .map { it.split("=") }
                    .groupBy({ it[0] }, { it[1] })

                SCHEMA = params["schema"]!![0]
                envUrl
            }

            connection = DriverManager.getConnection(url)
        }
    }

    fun isTestContainerRun(): Boolean {
        return Plv8TestContainer.initialized
    }

    @Test
    @Order(10)
    @EnabledIf("runTest")
    fun createStorage() {
        storage = JvmPlv8Storage("test_storage", connection, SCHEMA)
    }

    @Test
    @Order(11)
    @EnabledIf("dropInitially")
    fun dropSchemaIfExists() {
        assertNotNull(storage)
//        storage.dropSchema()
    }

    @Test
    @Order(13)
    @EnabledIf("runTest")
    fun initStorage() {
        storage.initStorage()
    }
}