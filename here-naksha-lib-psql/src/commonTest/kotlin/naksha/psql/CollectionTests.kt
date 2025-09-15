package naksha.psql

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import naksha.base.StringList
import naksha.model.Naksha
import naksha.model.NakshaError
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaCollection.NakshaCollection_C.APP_ID_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.AUTHOR_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.CS0_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.CS1_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.CS2_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.CS3_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.CV0_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.CV1_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.CV2_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.CV3_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.FEATURE_TYPE_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.GIST_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.HERE_TILE_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.ID_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.REF_POINT_IDX
import naksha.model.objects.NakshaCollection.NakshaCollection_C.TAGS_IDX
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.request.ErrorResponse
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.*

class CollectionTests : PgTestBase(collection = null, mapId = "") {

    @Test
    fun shouldDropCollection() {
        // Given: collection that will be tested
        val collection = NakshaCollection("drop_collection_test", map.id)

        // When: creating empty collection
        executeWrite(
            WriteRequest().add(
                Write().createCollection(collection)
            )
        )

        // Then: this collection is queryable and empty
        val readAllFromCollection = ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
        }
        val collectionContent = executeRead(readAllFromCollection)
        assertEquals(0, collectionContent.features.size)

        // And: Virtual Collections contain the created collection
        val selectCollectionFromVirt = ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += Naksha.COLLECTIONS_COL
            featureIds += collection.id
        }
        val virtBeforeDelete = executeRead(selectCollectionFromVirt)
        assertEquals(1, virtBeforeDelete.features.size)

        // When: Collection gets deleted
        executeWrite(
            WriteRequest().add(
                Write().deleteCollectionById(collection.mapId, collection.id)
            )
        )

        // Then: it is not present in Virtual Collections anymore
        val virtAfterDelete = executeRead(selectCollectionFromVirt)
        assertEquals(0, virtAfterDelete.features.size)

        // And: reading from this collection fails
        assertFails("ERROR: relation \"${collection.id}\" does not exist") {
            executeRead(readAllFromCollection)
        }
    }

    @Test
    fun collectionShouldHaveAllColumns() {
        val collection = NakshaCollection("check_db_columns_test", map.id)
        executeWrite(
            WriteRequest().add(
                Write().createCollection(collection)
            )
        )
        storage.adminConnection().use { conn ->
            val columns = mutableListOf<String>()
            conn.execute(
                sql = "SELECT column_name FROM information_schema.columns WHERE table_name = $1",
                args = arrayOf(collection.id)
            ).use { cursor ->
                while (cursor.next()) columns.add(cursor["column_name"])
                assertEquals(PgColumn.allColumns.size, columns.size)
                assertTrue(PgColumn.allColumns.all { column -> columns.contains(column.name) })
            }
        }
    }

    @Test
    fun collectionShouldHaveIndices() {
        val collection = NakshaCollection("check_db_indices_test", map.id)
        val indices = StringList(
            ID_IDX,
            HERE_TILE_IDX,
            APP_ID_IDX,
            AUTHOR_IDX,
            TAGS_IDX,
            REF_POINT_IDX,
            GIST_IDX,
            FEATURE_TYPE_IDX,
            CV0_IDX,
            CV1_IDX,
            CV2_IDX,
            CV3_IDX,
            CS0_IDX,
            CS1_IDX,
            CS2_IDX,
            CS3_IDX,
        )
        collection.withIndices(indices)
        executeWrite(
            WriteRequest().add(
                Write().createCollection(collection)
            )
        )
        val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        checkIndicesCreatedForTable(collection.id, indices)
        checkIndicesCreatedForTable("${collection.id}\$meta", indices)
        checkIndicesCreatedForTable("${collection.id}\$del", indices)
        checkIndicesCreatedForTable("${collection.id}\$hst\$y$currentYear", indices)
        checkIndicesCreatedForTable("${collection.id}\$hst\$y${currentYear + 1}", indices)
        checkIndicesCreatedForTable("${collection.id}\$meta", indices)
    }

    private fun checkIndicesCreatedForTable(tableName: String, indices: StringList) {
        storage.adminConnection().use { conn ->
            conn.execute(
                sql = "SELECT indexname FROM pg_indexes WHERE tablename = $1;",
                args = arrayOf(tableName)
            ).use { cursor ->
                val addedIndices = mutableListOf<String>()
                while (cursor.next()) addedIndices.add(cursor["indexname"])
                check(indices.size <= addedIndices.size) { "Too few indices" }
                indices.forEach { indexName ->
                    check(indexName != null)
                    val pgIndex = PgIndex.of(indexName)
                    check(pgIndex != null) { "pgIndex of $indexName should not be null" }
                    // Note: We know that the `id` index is replaced with `id_unique` internally for HEAD tables!
                    if (pgIndex == PgIndex.id) {
                        check(addedIndices.contains(pgIndex.id(tableName))
                                || addedIndices.contains(PgIndex.id_unique.id(tableName))) {
                            "Missing index ${pgIndex.name} aka $indexName"
                        }
                    } else {
                        check(addedIndices.contains(pgIndex.id(tableName))) {
                            "Missing index ${pgIndex.name} aka $indexName"
                        }
                    }
                }
            }
        }
    }

    @Test
    fun collectionShouldHasNoHistoryDBTable() {
        val collectionName = "check_no_hst_table_test"
        val collection = NakshaCollection(
            id = collectionName,
            mapId = map.id,
            storeHistory = StoreMode.OFF
        )
        executeWrite(
            WriteRequest().add(
                Write().createCollection(collection)
            )
        )
        val hstTableName = "$collectionName\$hst"
        storage.adminConnection().use { conn ->
            conn.execute(
                sql = "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = $1)",
                args = arrayOf(hstTableName)
            ).use { cursor ->
                // Check that hst table was not created
                assertFalse(cursor.fetch()["exists"])
            }
        }

        // Check that creating, updating and deleting features still work
        var feature = NakshaFeature()
        val createFeaturesResponse = executeWrite(
            WriteRequest().add(
                Write().createFeature(collection, feature)
            )
        )
        assertEquals(1, createFeaturesResponse.features.size)
        feature = assertNotNull(createFeaturesResponse.features[0])


        val readFeatureRequest = ReadFeatures()
        readFeatureRequest.mapId = map.id
        readFeatureRequest.collectionIds.add(collectionName)
        readFeatureRequest.featureIds.add(feature.id)
        val readFeaturesResponse = executeRead(readFeatureRequest)
        assertEquals(1, readFeaturesResponse.features.size)
        feature = assertNotNull(readFeaturesResponse.features[0])
        feature.properties["foo"] = "bar"


        val updateResponse = executeWrite(
            WriteRequest().add(
                Write().updateFeature(collection, feature, true)
            )
        )
        assertEquals(1, updateResponse.features.size)
        feature = assertNotNull(updateResponse.features[0])

        // Ensure that the updated feature has the "foo" property
        val readUpdatedFeatureResponse = executeRead(readFeatureRequest)
        assertEquals(1, readUpdatedFeatureResponse.features.size)
        val readFeature = assertNotNull(readUpdatedFeatureResponse.features[0])
        assertEquals("bar", readFeature.properties["foo"])

        // Delete the feature.
        executeWrite(
            WriteRequest().add(
                Write().deleteFeatureById(collection, feature.id)
            )
        )

        // Ensure that it is deleted.
        val deletedFeatureResponse = executeRead(readFeatureRequest)
        assertEquals(0, deletedFeatureResponse.features.size)
    }

    @Test
    fun collectionShouldNotHaveDeleteTable() {
        val collectionId = "check_no_del_table_test"
        var collection = NakshaCollection(
            id = collectionId,
            mapId = map.id,
            storeDeleted = StoreMode.OFF
        )

        // Create the collection and read the response, we need the XYZ namespace!
        val createCollectionResponse = executeWrite(
            WriteRequest().add(
                Write().createCollection(collection)
            )
        )
        assertEquals(1, createCollectionResponse.features.size)
        collection = createCollectionResponse.features[0]!!.proxy(NakshaCollection::class)

        // Proof that del table was not created
        val delTableName = "$collectionId\$del"
        storage.adminConnection().use { conn ->
            val SQL = "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = $1)"
            conn.execute(SQL, arrayOf(delTableName)).use { cursor ->
                assertFalse(cursor.fetch()["exists"])
            }
        }

        // Check that creating, updating and deleting features still work
        var feature = NakshaFeature()
        val featureCreateResponse = executeWrite(
            WriteRequest().add(
                Write().createFeature(collection, feature)
            )
        )
        assertEquals(1, featureCreateResponse.features.size)
        feature = featureCreateResponse.features[0]!!

        val readFeature = ReadFeatures()
        readFeature.mapId = map.id
        readFeature.collectionIds.add(collectionId)
        readFeature.featureIds.add(feature.id)
        val readFeatureResponse = executeRead(readFeature)
        assertEquals(1, readFeatureResponse.features.size)
        // TODO: Deep compare the features, they should be identical!
        feature = featureCreateResponse.features[0]!!

        feature.properties["foo"] = "bar"
        val writeResponse = executeWrite(
            WriteRequest().add(
                Write().updateFeature(collection, feature, true)
            )
        )
        assertEquals(1, writeResponse.features.size)
        Naksha.cache.clear()
        val updatedFeatureResponse = executeRead(readFeature)
        assertEquals("bar", updatedFeatureResponse.features[0]?.properties?.get("foo"))
        executeWrite(
            WriteRequest().add(
                Write().deleteFeatureById(collection, feature.id)
            )
        )
        val deletedFeatureResponse = executeRead(readFeature)
        assertEquals(0, deletedFeatureResponse.features.size)
    }

    @Test
    fun updateCollection() {
        val collectionName = "update_collection_test"
        var collection = NakshaCollection(id = collectionName, mapId = map.id)
        val createResponse = executeWrite(
            WriteRequest().add(
                Write().createCollection(collection)
            )
        )
        assertEquals(1, createResponse.features.size)
        collection = assertNotNull(createResponse.features[0]).proxy(NakshaCollection::class)

        // update collection
        collection.storeDeleted = StoreMode.SUSPEND
        val updateResponse = executeWrite(
            WriteRequest().add(
                Write().updateCollection(collection ,true)
            )
        )
        assertEquals(1, updateResponse.features.size)
        val responseCollection = assertNotNull(updateResponse.features[0]).proxy(NakshaCollection::class)
        assertEquals(StoreMode.SUSPEND, responseCollection.storeDeleted)
        val selectCollectionFromVirt = ReadFeatures().apply {
            mapId = map.id
            collectionIds += Naksha.COLLECTIONS_COL
            featureIds += collection.id
        }
        val colRead = assertNotNull(executeRead(selectCollectionFromVirt).features[0]).proxy(NakshaCollection::class)
        assertEquals(StoreMode.SUSPEND, colRead.storeDeleted)
    }

    @Test
    fun updateNotExistingCollection() {
        val collectionName = "not_existing_collection_test"
        val collection = NakshaCollection(id = collectionName, mapId = map.id)
        // update collection
        collection.storeDeleted = StoreMode.SUSPEND
        val response = executeWriteErrorResponse(
            WriteRequest().add(
                Write().updateCollection(collection, true)
            )
        )
        assertEquals(NakshaError.COLLECTION_NOT_FOUND, response.error.code)
        assertTrue(response.error.msg.contains(collectionName))
    }

    @Test
    fun shouldUpsertCollection() {
        val collectionName = "upsert_collection_test"
        val collection = NakshaCollection(id = collectionName, mapId = map.id)
        // create collection using upsert
        val response = executeWrite(
            WriteRequest().add(
                Write().upsertCollection(collection)
            )
        )
        val createdCollection = response.features[0]!!.proxy(NakshaCollection::class)
        assertEquals(StoreMode.ON, createdCollection.storeDeleted)
        collection.storeDeleted = StoreMode.SUSPEND
        // update collection using upsert
        val updateResponse = executeWrite(
            WriteRequest().add(
                Write().upsertCollection(collection)
            )
        )
        val updatedCollection = updateResponse.features[0]!!.proxy(NakshaCollection::class)
        assertEquals(StoreMode.SUSPEND, updatedCollection.storeDeleted)
    }

    @Test
    fun dropNotExistingCollection() {
        // given
        val collectionName = "not_existing_collection_name"

        // when
        val response = executeWrite(
            WriteRequest().add(
                Write().deleteCollectionById(collectionId = collectionName, mapId = map.id)
            )
        )

        // then
        assertEquals(0, response.length)
        assertEquals(0, response.featureTupleList.size)
        assertEquals(0, response.features.size)
    }

    @Test
    fun testCreateExistingCollection() {
        // Given: collection in db
        val collectionId = "test_create_existing_collection"
        executeWrite(
            WriteRequest().add(
                Write().createCollection(NakshaCollection(collectionId, map.id))
            )
        )

        // When: create same collection once again
        val response = storage.newWriteSession(newSessionOptions()).use { session ->
            session.execute(
                WriteRequest().add(
                    Write().createCollection(NakshaCollection(collectionId, map.id))
                )
            )
        }

        // Then
        assertIs<ErrorResponse>(response)
        assertTrue(response.error.isConflict())
    }
}