package naksha.psql.read
//
//import naksha.model.ACTION_CREATE
//import naksha.model.NakshaCollectionProxy
//import naksha.model.NakshaCollectionProxy.Companion.DEFAULT_GEO_INDEX
//import naksha.model.XYZ_EXEC_CREATED
//import naksha.model.request.InsertFeature
//import naksha.model.request.ResultRow
//import naksha.model.request.WriteFeature
//import naksha.model.request.WriteRequest
//import naksha.model.response.*
//import naksha.psql.*
//import naksha.psql.NKC_TABLE
//import kotlin.test.*
//
//abstract class DbCollectionTest : TestBasics(run=false) {
//
//    protected val collectionId = "plv8feature"
//
//    @Test
//    fun t010_createCollection() {
//        if (!run) return
//        val nakCollection = NakshaCollectionProxy(collectionId, partitionCount(), autoPurge = false, disableHistory = false)
//        val collectionWriteReq = WriteRequest(arrayOf(WriteFeature(NKC_TABLE, nakCollection)))
//        try {
//            val response: Response = nakshaSession.execute(collectionWriteReq)
//            assertIs<SuccessResponse>(response)
//            val successResponse: SuccessResponse = response as SuccessResponse
//            val responseRow: ResultRow = successResponse.rows[0]
//            val row: Row = responseRow.row!!
//            assertEquals(collectionId, row.id)
//            assertNotNull(row.meta?.getLuid())
//            assertSame(XYZ_EXEC_CREATED, responseRow.op)
//            val collection = responseRow.getFeature()?.proxy(NakshaCollectionProxy::class)!!
//            assertNotNull(collection)
//            assertEquals(collectionId, row.id)
//            assertFalse(collection.disableHistory)
//            assertEquals(partition(), collection.hasPartitions())
//            assertNotNull(collection.properties)
//            assertSame(ACTION_CREATE.toShort(), row.meta?.action)
//        } finally {
//            nakshaSession.commit()
//        }
//    }
//
//    @Test
//    fun t011_createExistingCollection() {
//        if (!run) return
//        val nakCollection =
//            NakshaCollectionProxy(collectionId, partitionCount(), autoPurge = false, disableHistory = false)
//        val collectionWriteReq = WriteRequest(arrayOf(InsertFeature(NKC_TABLE, nakCollection)))
//        try {
//            val response = nakshaSession.execute(collectionWriteReq)
//            assertIs<ErrorResponse>(response)
//            val errorResponse: ErrorResponse = response as ErrorResponse
//            assertEquals("NX000", errorResponse.reason.error)
//        } finally {
//            nakshaSession.commit()
//        }
//
//        assertTrue(isLockReleased(collectionId))
//    }
//
//    // Custom stuff between 50 and 9000
//    @Test
//    fun createBrittleCollection() {
//        if (!run) return
//        // given
//
//        // PREPARE CATALOGS IN DOCKER CONTAINER
//        createCatalogsForTablespace()
//        // PREPARE TABLESPACES
//        createTablespace()
//
//        // WRITE COLLECTION THAT SHOULD BE TEMPORARY
//        val collectionId = "foo_temp"
//
//        val nakCollection =
//            NakshaCollectionProxy(collectionId, partitionCount(), DEFAULT_GEO_INDEX, "brittle", false, false)
//        val collectionWriteReq = WriteRequest(arrayOf(WriteFeature(NKC_TABLE, nakCollection)))
//        val response = nakshaSession.execute(collectionWriteReq)
//        assertIs<SuccessResponse>(response)
//
//        nakshaSession.commit()
//
//        // then
//        val expectedTablespace = TEMPORARY_TABLESPACE
//        assertEquals(expectedTablespace, getTablespace(collectionId))
//        assertEquals(expectedTablespace, getTablespace("$collectionId\$hst"))
//        val currentYear = LocalDate.now().year
//        assertEquals(expectedTablespace, getTablespace("$collectionId\$hst_$currentYear"))
//        assertEquals(expectedTablespace, getTablespace("$collectionId\$del"))
//        assertEquals(expectedTablespace, getTablespace("$collectionId\$meta"))
//        if (partition()) {
//            assertEquals(
//                expectedTablespace,
//                getTablespace(collectionId + "\$hst_" + currentYear + "_p000")
//            )
//            assertEquals(expectedTablespace, getTablespace("$collectionId\$del_p000"))
//            assertEquals(expectedTablespace, getTablespace("$collectionId\$p000"))
//        }
//    }
//
//    private fun getTablespace(table: String): String {
//        nakshaSession.usePgConnection().prepare("select tablespace from pg_tables where tablename = $1", arrayOf(PgType.STRING.toString()))
//            .use {
//                val resultSet = PgPlan. (it.execute(arrayOf(table)))
//                assertTrue(resultSet.next()) { "no table found: $table" }
//                return resultSet.getString(1)
//            }
//    }
//
//    private fun createCatalogsForTablespace() {
//        dockerContainerInfo!!.execInContainer("mkdir", "-p", "/tmp/temporary_space")
//        dockerContainerInfo!!.execInContainer("chown", "postgres:postgres", "-R", "/tmp/temporary_space")
//    }
//
//    private fun createTablespace() {
//        dockerContainerInfo!!.execInContainer(
//            "naksha/psql",
//            "-U",
//            "postgres",
//            "-d",
//            "postgres",
//            "-c",
//            String.format("create tablespace %s LOCATION '/tmp/temporary_space';", TEMPORARY_TABLESPACE)
//        )
//    }
//
//    private fun isLockReleased(collectionId: String): Boolean {
//        adminConnection.prepareStatement(
//            "select count(*) from pg_locks where locktype = 'advisory' and ((classid::bigint << 32) | objid::bigint) =" +
//                    " ?;"
//        )
//            .use { stmt ->
//                stmt.setLong(1, PgStatic.lockId(collectionId).toLong())
//                val resultSet: ResultSet = stmt.executeQuery()
//                resultSet.next()
//                return resultSet.getInt(1) == 0
//            }
//    }
//
//    open fun partitionCount(): Int {
//        return if (partition()) 8 else 1
//    }
//
//    open fun partition(): Boolean = true
//}
