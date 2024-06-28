package naksha.psql

import TestContainer.Companion.context
import TestContainer.Companion.adminConnection
import TestContainer.Companion.storage
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import naksha.base.Platform
import naksha.base.PlatformMap
import naksha.geo.LineStringProxy
import naksha.geo.PointProxy
import naksha.geo.ProxyGeoUtil.createBBoxEnvelope
import naksha.geo.ProxyGeoUtil.toJtsGeometry
import naksha.geo.cords.PointCoordsProxy
import naksha.model.*
import naksha.model.request.*
import naksha.model.request.ReadFeatures.Companion.readIdsBy
import naksha.model.request.condition.LOp
import naksha.model.request.condition.LOp.Companion.and
import naksha.model.request.condition.LOp.Companion.not
import naksha.model.request.condition.POp
import naksha.model.request.condition.POp.Companion.contains
import naksha.model.request.condition.POp.Companion.eq
import naksha.model.request.condition.POp.Companion.exists
import naksha.model.request.condition.PRef
import naksha.model.request.condition.PRef.NON_INDEXED_PREF
import naksha.model.request.condition.SOp
import naksha.model.request.condition.SOp.Companion.intersectsWithTransformation
import naksha.model.request.condition.geometry.BufferTransformation.Companion.bufferInMeters
import naksha.model.request.condition.geometry.BufferTransformation.Companion.bufferInRadius
import naksha.model.response.ErrorResponse
import naksha.model.response.Response
import naksha.model.response.SuccessResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.locationtech.jts.geom.Point
import org.locationtech.spatial4j.distance.DistanceUtils
import org.locationtech.spatial4j.io.GeohashUtils.encodeLatLon
import org.postgresql.util.PSQLException
import java.sql.ResultSet
import java.time.LocalDate

class DbReadWriteTest : DbCollectionTest() {

    private val session by lazy { sessionWrite() }
    private val SINGLE_FEATURE_ID = "feature1"
    private val SINGLE_FEATURE_INITIAL_TAG = "@:foo:world"
    private val SINGLE_FEATURE_REPLACEMENT_TAG: String = "@:foo:bar"
    private val fg = ProxyFeatureGenerator()


    @Test
    @Order(50)
    @EnabledIf("runTest")
    fun singleFeatureCreate() {

        val feature = NakshaFeatureProxy()
        feature.id = SINGLE_FEATURE_ID
        val geometry = PointProxy(5.0, 6.0, 2.0)
        feature.geometry = geometry
        val xyz = XyzProxy()
        xyz.tags = TagsProxy()
        xyz.tags.add(SINGLE_FEATURE_INITIAL_TAG)
        val nakProperties = NakshaPropertiesProxy()
        nakProperties.xyz = xyz
        feature.properties = nakProperties

        val nakInsertFeature = InsertFeature(collectionId, feature)
        val request = WriteRequest(arrayOf(nakInsertFeature))
        try {
            val response: Response = session.execute(request)
            assertInstanceOf(SuccessResponse::class.java, response)
            val successResp: SuccessResponse = response as SuccessResponse
            assertEquals(1, successResp.rows.size)
            val first = successResp.rows.first()
            assertSame(XYZ_EXEC_CREATED, first.op)
            val id: String = first.row!!.id
            assertEquals(SINGLE_FEATURE_ID, id)
            assertNotNull(first.row!!.meta?.getLuid())
            val f = first.getFeature()!!
            val point = f.geometry!!.asPointProxy().coordinates!!
            assertNotNull(point)
            assertEquals(5.0, point.getLongitude())
            assertEquals(6.0, point.getLatitude())
            assertEquals(2.0, point.getAltitude())
            assertEquals(SINGLE_FEATURE_ID, f.id)
            val props = f.properties.proxy(NakshaPropertiesProxy::class)
            assertEquals(1, props.xyz?.uuid)
            assertSame(XYZ_EXEC_CREATED, props.xyz?.action)
            assertEquals(
                SINGLE_FEATURE_INITIAL_TAG,
                props.xyz?.tags?.first()
            )
        } finally {
            session.commit()
        }
    }


    @Test
    @Order(51)
    @EnabledIf("runTest")
    fun verifyTransactionCounts() {
        val readFeatures = ReadFeatures(collectionIds = arrayOf("naksha~transactions"))
        val nakResponse = session.execute(readFeatures)
        // there should be just one transaction log at the moment (single feature has been created)
        assertInstanceOf(SuccessResponse::class.java, nakResponse)
        val successResp = nakResponse as SuccessResponse
        val first = successResp.rows.first()
        val idFields: List<String> = first.row!!.id.split(":")
        assertEquals(storage.id(), idFields[0])
        assertEquals("txn", idFields[1])
        assertEquals(4, idFields[2].length) // year (4- digits)
        assertTrue(idFields[3].length <= 2) // month (1 or 2 digits)
        assertTrue(idFields[4].length <= 2) // day (1 or 2 digits)
        assertEquals(
            "3",
            idFields[5]
        ) // seq txn (first for internal collections, second for feature create collection, third for create feature)
        assertEquals("0", idFields[6]) // uid seq

        val transactionProxy = first.getFeature()!!.proxy(NakshaTransactionProxy::class)
        assertEquals(1, transactionProxy.featuresModified)
        assertEquals(1, transactionProxy.collections[collectionId])
        assertNull(transactionProxy.seqNumber)
    }

