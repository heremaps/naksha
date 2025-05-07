package naksha.psql

import kotlin.test.Test

class PsqlConnectionTest: PgTestBase(collection = null) {

    @Test
    fun testConnectionCloseAfterTimeout() {
        // TODO: We do not have access any longer to cluster, fix this!
//        // given
//        val master = env.storage.adminMap.cluster.master as PsqlInstance
//        master.connectionPool.clear()
//        val conn = master.openConnection(env.options.copy(socketTimeout = 1, stmtTimeout = 1, connectTimeout = 1), false)
//
//        // when jdbc connection has been closed i.e. by SocketTimeout
//        conn.jdbc.close()
//
//        // then our conn close should not return connection to pool
//        assertEquals(1, master.connectionPool.size)
//        conn.close()
//        assertEquals(0, master.connectionPool.size)
    }
}