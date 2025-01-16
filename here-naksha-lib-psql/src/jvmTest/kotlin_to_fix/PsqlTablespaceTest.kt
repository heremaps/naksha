//package naksha.psql
//
//import kotlinx.datetime.Clock
//import kotlinx.datetime.TimeZone
//import kotlinx.datetime.toLocalDateTime
//import naksha.model.objects.NakshaCollection
//import naksha.model.objects.NakshaCollection.Companion.DEFAULT_GEO_INDEX
//import naksha.model.request.op.UpsertFeature
//import naksha.model.request.WriteRequest
//import naksha.model.request.SuccessResponse
//import org.junit.jupiter.api.Disabled
//import org.junit.jupiter.api.Test
//import org.testcontainers.containers.GenericContainer
//import kotlin.test.assertEquals
//import kotlin.test.assertIs
//import kotlin.test.assertTrue
//
//class PsqlTablespaceTest {
// TODO check if still relevant

//    private val env = TestEnv(dropSchema = false, initStorage = false)
//    private var dockerContainerInfo: GenericContainer<*>? = null
//
//    init {
//        dockerContainerInfo = PsqlTestStorage.dockerContainerInfo.get()?.container
//    }
//
//    @Disabled
//    @Test
//    fun createBrittleCollection() {
//        // given
//        if (dockerContainerInfo == null) // skip
//            return
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
//            NakshaCollection(collectionId, partitionCount(), DEFAULT_GEO_INDEX, "brittle", false, false)
//        val collectionWriteReq = WriteRequest()
//        collectionWriteReq.add(UpsertFeature(NKC_TABLE, nakCollection))
//        val response = env.pgSession.write(collectionWriteReq)
//        assertIs<SuccessResponse>(response)
//
//        env.pgSession.commit()
//
//        // then
//        val expectedTablespace = TEMPORARY_TABLESPACE
//        assertEquals(expectedTablespace, getTablespace(collectionId))
//        assertEquals(expectedTablespace, getTablespace("$collectionId\$hst"))
//        val currentYear = Clock.System.now().toLocalDateTime(TimeZone.UTC).year
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
//        env.pgSession.usePgConnection().prepare("select tablespace from pg_tables where tablename = $1", arrayOf(PgType.STRING.toString()))
//            .execute(arrayOf(table)).fetch()
//            .use {
//                assertTrue(it.isRow(),"no table found: $table")
//                return it.column("tablespace") as String
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
//    fun partitionCount(): Int {
//        return if (partition()) 8 else 1
//    }
//
//    fun partition(): Boolean = true
//}