    @Test
    @Order(51)
    @EnabledIf("runTest")
    fun singleFeatureRead() {


        val readFeatures = ReadFeatures.readHeadBy(collectionId, eq(PRef.ID, SINGLE_FEATURE_ID))

        val nakResponse = session.execute(readFeatures)
        assertInstanceOf(SuccessResponse::class.java, nakResponse)
        val successResp = nakResponse as SuccessResponse
        assertEquals(1, successResp.rows.size)
        val first = successResp.rows[0]
        assertSame(XYZ_EXEC_READ, first.op)

        val feature = first.getFeature()!!
        val xyz = feature.xyz()

        // then
        assertEquals(SINGLE_FEATURE_ID, feature.id)
        assertEquals(1, xyz?.version)
        assertEquals(XYZ_EXEC_CREATED, xyz?.action)
        val geometry = feature.geometry
        assertNotNull(geometry)
        val coordinate = geometry!!.asPointProxy().coordinates!!
        assertEquals(5.0, coordinate.getLongitude())
        assertEquals(6.0, coordinate.getLatitude())
        assertEquals(2.0, coordinate.getAltitude())

        val lt = LocalDate.now()
        val guid = first.row!!.guid!!
        assertEquals(storage.id(), guid.storageId)
        assertEquals(collectionId, guid.collectionId)
        assertEquals(lt.year, guid.luid.txn.year()) // year (4- digits)
        assertEquals(lt.month, guid.luid.txn.month()) // month (1 or 2 digits)
        assertEquals(lt.dayOfMonth, guid.luid.txn.day()) // day (1 or 2 digits)
        assertEquals(
            3,
            guid.luid.txn.seq()
        ) // seq txn (first for internal collections, second for feature create collection, third for create feature)
        assertEquals(0, guid.luid.uid) // uid seq
        assertEquals(context.appId, xyz?.appId)
        assertEquals(context.author, xyz?.author)
        assertEquals(xyz?.createdAt, xyz?.updatedAt)

        // FIXME after merge
//      assertEquals(encodeLatLon(coordinate.y, coordinate.x, 14), xyz.get("grid"));
        assertEquals(0, first.row?.meta?.geoGrid)

        assertEquals(listOf(SINGLE_FEATURE_INITIAL_TAG), xyz?.tags)

        assertEquals(1, successResp.size())
    }

    @Test
    @Order(52)
    @EnabledIf("runTest")
    fun readByBbox() {

        val envelopeBbox = createBBoxEnvelope(4.0, 5.0, 5.5, 6.5)

        val readFeatures = ReadFeatures(
            collectionIds = arrayOf(collectionId),
            spatialOp = SOp.intersects(envelopeBbox)
        )

        val nakResponse = session.execute(readFeatures)
        assertInstanceOf(SuccessResponse::class.java, nakResponse)
        val successResp = nakResponse as SuccessResponse
        // then
        assertEquals(SINGLE_FEATURE_ID, successResp.rows.first().row?.id)
        assertEquals(1, successResp.size())
    }

    @Test
    @Order(52)
    @EnabledIf("runTest")
    fun readWithBuffer() {

        val xyzPoint = PointProxy(4.0, 5.0)

        val readFeatures = ReadFeatures(
            collectionIds = arrayOf(collectionId),
            spatialOp = intersectsWithTransformation(xyzPoint, bufferInRadius(1.0))
        )

        var nakResponse = session.execute(readFeatures)
        assertInstanceOf(SuccessResponse::class.java, nakResponse)
        assertEquals(0, nakResponse.size())

        val readFeatures2 = ReadFeatures(
            collectionIds = arrayOf(collectionId),
            spatialOp = intersectsWithTransformation(xyzPoint, bufferInRadius(2.0))
        )
        nakResponse = session.execute(readFeatures2)
        assertEquals(1, nakResponse.size())
    }

