//package old
//
//import naksha.psql.PsqlTestStorage.Companion.context
//import naksha.psql.PsqlTestStorage.Companion.adminConnection
//import com.fasterxml.jackson.databind.JsonNode
//import com.fasterxml.jackson.databind.ObjectMapper
//import com.fasterxml.jackson.databind.ObjectReader
//import naksha.base.Platform
//import naksha.base.PlatformMap
//import naksha.geo.LineStringGeometry
//import naksha.geo.ProxyGeoUtil.createBBoxEnvelope
//import naksha.geo.ProxyGeoUtil.toJtsGeometry
//import naksha.geo.PointGeometry
//import naksha.model.*
//import naksha.model.request.*
//import naksha.model.request.ReadFeatures.Companion.readIdsBy
//import naksha.model.request.condition.LOp
//import naksha.model.request.condition.LOp.Companion.and
//import naksha.model.request.condition.LOp.Companion.not
//import naksha.model.request.condition.POp
//import naksha.model.request.condition.POp.Companion.contains
//import naksha.model.request.condition.POp.Companion.eq
//import naksha.model.request.condition.POp.Companion.exists
//import naksha.model.request.condition.PRef
//import naksha.model.request.condition.PRef.NON_INDEXED_PREF
//import naksha.model.request.condition.SOp
//import naksha.model.request.condition.SOp.Companion.intersectsWithTransformation
//import naksha.model.request.condition.BufferTransformation.Companion.bufferInMeters
//import naksha.model.request.condition.BufferTransformation.Companion.bufferInRadius
//import naksha.model.request.ErrorResponse
//import naksha.model.request.Response
//import naksha.model.request.SuccessResponse
//import naksha.psql.read.DbCollectionTest
//import org.junit.jupiter.api.Assertions.*
//import org.junit.jupiter.api.Order
//import org.junit.jupiter.api.condition.EnabledIf
//import org.locationtech.jts.geom.Point
//import org.locationtech.spatial4j.distance.DistanceUtils
//import org.locationtech.spatial4j.io.GeohashUtils.encodeLatLon
//import org.postgresql.util.PSQLException
//import java.sql.ResultSet
//import java.time.LocalDate
//import kotlin.test.Test
//
//class DbReadWriteTest : DbCollectionTest() {
//
//    private val session by lazy { sessionWrite() }
//    private val SINGLE_FEATURE_ID = "feature1"
//    private val SINGLE_FEATURE_INITIAL_TAG = "@:foo:world"
//    private val SINGLE_FEATURE_REPLACEMENT_TAG: String = "@:foo:bar"
//    private val fg = ProxyFeatureGenerator()
//
//    @Test
//    @Order(64)
//    @EnabledIf("runTest")
//    fun singleFeatureDeleteById() {
//        val feature = NakshaFeatureProxy("TO_DEL_BY_ID")
//        val request = WriteRequest(arrayOf(InsertFeature(collectionId, feature)))
//
//        // when
//        session.execute(request)
//        session.commit()
//
//        val deleteOp = DeleteFeature(collectionId, "TO_DEL_BY_ID", null)
//        val deleteReq = WriteRequest(arrayOf(deleteOp))
//        try {
//            val nakResponse = session.execute(deleteReq) as SuccessResponse
//            val row = nakResponse.rows[0]
//            assertSame(XYZ_EXEC_DELETED, row.op)
//            assertEquals("TO_DEL_BY_ID", row.row?.id)
//            val xyzNamespace = row.getFeature()!!.xyz()
//            assertNotEquals(xyzNamespace?.createdAt, xyzNamespace!!.updatedAt)
//            assertEquals(XYZ_EXEC_DELETED, xyzNamespace.action)
//            assertEquals(2, xyzNamespace.version)
//            assertEquals(1, nakResponse.rows.size)
//        } finally {
//            session.commit()
//        }
//
//        // verify if hst contains 2 versions
//        val read = ReadFeatures.readFeaturesByIdRequest(collectionId, "TO_DEL_BY_ID", limitVersions = 999)
//        val response = (session.execute(read) as SuccessResponse)
//        assertEquals(ACTION_CREATE.toShort(), response.rows.first().row?.meta?.action)
//        assertEquals(ACTION_DELETE.toShort(), response.rows[1].row?.meta?.action)
//    }
//
//    @Test
//    @Order(65)
//    @EnabledIf("runTest")
//    fun singleFeatureDeleteVerify() {
//        // when
//        /**
//         * Read from feature should return nothing.
//         */
//        val request = ReadFeatures.readFeaturesByIdRequest(collectionId, SINGLE_FEATURE_ID)
//        val response = session.execute(request) as SuccessResponse
//        assertEquals(0, response.rows.size)
//        var rs = getFeatureFromTable(collectionId, SINGLE_FEATURE_ID)
//        assertFalse(rs.next())
//        /**
//         * Read from deleted should return valid feature.
//         */
//        val requestWithDeleted =
//            ReadFeatures.readFeaturesByIdRequest(collectionId, SINGLE_FEATURE_ID, queryDeleted = true)
//        var featureJsonBeforeDeletion: String
//
//        /* TODO uncomment it when read with deleted is ready.
//
//    try (final ResultCursor<XyzFeature> cursor =
//    session.execute(requestWithDeleted).cursor()) {
//    cursor.next();
//    final XyzFeature feature = cursor.getFeature()!!;
//    XyzNamespace xyz = feature.xyz();
//
//    // then
//    assertSame(EExecutedOp.DELETED, cursor.op);
//    final String id = cursor .id;
//    assertEquals(SINGLE_FEATURE_ID, id);
//    final String uuid = cursor.row?.guid;
//    assertNotNull(uuid);
//    final Geometry geometry = cursor.geometry;
//    assertNotNull(geometry);
//    assertEquals(new Coordinate(5.1d, 6.0d, 2.1d), geometry.getCoordinate());
//    assertNotNull(feature);
//    assertEquals(SINGLE_FEATURE_ID, feature .id);
//    assertEquals(uuid, feature.xyz().row?.guid);
//    assertSame(EXyzAction.DELETE, feature.xyz()?.action);
//    featureJsonBeforeDeletion = cursor.getJson()
//    assertFalse(cursor.next());
//    }
//    */
//        /**
//         * Check directly $del table.
//         */
//        val collectionDelTableName = "$collectionId\$del"
//        rs = getFeatureFromTable(collectionDelTableName, SINGLE_FEATURE_ID)
//        // feature exists in $del table
//        assertTrue(rs.next())
//    }
//
//    @Test
//    @Order(66)
//    @EnabledIf("runTest")
//    fun singleFeaturePurge() {
//
//
//        // given
//        /**
//         * Data inserted in [.singleFeatureCreate] and deleted in [.singleFeatureDelete].
//         * We don't care about geometry or other properties during PURGE operation, feature_id is only required thing,
//         * thanks to that you don't have to read feature before purge operation.
//         */
//        val purgeOp = PurgeFeature(collectionId, SINGLE_FEATURE_ID, null)
//        val request = WriteRequest(arrayOf(purgeOp))
//
//        // when
//        try {
//            val response = session.execute(request) as SuccessResponse
//            val row = response.rows[0]
//
//            // then
//            assertSame(XYZ_EXEC_PURGED, row.op)
//            assertEquals(SINGLE_FEATURE_ID, row.row?.id)
//        } finally {
//            session.commit()
//        }
//    }
//
//    @Test
//    @Order(67)
//    @EnabledIf("runTest")
//    fun singleFeaturePurgeVerify() {
//        // given
//        val collectionDelTableName = "$collectionId\$del"
//
//        val rs = getFeatureFromTable(collectionDelTableName, SINGLE_FEATURE_ID)
//        // then
//        assertFalse(rs.next())
//    }
//
//    @Test
//    @Order(67)
//    @EnabledIf("runTest")
//    fun autoPurgeCheck() {
//        // given
//        val collectionWithAutoPurge: String = collectionId + "_ap"
//        val collection =
//            NakshaCollectionProxy(collectionWithAutoPurge, partitionCount(), autoPurge = true, disableHistory = false)
//        val request = WriteRequest(arrayOf(InsertFeature(NKC_TABLE, collection)))
//
//        // when
//        try {
//            val nakResponse = session.execute(request)
//            assertInstanceOf(SuccessResponse::class.java, nakResponse)
//            val successResponse = nakResponse as SuccessResponse
//            val respCol = successResponse.rows[0].getFeature()!!.proxy(NakshaCollectionProxy::class)
//            assertTrue(respCol.autoPurge)
//        } finally {
//            session.commit()
//        }
//
//        // CREATE feature
//        val featureToDel = NakshaFeatureProxy(SINGLE_FEATURE_ID)
//        val requestFeature = WriteRequest(
//            arrayOf(InsertFeature(collectionWithAutoPurge, featureToDel))
//        )
//        try {
//            val nakResponse = session.execute(requestFeature)
//            assertInstanceOf(SuccessResponse::class.java, nakResponse)
//        } finally {
//            session.commit()
//        }
//
//        // DELETE feature
//        val deleteOp = DeleteFeature(collectionId, "TO_DEL_BY_ID", null)
//        val deleteReq = WriteRequest(arrayOf(deleteOp))
//        try {
//            val nakResponse = session.execute(deleteReq)
//            assertInstanceOf(SuccessResponse::class.java, nakResponse)
//        } finally {
//            session.commit()
//        }
//
//        var rs = getFeatureFromTable("$collectionWithAutoPurge\$del", SINGLE_FEATURE_ID)
//        // then
//        assertFalse(rs.next())
//        rs = getFeatureFromTable("$collectionWithAutoPurge\$hst", SINGLE_FEATURE_ID)
//        // then
//        assertTrue(rs.next())
//    }
//
//    @Test
//    @Order(70)
//    @EnabledIf("runTest")
//    fun multipleFeaturesInsert() {
//
//        var i = 0
//        var firstNameAdded = false
//        val size = 1000
//        val features = mutableListOf<Write>()
//        while (i < size || !firstNameAdded) {
//            val feature = fg.newRandomFeature()
//            if (!firstNameAdded) {
//                firstNameAdded = fg.firstNames[0] == feature.properties["firstName"]
//            }
//            features.add(WriteFeature(collectionId, feature))
//            i++
//        }
//        val reqWrite = WriteRequest(features.toTypedArray())
//
//        try {
//            val nakResponse = session.execute(reqWrite) as SuccessResponse
//            for (row in nakResponse.rows) {
//                val op: String = row.op
//                assertSame(XYZ_EXEC_CREATED, op)
//                val id: String = row.row!!.id
//                assertNotNull(id)
//                assertNotNull(row.row?.guid)
//                val geometry = row.getFeature()!!.geometry
//                assertNotNull(geometry)
//                val f = row.getFeature()!!
//                assertNotNull(f)
//                assertEquals(id, f.id)
//                assertSame(XYZ_EXEC_CREATED, f.xyz()?.action)
//            }
//        } finally {
//            session.commit()
//        }
//    }
//
//    @Test
//    @Order(71)
//    @EnabledIf("runTest")
//    fun multipleFeaturesRead() {
//
//
//        val request = ReadFeatures(
//            collectionIds = arrayOf(collectionId),
//            op = LOp.or(
//                contains(PRef.TAGS, "@:firstName:" + fg.firstNames[0]),
//                contains(PRef.TAGS, "@:firstName:" + fg.firstNames[1])
//            )
//        )
//
//        try {
//            val response = session.execute(request) as SuccessResponse
//            // We expect that at least one feature was found!
//            for (row in response.rows) {
//                assertSame(XYZ_EXEC_READ, row.op)
//                val id: String = row.row!!.id
//                assertNotNull(id)
//                val uuid: String = row.row!!.meta!!.getLuid().toString()
//                assertNotNull(uuid)
//                assertNotNull(row.row?.geo)
//                val f = row.getFeature()!!
//                assertNotNull(f)
//                assertEquals(id, f.id)
//                assertEquals(uuid, f.xyz()?.uuid)
//                assertSame(XYZ_EXEC_CREATED, f.xyz()?.action)
//                val tags = f.xyz()?.tags
//                assertNotNull(tags)
//                assertTrue(tags!!.size > 0)
//                assertTrue(
//                    tags.contains("@:firstName:" + fg.firstNames[0])
//                            || tags.contains("@:firstName:" + fg.firstNames[1])
//                )
//            }
//        } finally {
//            session.commit()
//        }
//    }
//
//    @Test
//    @Order(73)
//    @EnabledIf("runTest")
//    fun testRestoreOrder() {
//        // given
//        val feature1 = NakshaFeatureProxy("123")
//        val feature2 = NakshaFeatureProxy("121")
//        val request = WriteRequest(
//            arrayOf(
//                InsertFeature(collectionId, feature1),
//                InsertFeature(collectionId, feature2)
//            ),
//            noResults = false,
//            allowRandomOrder = false,
//            noFeature = false,
//            noGeometry = false,
//            noMeta = false,
//            noTags = true,
//            resultFilter = arrayOf()
//        )
//
//        try {
//            // when
//            val result = session.execute(request) as SuccessResponse
//
//            // then
//            val rows = result.rows
//            assertEquals("123", rows[0].row?.id)
//            assertEquals("121", rows[1].row?.id)
//        } finally {
//            session.commit()
//        }
//    }
//
//    @Test
//    @Order(74)
//    @EnabledIf("runTest")
//    fun limitedRead() {
//        limitToN(1)
//        limitToN(2)
//    }
//
//
//    private fun limitToN(limit: Int) {
//        val request = ReadFeatures(collectionIds = arrayOf(collectionId), limit = limit)
//        val response = session.execute(request) as SuccessResponse
//        assertEquals(limit, response.rows.size)
//    }
//
//    @Test
//    @Order(110)
//    @EnabledIf("runTest")
//    fun readCollections() {
//
//        val request = ReadCollections(ids = arrayOf(collectionId))
//        val response = session.execute(request) as SuccessResponse
//
//        assertEquals(collectionId, response.rows.first().row?.id)
//    }
//
//    @Test
//    @Order(111)
//    @EnabledIf("runTest")
//    fun intersectionSearch() {
//        val feature = NakshaFeatureProxy("otherFeature")
//        val geometry = LineStringGeometry(
//            PointGeometry(4.0, 5.0),
//            PointGeometry(4.0, 6.0)
//        )
//        feature.geometry = geometry
//        val request = WriteRequest(
//            arrayOf(InsertFeature(collectionId, feature))
//        )
//        try {
//            val nakResponse = session.execute(request)
//            assertInstanceOf(SuccessResponse::class.java, nakResponse)
//        } finally {
//            session.commit()
//        }
//
//        // read by bbox that surrounds only first point
//        val envelopeBbox = createBBoxEnvelope(3.9, 4.9, 4.1, 5.1)
//        val readFeatures = ReadFeatures(collectionIds = arrayOf(collectionId), spatialOp = SOp.intersects(envelopeBbox))
//
//        val response = session.execute(readFeatures) as SuccessResponse
//        // then
//        assertEquals("otherFeature", response.rows.first().getFeature()!!.id)
//        assertEquals(1, response.rows.size)
//    }
//
//
//    @Test
//    @Order(112)
//    @EnabledIf("runTest")
//    fun notIndexedPropertyRead() {
//
//        // given
//        val jsonReference =
//            "{\"id\":\"32167\",\"properties\":{\"weight\":60,\"length\":null,\"color\":\"red\",\"ids\":[0,1,9],\"ids2\":[\"a\",\"b\",\"c\"],\"subJson\":{\"b\":1},\"references\":[{\"id\":\"urn:here::here:Topology:106003684\",\"type\":\"Topology\",\"prop\":{\"a\":1}}]}}"
//        val reader: ObjectReader = ObjectMapper().reader()
//
//        val featurePlatform = (Platform.fromJSON(jsonReference) as PlatformMap).proxy(NakshaFeatureProxy::class)
//
//        session.execute(WriteRequest(arrayOf(WriteFeature(collectionId, featurePlatform))))
//        session.commit()
//
//        val expect = { readFeaturesReq: ReadRequest ->
//            val res = session.execute(readFeaturesReq) as SuccessResponse
//            assertEquals("32167", res.rows.first().row?.id)
//            assertEquals(1, res.rows.size)
//        }
//
//        // when - search for int value
//        val weightSearch = eq(NON_INDEXED_PREF("properties", "weight"), 60)
//        // then
//        expect(readIdsBy(collectionId, weightSearch))
//
//        // when - search 'not'
//        val notSearch = not(eq(NON_INDEXED_PREF("properties", "weight"), 59))
//        // then
//        expect(readIdsBy(collectionId, notSearch))
//
//        // when - search 'exists'
//        val existsSearch = exists(NON_INDEXED_PREF("properties", "weight"))
//        // then
//        expect(readIdsBy(collectionId, existsSearch))
//
//        // when - search 'not exists'
//        val notExistsSearch = and(not(exists(NON_INDEXED_PREF("properties", "weight2"))), eq(PRef.ID, "32167"))
//        // then
//        expect(readIdsBy(collectionId, notExistsSearch))
//
//        // when - search not null value
//        val notNullSearch = POp.isNotNull(NON_INDEXED_PREF("properties", "color"))
//        // then
//        expect(readIdsBy(collectionId, notNullSearch))
//
//        // when - search null value
//        val nullSearch = POp.isNull(NON_INDEXED_PREF("properties", "length"))
//        // then
//        expect(readIdsBy(collectionId, nullSearch))
//
//        // when - search array contains
//        val arraySearch = contains(NON_INDEXED_PREF("properties", "ids"), 9)
//        // then
//        expect(readIdsBy(collectionId, arraySearch))
//
//        // when - search array contains string
//        val arrayStringSearch = contains(NON_INDEXED_PREF("properties", "ids2"), "a")
//        // then
//        expect(readIdsBy(collectionId, arrayStringSearch))
//
//        // when - search by json object
//        val jsonSearch2 = contains(
//            NON_INDEXED_PREF("properties", "references"),
//            reader.readValue("[{\"id\":\"urn:here::here:Topology:106003684\"}]", ArrayList::class.java)
//        )
//        // then
//        expect(readIdsBy(collectionId, jsonSearch2))
//
//        // when - search by json object
//        val jsonSearch3 = contains(
//            NON_INDEXED_PREF("properties", "references"),
//            reader.readValue("[{\"prop\":{\"a\":1}}]", JsonNode::class.java)
//        )
//        // then
//        expect(readIdsBy(collectionId, jsonSearch3))
//
//        // when - search by json object
//        val jsonSearch4 =
//            contains(NON_INDEXED_PREF("properties", "subJson"), reader.readValue("{\"b\":1}", JsonNode::class.java))
//        // then
//        expect(readIdsBy(collectionId, jsonSearch4))
//    }
//
//
//    @Test
//    @Order(120)
//    @EnabledIf("runTest")
//    fun dropFooCollection() {
//
//        val deleteCollection = DeleteFeature(collectionId = NKC_TABLE, id = collectionId)
//        val nakResponse = session.execute(WriteRequest(arrayOf(deleteCollection)))
//        assertInstanceOf(SuccessResponse::class.java, nakResponse)
//        session.commit()
//
//        // try readSession after purge, table doesn't exist anymore, so it should throw an exception.
//        assertThrowsExactly(
//            PSQLException::class.java,
//            { getFeatureFromTable(collectionId, SINGLE_FEATURE_ID) },
//            "ERROR: relation \"foo\" does not exist"
//        )
//    }
//
//    private fun getFeatureFromTable(table: String, featureId: String): ResultSet {
//        val stmt = adminConnection.prepareStatement("SELECT * from $table WHERE id = ? ;")
//        stmt.setString(1, featureId)
//        return stmt.executeQuery()
//    }
//}
