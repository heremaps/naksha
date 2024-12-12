package naksha.psql

import naksha.psql.base.PgTestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class PsqlConnectionTest: PgTestBase() {

    @Test
    fun testConnectionCloseAfterTimeout() {
        // given
        val master = env.storage.cluster.master as PsqlInstance
        master.connectionPool.clear()
        val conn = master.openConnection(env.options.copy(socketTimeout = 1, stmtTimeout = 1, connectTimeout = 1), false)

        // when jdbc connection has been closed i.e. by SocketTimeout
        conn.jdbc.close()

        // then our conn close should not return connection to pool
        assertEquals(1, master.connectionPool.size)
        conn.close()
        assertEquals(0, master.connectionPool.size)
    }
}