    @Test
    @Order(52)
    @EnabledIf("runTest")
    fun readWithBufferInMeters() {

        val xyzPoint = PointProxy(4.0, 5.0)

        var readFeatures = ReadFeatures(
            collectionIds = arrayOf(collectionId),
            spatialOp = intersectsWithTransformation(xyzPoint, bufferInMeters(150000.0))
        )

        var nakResponse = session.execute(readFeatures)
        assertEquals(0, nakResponse.size())

        readFeatures = ReadFeatures(
            collectionIds = arrayOf(collectionId),
            spatialOp = intersectsWithTransformation(xyzPoint, bufferInMeters(160000.0))
        )
        nakResponse = session.execute(readFeatures)
        val successResp = nakResponse as SuccessResponse
        val jtsGeometry = toJtsGeometry(successResp.rows.first().getFeature()!!.geometry!!)
        val distanceInRadius: Double = toJtsGeometry(xyzPoint).distance(jtsGeometry)
        // this is very inaccurate method to calculate meters, but it's enough for test purpose
        val distanceInMeters: Double = distanceInRadius * DistanceUtils.DEG_TO_KM * 1000
        assertTrue(150000.0 < distanceInMeters && distanceInMeters < 160000.0, "Real: $distanceInMeters")
    }

    @Test
    @Order(54)
    @EnabledIf("runTest")
    fun singleFeatureUpsert() {
        // given
        val featureToUpdate = NakshaFeatureProxy()
        featureToUpdate.id = SINGLE_FEATURE_ID
        val xyzGeometry = PointProxy(5.0, 6.0, 2.0)
        featureToUpdate.geometry = xyzGeometry
        featureToUpdate.xyz()?.tags =
            TagsProxy(SINGLE_FEATURE_INITIAL_TAG, SINGLE_FEATURE_REPLACEMENT_TAG)

        val writeFeatures = WriteRequest(arrayOf(WriteFeature(collectionId, featureToUpdate)))
        // when
        try {
            val nakResponse = session.execute(writeFeatures)
            val successResponse = nakResponse as SuccessResponse
            // then
            val row1 = successResponse.rows[0]
            val feature = row1.getFeature()!!
            assertSame(XYZ_EXEC_UPDATED, row1.op)
            assertEquals(SINGLE_FEATURE_ID, feature.id)
            val geometry = feature.geometry?.asPointProxy()
            assertEquals(xyzGeometry, geometry)
            assertEquals(
                listOf(SINGLE_FEATURE_INITIAL_TAG, SINGLE_FEATURE_REPLACEMENT_TAG),
                feature.xyz()?.tags
            )
        } finally {
            session.commit()
        }
    }

    @Test
    @Order(55)
    @EnabledIf("runTest")
    fun singleFeatureUpdate() {
        // given
        /**
         * data inserted in [.singleFeatureCreate] test
         */
        val featureToUpdate = NakshaFeatureProxy(SINGLE_FEATURE_ID)
        // different geometry
        val newPoint1 = PointCoordsProxy(5.1, 6.0, 2.1)
        val newPoint2 = PointCoordsProxy(5.15, 6.0, 2.15)
        val lineString = LineStringProxy(newPoint1, newPoint2)

        featureToUpdate.geometry = lineString
        // This tag should replace the previous one!
        val xyz = XyzProxy()
        xyz.tags = TagsProxy(SINGLE_FEATURE_REPLACEMENT_TAG)
        featureToUpdate.nakshaProperties().xyz = xyz
        val request = WriteRequest(arrayOf(UpdateFeature(collectionId, featureToUpdate)))
        // when
        try {
            val nakResponse = session.execute(request)
            val successResponse = nakResponse as SuccessResponse
            val row = successResponse.rows[0]

            // then
            val feature = row.getFeature()!!
            assertSame(XYZ_EXEC_UPDATED, row.op)
            assertEquals(SINGLE_FEATURE_ID, row.row?.id)
            //      assertNotNull(cursor.getPropertiesType());
            val respGeometry = row.getFeature()!!.geometry
            assertEquals(lineString, respGeometry)
            assertEquals(
                listOf(SINGLE_FEATURE_REPLACEMENT_TAG),
                feature.xyz()?.tags
            )
        } finally {
            session.commit()
        }
    }

