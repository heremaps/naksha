package naksha.psql

import Plv8TestContainer.Companion.postgreSQLContainer
import naksha.model.ACTION_CREATE
import naksha.model.NakshaCollectionProxy
import naksha.model.NakshaCollectionProxy.Companion.DEFAULT_GEO_INDEX
import naksha.model.XYZ_EXEC_CREATED
import naksha.model.request.InsertFeature
import naksha.model.request.ResultRow
import naksha.model.request.WriteFeature
import naksha.model.request.WriteRequest
import naksha.model.response.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIf
import java.io.IOException
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class DbCollectionTest : DbTest() {

    protected val collectionId = "plv8feature"

    open fun runTest() = true
    open fun dropInitially() = true

    @Test
    @Order(30)
    @EnabledIf("runTest")
    fun createCollection() {
        val session = sessionWrite()
        val nakCollection =
            NakshaCollectionProxy(collectionId, partitionCount(), autoPurge = false, disableHistory = false)
        val collectionWriteReq = WriteRequest(arrayOf(WriteFeature(NKC_TABLE, nakCollection)))
        try {
            val response: Response = session.execute(collectionWriteReq)
            assertInstanceOf(SuccessResponse::class.java, response)
            val successResponse: SuccessResponse = response as SuccessResponse
            val responseRow: ResultRow = successResponse.rows[0]
            val row: Row = responseRow.row!!
            assertEquals(collectionId, row.id)
            assertNotNull(row.meta?.getLuid())
            assertSame(XYZ_EXEC_CREATED, responseRow.op)
            val collection = responseRow.getFeature()?.proxy(NakshaCollectionProxy::class)!!
            assertNotNull(collection)
            assertEquals(collectionId, row.id)
            assertFalse(collection.disableHistory)
            assertEquals(partition(), collection.hasPartitions())
            assertNotNull(collection.properties)
            assertSame(ACTION_CREATE.toShort(), row.meta?.action)
        } finally {
            session.commit()
        }
    }

    @Test
    @Order(35)
    @EnabledIf("runTest")
    @Throws(SQLException::class)
    fun createExistingCollection() {
        val session = sessionWrite()
        val nakCollection =
            NakshaCollectionProxy(collectionId, partitionCount(), autoPurge = false, disableHistory = false)
        val collectionWriteReq = WriteRequest(arrayOf(InsertFeature(NKC_TABLE, nakCollection)))
        try {
            val response = session.execute(collectionWriteReq)
            assertInstanceOf(ErrorResponse::class.java, response)
            val errorResponse: ErrorResponse = response as ErrorResponse
            assertEquals("NX000", errorResponse.reason.error)
        } finally {
            session.commit()
        }

        assertTrue(isLockReleased(collectionId))
    }

    // Custom stuff between 50 and 9000
    @Test
    @Order(9002)
    @EnabledIf("isTestContainerRun")
    @Throws(
        SQLException::class, IOException::class, InterruptedException::class
    )
    fun createBrittleCollection() {
        val session = sessionWrite()

        // given

        // PREPARE CATALOGS IN DOCKER CONTAINER
        createCatalogsForTablespace()
        // PREPARE TABLESPACES
        createTablespace()

        // WRITE COLLECTION THAT SHOULD BE TEMPORARY
        val collectionId = "foo_temp"

        val nakCollection =
            NakshaCollectionProxy(collectionId, partitionCount(), DEFAULT_GEO_INDEX, "brittle", false, false)
        val collectionWriteReq = WriteRequest(arrayOf(WriteFeature(NKC_TABLE, nakCollection)))
        val response = session.execute(collectionWriteReq)
        assertInstanceOf(SuccessResponse::class.java, response)

        session.commit()

        // then
        val expectedTablespace = TEMPORARY_TABLESPACE
        assertEquals(expectedTablespace, getTablespace(collectionId))
        assertEquals(expectedTablespace, getTablespace("$collectionId\$hst"))
        val currentYear = LocalDate.now().year
        assertEquals(expectedTablespace, getTablespace("$collectionId\$hst_$currentYear"))
        assertEquals(expectedTablespace, getTablespace("$collectionId\$del"))
        assertEquals(expectedTablespace, getTablespace("$collectionId\$meta"))
        if (partition()) {
            assertEquals(
                expectedTablespace,
                getTablespace(collectionId + "\$hst_" + currentYear + "_p000")
            )
            assertEquals(expectedTablespace, getTablespace("$collectionId\$del_p000"))
            assertEquals(expectedTablespace, getTablespace("$collectionId\$p000"))
        }
    }

    @Throws(SQLException::class)
    private fun getTablespace(table: String): String {
        connection.prepareStatement("select tablespace from pg_tables where tablename=?").use { statement ->
            statement.setString(1, table)
            val resultSet: ResultSet = statement.executeQuery()
            assertTrue(resultSet.next()) { "no table found: $table" }
            return resultSet.getString(1)
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun createCatalogsForTablespace() {
        postgreSQLContainer.execInContainer("mkdir", "-p", "/tmp/temporary_space")
        postgreSQLContainer.execInContainer("chown", "postgres:postgres", "-R", "/tmp/temporary_space")
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun createTablespace() {
        postgreSQLContainer.execInContainer(
            "psql",
            "-U",
            "postgres",
            "-d",
            "postgres",
            "-c",
            String.format("create tablespace %s LOCATION '/tmp/temporary_space';", TEMPORARY_TABLESPACE)
        )
    }

    @Throws(SQLException::class)
    private fun isLockReleased(collectionId: String): Boolean {
        connection.prepareStatement("select count(*) from pg_locks where locktype = 'advisory' and ((classid::bigint << 32) | objid::bigint) = ?;")
            .use { stmt ->
                stmt.setLong(1, Static.lockId(collectionId).toLong())
                val resultSet: ResultSet = stmt.executeQuery()
                resultSet.next()
                return resultSet.getInt(1) == 0
            }
    }

    open fun partitionCount(): Int {
        return if (partition()) 8 else 1
    }

    open fun partition(): Boolean = true
}
