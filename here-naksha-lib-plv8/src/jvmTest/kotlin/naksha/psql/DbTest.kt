package naksha.psql

import TestContainer
import TestContainer.Companion.context
import TestContainer.Companion.initialized
import TestContainer.Companion.adminSession
import TestContainer.Companion.schema
import TestContainer.Companion.storage
import naksha.model.IReadSession
import naksha.model.IWriteSession
import org.junit.jupiter.api.*
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Abstract class for all tests using connection to db.
 */
@ExtendWith(TestContainer::class)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class DbTest {

    protected fun sessionRead(): IReadSession =
        storage.newReadSession(context, true)

    protected fun sessionWrite(): IWriteSession =
        storage.newWriteSession(context)

    fun isTestContainerRun(): Boolean = initialized.get()

    @Test
    @Order(10)
    @EnabledIf("dropInitially")
    fun dropSchemaIfExists() {
        adminSession.apply {
            execute("DROP SCHEMA IF EXISTS ${PgUtil.quoteIdent(schema)} CASCADE")
        }
    }

    @Test
    @Order(11)
    @EnabledIf("runTest")
    fun initStorage() {
        storage.initStorage()
    }
}