    @Test
    @Order(56)
    @EnabledIf("runTest")
    fun singleFeatureUpdateVerify() {


        // given
        /**
         * data inserted in [.singleFeatureCreate] test and updated by [.singleFeatureUpdate].
         */
        val request =
            ReadFeatures.readFeaturesByIdRequest(collectionId, SINGLE_FEATURE_ID)

        // when
        val nakResponse = session.execute(request)
        val successResponse = nakResponse as SuccessResponse
        val row = successResponse.rows[0]
        val feature = row.getFeature()!!
        val xyz = feature.xyz()

        // then
        assertEquals(SINGLE_FEATURE_ID, feature.id)
        assertEquals(3, xyz?.version)
        assertSame(XYZ_EXEC_UPDATED, xyz?.action)
        val geometry = feature.geometry
        assertNotNull(geometry)
        val expectedGeometry = PointProxy(5.15, 6.0, 2.15)
        assertEquals(expectedGeometry, geometry?.asPointProxy())

        val guid: Guid = row.row?.guid!!
        val now = LocalDate.now()
        assertEquals(storage.id(), guid.storageId)
        assertEquals(collectionId, guid.collectionId)
        assertEquals(now.year, guid.luid.txn.year()) // year (4- digits)
        assertEquals(now.month, guid.luid.txn.month()) // month (1 or 2 digits)
        assertEquals(now.dayOfMonth, guid.luid.txn.day()) // day (1 or 2 digits)
        // Note: the txn seq should be 4 as:
        // 1 - used for create internal collections
        // 2 - used for create collection
        // 3 - used for insert feature
        // 4 - used for upsert
        // 5 - used for update
        assertEquals("5", guid.luid.uid)
        // Note: for each new txn_seq we reset uid to 0
        assertEquals("0", guid.luid.txn.seq())
        // Note: We know that if the schema was dropped, the transaction number is reset to 0.
        // - Create the collection in parent PsqlTest (0) <- commit
        // - Create the single feature (1) <- commit
        // - Upsert the single feature (2) <- commit
        // - Update the single feature (3) <- commit

        assertEquals(context.appId, xyz?.appId)
        assertEquals(context.author, xyz?.author)

        val centroid: Point = toJtsGeometry(geometry!!).getCentroid()
        assertEquals(encodeLatLon(centroid.getY(), centroid.getX(), 14), xyz?.get("grid"))
        assertEquals(0, xyz?.geoGrid)
        assertEquals(1, nakResponse.size())
    }

    @Test
    @Order(56)
    @EnabledIf("runTest")
    fun singleFeatureGetAllVersions() {


        // given
        /**
         * data inserted in [.singleFeatureCreate] test and updated by [.singleFeatureUpdate].
         */
        val request =
            ReadFeatures.readFeaturesByIdRequest(collectionId, SINGLE_FEATURE_ID, limitVersions = 999)

        // when
        val nakResponse = session.execute(request) as SuccessResponse
        val rows = nakResponse.rows
        val ver1 = rows[0].row!!
        val ver2 = rows[1].row!!
        val ver3 = rows[2].row!!
        assertEquals(3, nakResponse.size())
        assertEquals(ver1.id, ver2.id)
        assertEquals(ver1.id, ver3.id)
        assertNotEquals(ver1.meta!!.txn, ver2.meta!!.txn)
    }

    @Test
    @Order(56)
    @EnabledIf("runTest")
    fun singleFeatureGetSpecificVersionRead() {
        // given
        /**
         * data inserted in [.singleFeatureCreate] test and updated by [.singleFeatureUpdate].
         */
        val requestAll =
            ReadFeatures.readFeaturesByIdRequest(collectionId, SINGLE_FEATURE_ID, limitVersions = 999)

        // when
        val nakSuccessResponse = session.execute(requestAll) as SuccessResponse
        val txnOfMiddleVersion = nakSuccessResponse.rows[2].row!!.meta!!.txn


        val requestForTxn = ReadFeatures(
            collectionIds = arrayOf(collectionId),
            op = and(eq(PRef.ID, SINGLE_FEATURE_ID), eq(PRef.TXN, txnOfMiddleVersion)),
            limitVersions = 999,
        )

        // when
        val secondResponse = session.execute(requestForTxn) as SuccessResponse
        val meta = secondResponse.rows[0].row!!.meta

        val puuid: String = Guid(
            storage.id(),
            collectionId,
            SINGLE_FEATURE_ID,
            Luid(Txn(meta!!.ptxn!!), meta.puid!!)
        ).toString()

        assertEquals(txnOfMiddleVersion, meta.txn)
        assertEquals(1, secondResponse.size())

        // get previous version by uuid = puuid
        val requestForPreviousVersion =
            ReadFeatures.readFeaturesByIdRequest(collectionId, SINGLE_FEATURE_ID)
        val response = session.execute(requestForPreviousVersion) as SuccessResponse

        val xyzNamespace = response.rows.first().getFeature()!!.properties.proxy(XyzProxy::class)
        assertEquals(puuid, xyzNamespace.uuid)
        assertTrue(txnOfMiddleVersion > response.rows.first().row!!.meta!!.txn)

    }

    @Test
    @Order(57)
    @EnabledIf("runTest")
    fun singleFeaturePutWithSameId() {


        val feature = NakshaFeatureProxy((SINGLE_FEATURE_ID))
        val geometry = PointProxy(5.0, 6.0, 2.0)
        feature.geometry = geometry
        val request = WriteRequest(arrayOf(WriteFeature(collectionId, feature)))
        try {
            val response = session.execute(request) as SuccessResponse
            // should change to operation update as row already exists.
            assertSame(XYZ_EXEC_UPDATED, response.rows[0].op)
        } finally {
            session.commit()
        }
    }

    @Test
    @Order(60)
    @EnabledIf("runTest")
    fun testDuplicateFeatureId() {

        // given
        val feature = NakshaFeatureProxy(SINGLE_FEATURE_ID)
        feature.geometry = PointProxy(0.0, 0.0, 0.0)
        val request = WriteRequest(arrayOf(InsertFeature(collectionId, feature)))
        // when
        val result = session.execute(request)

        // then
        assertInstanceOf(ErrorResponse::class.java, result)
        val errorResult = result as ErrorResponse
        assertEquals("CONFLICT", errorResult.reason.error)
        assertTrue(errorResult.reason.message.startsWith("ERROR: duplicate key value violates unique"))
        session.commit()

        // make sure feature hasn't been updated (has old geometry).
        val readRequest = ReadFeatures.readFeaturesByIdRequest(collectionId, SINGLE_FEATURE_ID)
        val successResponse = session.execute(readRequest) as SuccessResponse
        assertEquals(
            PointProxy(5.0, 6.0, 2.0),
            successResponse.rows.first().getFeature()?.geometry
        )
    }

    @Test
    @Order(61)
    @EnabledIf("runTest")
    fun testMultiOperationPartialFailCausesOverallFailure() {
        // given
        val featureToSucceed = NakshaFeatureProxy("123")
        val featureToFail = NakshaFeatureProxy(SINGLE_FEATURE_ID)
        val request = WriteRequest(
            arrayOf(
                InsertFeature(collectionId, featureToSucceed),
                InsertFeature(collectionId, featureToFail)
            )
        )

        // when
        val result = session.execute(request)

        // then
        assertInstanceOf(ErrorResponse::class.java, result)
        val errorResult = result as ErrorResponse
        assertEquals("CONFLICT", errorResult.reason.error)
        assertTrue(errorResult.reason.message.startsWith("ERROR: duplicate key value violates unique"))

        // we don't have detailed information, we can work only in mode: all or nothing.
        session.commit()
        // verify if other feature has not been stored
        val readFeature = ReadFeatures.readFeaturesByIdRequest(collectionId, featureToSucceed.id)
        val res = session.execute(readFeature) as SuccessResponse
        assertTrue(res.rows.isEmpty())
    }

    @Test
    @Order(62)
    @EnabledIf("runTest")
    fun testInvalidUuid() {


        // given
        val deleteOp = DeleteFeature(collectionId, SINGLE_FEATURE_ID, "invalid_UUID")
        val deleteReq = WriteRequest(arrayOf(deleteOp))
        // when
        try {
            val result = session.execute(deleteReq)
            // then
            assertInstanceOf(ErrorResponse::class.java, result)
            val errorResult = result as ErrorResponse
            assertEquals("NX000", errorResult.reason.error)
            assertTrue(errorResult.reason.message.contains("invalid naksha uuid invalid_UUID"))
        } finally {
            session.commit()
        }
    }

    @Test
    @Order(64)
    @EnabledIf("runTest")
    fun singleFeatureDeleteById() {
        val feature = NakshaFeatureProxy("TO_DEL_BY_ID")
        val request = WriteRequest(arrayOf(InsertFeature(collectionId, feature)))

        // when
        session.execute(request)
        session.commit()

        val deleteOp = DeleteFeature(collectionId, "TO_DEL_BY_ID", null)
        val deleteReq = WriteRequest(arrayOf(deleteOp))
        try {
            val nakResponse = session.execute(deleteReq) as SuccessResponse
            val row = nakResponse.rows[0]
            assertSame(XYZ_EXEC_DELETED, row.op)
            assertEquals("TO_DEL_BY_ID", row.row?.id)
            val xyzNamespace = row.getFeature()!!.xyz()
            assertNotEquals(xyzNamespace?.createdAt, xyzNamespace!!.updatedAt)
            assertEquals(XYZ_EXEC_DELETED, xyzNamespace.action)
            assertEquals(2, xyzNamespace.version)
            assertEquals(1, nakResponse.rows.size)
        } finally {
            session.commit()
        }

        // verify if hst contains 2 versions
        val read = ReadFeatures.readFeaturesByIdRequest(collectionId, "TO_DEL_BY_ID", limitVersions = 999)
        val response = (session.execute(read) as SuccessResponse)
        assertEquals(ACTION_CREATE.toShort(), response.rows.first().row?.meta?.action)
        assertEquals(ACTION_DELETE.toShort(), response.rows[1].row?.meta?.action)
    }

    @Test
    @Order(65)
    @EnabledIf("runTest")
    fun singleFeatureDeleteVerify() {
        // when
        /**
         * Read from feature should return nothing.
         */
        val request = ReadFeatures.readFeaturesByIdRequest(collectionId, SINGLE_FEATURE_ID)
        val response = session.execute(request) as SuccessResponse
        assertEquals(0, response.rows.size)
        var rs = getFeatureFromTable(collectionId, SINGLE_FEATURE_ID)
        assertFalse(rs.next())
        /**
         * Read from deleted should return valid feature.
         */
        val requestWithDeleted =
            ReadFeatures.readFeaturesByIdRequest(collectionId, SINGLE_FEATURE_ID, queryDeleted = true)
        var featureJsonBeforeDeletion: String

        /* TODO uncomment it when read with deleted is ready.

    try (final ResultCursor<XyzFeature> cursor =
    session.execute(requestWithDeleted).cursor()) {
    cursor.next();
    final XyzFeature feature = cursor.getFeature()!!;
    XyzNamespace xyz = feature.xyz();

    // then
    assertSame(EExecutedOp.DELETED, cursor.op);
    final String id = cursor .id;
    assertEquals(SINGLE_FEATURE_ID, id);
    final String uuid = cursor.row?.guid;
    assertNotNull(uuid);
    final Geometry geometry = cursor.geometry;
    assertNotNull(geometry);
    assertEquals(new Coordinate(5.1d, 6.0d, 2.1d), geometry.getCoordinate());
    assertNotNull(feature);
    assertEquals(SINGLE_FEATURE_ID, feature .id);
    assertEquals(uuid, feature.xyz().row?.guid);
    assertSame(EXyzAction.DELETE, feature.xyz()?.action);
    featureJsonBeforeDeletion = cursor.getJson()
    assertFalse(cursor.next());
    }
    */
        /**
         * Check directly $del table.
         */
        val collectionDelTableName = "$collectionId\$del"
        rs = getFeatureFromTable(collectionDelTableName, SINGLE_FEATURE_ID)
        // feature exists in $del table
        assertTrue(rs.next())
    }

    @Test
    @Order(66)
    @EnabledIf("runTest")
    fun singleFeaturePurge() {


        // given
        /**
         * Data inserted in [.singleFeatureCreate] and deleted in [.singleFeatureDelete].
         * We don't care about geometry or other properties during PURGE operation, feature_id is only required thing,
         * thanks to that you don't have to read feature before purge operation.
         */
        val purgeOp = PurgeFeature(collectionId, SINGLE_FEATURE_ID, null)
        val request = WriteRequest(arrayOf(purgeOp))

        // when
        try {
            val response = session.execute(request) as SuccessResponse
            val row = response.rows[0]

            // then
            assertSame(XYZ_EXEC_PURGED, row.op)
            assertEquals(SINGLE_FEATURE_ID, row.row?.id)
        } finally {
            session.commit()
        }
    }

    @Test
    @Order(67)
    @EnabledIf("runTest")
    fun singleFeaturePurgeVerify() {
        // given
        val collectionDelTableName = "$collectionId\$del"

        val rs = getFeatureFromTable(collectionDelTableName, SINGLE_FEATURE_ID)
        // then
        assertFalse(rs.next())
    }

    @Test
    @Order(67)
    @EnabledIf("runTest")
    fun autoPurgeCheck() {
        // given
        val collectionWithAutoPurge: String = collectionId + "_ap"
        val collection =
            NakshaCollectionProxy(collectionWithAutoPurge, partitionCount(), autoPurge = true, disableHistory = false)
        val request = WriteRequest(arrayOf(InsertFeature(NKC_TABLE, collection)))

        // when
        try {
            val nakResponse = session.execute(request)
            assertInstanceOf(SuccessResponse::class.java, nakResponse)
            val successResponse = nakResponse as SuccessResponse
            val respCol = successResponse.rows[0].getFeature()!!.proxy(NakshaCollectionProxy::class)
            assertTrue(respCol.autoPurge)
        } finally {
            session.commit()
        }

        // CREATE feature
        val featureToDel = NakshaFeatureProxy(SINGLE_FEATURE_ID)
        val requestFeature = WriteRequest(
            arrayOf(InsertFeature(collectionWithAutoPurge, featureToDel))
        )
        try {
            val nakResponse = session.execute(requestFeature)
            assertInstanceOf(SuccessResponse::class.java, nakResponse)
        } finally {
            session.commit()
        }

        // DELETE feature
        val deleteOp = DeleteFeature(collectionId, "TO_DEL_BY_ID", null)
        val deleteReq = WriteRequest(arrayOf(deleteOp))
        try {
            val nakResponse = session.execute(deleteReq)
            assertInstanceOf(SuccessResponse::class.java, nakResponse)
        } finally {
            session.commit()
        }

        var rs = getFeatureFromTable("$collectionWithAutoPurge\$del", SINGLE_FEATURE_ID)
        // then
        assertFalse(rs.next())
        rs = getFeatureFromTable("$collectionWithAutoPurge\$hst", SINGLE_FEATURE_ID)
        // then
        assertTrue(rs.next())
    }

    @Test
    @Order(70)
    @EnabledIf("runTest")
    fun multipleFeaturesInsert() {

        var i = 0
        var firstNameAdded = false
        val size = 1000
        val features = mutableListOf<Write>()
        while (i < size || !firstNameAdded) {
            val feature = fg.newRandomFeature()
            if (!firstNameAdded) {
                firstNameAdded = fg.firstNames[0] == feature.properties["firstName"]
            }
            features.add(WriteFeature(collectionId, feature))
            i++
        }
        val reqWrite = WriteRequest(features.toTypedArray())

        try {
            val nakResponse = session.execute(reqWrite) as SuccessResponse
            for (row in nakResponse.rows) {
                val op: String = row.op
                assertSame(XYZ_EXEC_CREATED, op)
                val id: String = row.row!!.id
                assertNotNull(id)
                assertNotNull(row.row?.guid)
                val geometry = row.getFeature()!!.geometry
                assertNotNull(geometry)
                val f = row.getFeature()!!
                assertNotNull(f)
                assertEquals(id, f.id)
                assertSame(XYZ_EXEC_CREATED, f.xyz()?.action)
            }
        } finally {
            session.commit()
        }
    }

    @Test
    @Order(71)
    @EnabledIf("runTest")
    fun multipleFeaturesRead() {


        val request = ReadFeatures(
            collectionIds = arrayOf(collectionId),
            op = LOp.or(
                contains(PRef.TAGS, "@:firstName:" + fg.firstNames[0]),
                contains(PRef.TAGS, "@:firstName:" + fg.firstNames[1])
            )
        )

        try {
            val response = session.execute(request) as SuccessResponse
            // We expect that at least one feature was found!
            for (row in response.rows) {
                assertSame(XYZ_EXEC_READ, row.op)
                val id: String = row.row!!.id
                assertNotNull(id)
                val uuid: String = row.row!!.meta!!.getLuid().toString()
                assertNotNull(uuid)
                assertNotNull(row.row?.geo)
                val f = row.getFeature()!!
                assertNotNull(f)
                assertEquals(id, f.id)
                assertEquals(uuid, f.xyz()?.uuid)
                assertSame(XYZ_EXEC_CREATED, f.xyz()?.action)
                val tags = f.xyz()?.tags
                assertNotNull(tags)
                assertTrue(tags!!.size > 0)
                assertTrue(
                    tags.contains("@:firstName:" + fg.firstNames[0])
                            || tags.contains("@:firstName:" + fg.firstNames[1])
                )
            }
        } finally {
            session.commit()
        }
    }

    @Test
    @Order(73)
    @EnabledIf("runTest")
    fun testRestoreOrder() {
        // given
        val feature1 = NakshaFeatureProxy("123")
        val feature2 = NakshaFeatureProxy("121")
        val request = WriteRequest(
            arrayOf(
                InsertFeature(collectionId, feature1),
                InsertFeature(collectionId, feature2)
            ),
            noResults = false,
            allowRandomOrder = false,
            noFeature = false,
            noGeometry = false,
            noMeta = false,
            noTags = true,
            resultFilter = arrayOf()
        )

        try {
            // when
            val result = session.execute(request) as SuccessResponse

            // then
            val rows = result.rows
            assertEquals("123", rows[0].row?.id)
            assertEquals("121", rows[1].row?.id)
        } finally {
            session.commit()
        }
    }

    @Test
    @Order(74)
    @EnabledIf("runTest")
    fun limitedRead() {
        limitToN(1)
        limitToN(2)
    }


    private fun limitToN(limit: Int) {
        val request = ReadFeatures(collectionIds = arrayOf(collectionId), limit = limit)
        val response = session.execute(request) as SuccessResponse
        assertEquals(limit, response.rows.size)
    }

    @Test
    @Order(110)
    @EnabledIf("runTest")
    fun readCollections() {

        val request = ReadCollections(ids = arrayOf(collectionId))
        val response = session.execute(request) as SuccessResponse

        assertEquals(collectionId, response.rows.first().row?.id)
    }

    @Test
    @Order(111)
    @EnabledIf("runTest")
    fun intersectionSearch() {
        val feature = NakshaFeatureProxy("otherFeature")
        val geometry = LineStringProxy(
            PointCoordsProxy(4.0, 5.0),
            PointCoordsProxy(4.0, 6.0)
        )
        feature.geometry = geometry
        val request = WriteRequest(
            arrayOf(InsertFeature(collectionId, feature))
        )
        try {
            val nakResponse = session.execute(request)
            assertInstanceOf(SuccessResponse::class.java, nakResponse)
        } finally {
            session.commit()
        }

        // read by bbox that surrounds only first point
        val envelopeBbox = createBBoxEnvelope(3.9, 4.9, 4.1, 5.1)
        val readFeatures = ReadFeatures(collectionIds = arrayOf(collectionId), spatialOp = SOp.intersects(envelopeBbox))

        val response = session.execute(readFeatures) as SuccessResponse
        // then
        assertEquals("otherFeature", response.rows.first().getFeature()!!.id)
        assertEquals(1, response.rows.size)
    }


    @Test
    @Order(112)
    @EnabledIf("runTest")
    fun notIndexedPropertyRead() {

        // given
        val jsonReference =
            "{\"id\":\"32167\",\"properties\":{\"weight\":60,\"length\":null,\"color\":\"red\",\"ids\":[0,1,9],\"ids2\":[\"a\",\"b\",\"c\"],\"subJson\":{\"b\":1},\"references\":[{\"id\":\"urn:here::here:Topology:106003684\",\"type\":\"Topology\",\"prop\":{\"a\":1}}]}}"
        val reader: ObjectReader = ObjectMapper().reader()

        val featurePlatform = (Platform.fromJSON(jsonReference) as PlatformMap).proxy(NakshaFeatureProxy::class)

        session.execute(WriteRequest(arrayOf(WriteFeature(collectionId, featurePlatform))))
        session.commit()

        val expect = { readFeaturesReq: ReadRequest ->
            val res = session.execute(readFeaturesReq) as SuccessResponse
            assertEquals("32167", res.rows.first().row?.id)
            assertEquals(1, res.rows.size)
        }

        // when - search for int value
        val weightSearch = eq(NON_INDEXED_PREF("properties", "weight"), 60)
        // then
        expect(readIdsBy(collectionId, weightSearch))

        // when - search 'not'
        val notSearch = not(eq(NON_INDEXED_PREF("properties", "weight"), 59))
        // then
        expect(readIdsBy(collectionId, notSearch))

        // when - search 'exists'
        val existsSearch = exists(NON_INDEXED_PREF("properties", "weight"))
        // then
        expect(readIdsBy(collectionId, existsSearch))

        // when - search 'not exists'
        val notExistsSearch = and(not(exists(NON_INDEXED_PREF("properties", "weight2"))), eq(PRef.ID, "32167"))
        // then
        expect(readIdsBy(collectionId, notExistsSearch))

        // when - search not null value
        val notNullSearch = POp.isNotNull(NON_INDEXED_PREF("properties", "color"))
        // then
        expect(readIdsBy(collectionId, notNullSearch))

        // when - search null value
        val nullSearch = POp.isNull(NON_INDEXED_PREF("properties", "length"))
        // then
        expect(readIdsBy(collectionId, nullSearch))

        // when - search array contains
        val arraySearch = contains(NON_INDEXED_PREF("properties", "ids"), 9)
        // then
        expect(readIdsBy(collectionId, arraySearch))

        // when - search array contains string
        val arrayStringSearch = contains(NON_INDEXED_PREF("properties", "ids2"), "a")
        // then
        expect(readIdsBy(collectionId, arrayStringSearch))

        // when - search by json object
        val jsonSearch2 = contains(
            NON_INDEXED_PREF("properties", "references"),
            reader.readValue("[{\"id\":\"urn:here::here:Topology:106003684\"}]", ArrayList::class.java)
        )
        // then
        expect(readIdsBy(collectionId, jsonSearch2))

        // when - search by json object
        val jsonSearch3 = contains(
            NON_INDEXED_PREF("properties", "references"),
            reader.readValue("[{\"prop\":{\"a\":1}}]", JsonNode::class.java)
        )
        // then
        expect(readIdsBy(collectionId, jsonSearch3))

        // when - search by json object
        val jsonSearch4 =
            contains(NON_INDEXED_PREF("properties", "subJson"), reader.readValue("{\"b\":1}", JsonNode::class.java))
        // then
        expect(readIdsBy(collectionId, jsonSearch4))
    }


    @Test
    @Order(120)
    @EnabledIf("runTest")
    fun dropFooCollection() {

        val deleteCollection = DeleteFeature(collectionId = NKC_TABLE, id = collectionId)
        val nakResponse = session.execute(WriteRequest(arrayOf(deleteCollection)))
        assertInstanceOf(SuccessResponse::class.java, nakResponse)
        session.commit()

        // try readSession after purge, table doesn't exist anymore, so it should throw an exception.
        assertThrowsExactly(
            PSQLException::class.java,
            { getFeatureFromTable(collectionId, SINGLE_FEATURE_ID) },
            "ERROR: relation \"foo\" does not exist"
        )
    }

    private fun getFeatureFromTable(table: String, featureId: String): ResultSet {
        val stmt = adminConnection.prepareStatement("SELECT * from $table WHERE id = ? ;")
        stmt.setString(1, featureId)
        return stmt.executeQuery()
    }
}
