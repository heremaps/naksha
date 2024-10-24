package naksha.psql

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import naksha.base.Int64
import naksha.base.proxy
import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.base.PgTestBase
import kotlin.test.*

class CollectionTests : PgTestBase(collection = null) {

    @Test
    fun shouldDropCollection() {
        // Given: collection that will be tested
        val collection = NakshaCollection("drop_collection_test")

        // When: creating empty collection
        executeWrite(
            WriteRequest().add(
                Write().createCollection(null, collection)
            )
        )

        // Then: this collection is queryable and empty
        val readAllFromCollection = ReadFeatures().apply { collectionIds += collection.id }
        val collectionContent = executeRead(readAllFromCollection)
        assertEquals(0, collectionContent.features.size)

        // And: Virtual Collections contain the created collection
        val selectCollectionFromVirt = ReadFeatures().apply {
            collectionIds += Naksha.VIRT_COLLECTIONS
            featureIds += collection.id
        }
        val virtBeforeDelete = executeRead(selectCollectionFromVirt)
        assertEquals(1, virtBeforeDelete.features.size)

        // When: Collection gets deleted
        executeWrite(
            WriteRequest().add(
                Write().deleteCollectionById(null, collectionId = collection.id)
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
    fun collectionShouldHasAllDbColumns() {
        val collection = NakshaCollection("check_db_columns_test")
        executeWrite(
            WriteRequest().add(
                Write().createCollection(null, collection)
            )
        )
        val cursor = useConnection().execute(
            sql = """ SELECT column_name
                    FROM information_schema.columns
                    WHERE table_name = $1
            """.trimIndent(),
            args = arrayOf(collection.id)
        )
        val columns = mutableListOf<String>()
        while (cursor.next()) {
            columns.add(cursor["column_name"])
        }
        assertEquals(PgColumn.allColumns.size, columns.size)
        assertTrue(PgColumn.allColumns.all { column -> columns.contains(column.name) })
        cursor.close()
    }

    @Test
    fun collectionShouldHasAllDbIndices() {
        val collection = NakshaCollection("check_db_indices_test")
        executeWrite(
            WriteRequest().add(
                Write().createCollection(null, collection)
            )
        )
        val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        checkAllDefaultIndicesCreatedForTable(collection.id)
        checkAllDefaultIndicesCreatedForTable("${collection.id}\$meta")
        checkAllDefaultIndicesCreatedForTable("${collection.id}\$del")
        checkAllDefaultIndicesCreatedForTable("${collection.id}\$hst\$y$currentYear")
        checkAllDefaultIndicesCreatedForTable("${collection.id}\$hst\$y${currentYear + 1}")
        checkAllDefaultIndicesCreatedForTable("${collection.id}\$meta")
    }

    private fun checkAllDefaultIndicesCreatedForTable(tableName: String) {
        val cursor = useConnection().execute(
            sql = """ SELECT indexname
                    FROM pg_indexes
                    WHERE tablename = $1;
            """.trimIndent(),
            args = arrayOf(tableName)
        )
        val indices = mutableListOf<String>()
        while (cursor.next()) {
            indices.add(cursor["indexname"])
        }
        assertTrue(PgIndex.DEFAULT_INDICES.size <= indices.size)
        assertTrue(PgIndex.DEFAULT_INDICES.all { index -> indices.any { addedIndex -> addedIndex.contains(index)}})
        cursor.close()
    }

    @Test
    fun collectionShouldHasNoHistoryDBTable() {
        val collectionName = "check_no_hst_table_test"
        val collection = NakshaCollection(
            id = collectionName,
            storeHistory = StoreMode.OFF
        )
        executeWrite(
            WriteRequest().add(
                Write().createCollection(null, collection)
            )
        )
        val hstTableName = "$collectionName\$hst"
        val cursor = useConnection().execute(
            sql = """ SELECT EXISTS (
                    SELECT FROM information_schema.tables 
                    WHERE table_name = $1 
                    )
            """.trimIndent(),
            args = arrayOf(hstTableName)
        )
        // Check that hst table was not created
        assertFalse(cursor.fetch()["exists"])
        cursor.close()
        // Check that creating, updating and deleting features still work
        val feature = NakshaFeature()
        val readFeature = ReadFeatures()
        readFeature.collectionIds.add(collectionName)
        readFeature.featureIds.add(feature.id)
        executeWrite(
            WriteRequest().add(
                Write().createFeature(null, collectionName,feature)
            )
        )
        val insertedFeatureResponse = executeRead(readFeature)
        assertEquals(1,insertedFeatureResponse.features.size)
        feature.properties["foo"] = "bar"
        executeWrite(
            WriteRequest().add(
                Write().updateFeature(null, collectionName,feature)
            )
        )
        val updatedFeatureResponse = executeRead(readFeature)
        assertEquals("bar", updatedFeatureResponse.features[0]?.properties!!["foo"])
        executeWrite(
            WriteRequest().add(
                Write().deleteFeatureById(null, collectionName,feature.id)
            )
        )
        val deletedFeatureResponse = executeRead(readFeature)
        assertEquals(0, deletedFeatureResponse.features.size)
    }

    @Test
    fun collectionShouldHasNoDeleteDBTable() {
        val collectionName = "check_no_del_table_test"
        val collection = NakshaCollection(
            id = collectionName,
            storeDeleted = StoreMode.OFF
            )
        executeWrite(
            WriteRequest().add(
                Write().createCollection(null, collection)
            )
        )
        val delTableName = "$collectionName\$del"
        val cursor = useConnection().execute(
            sql = """ SELECT EXISTS (
                    SELECT FROM information_schema.tables 
                    WHERE table_name = $1 
                    )
            """.trimIndent(),
            args = arrayOf(delTableName)
        )
        // Check that del table was not created
        assertFalse(cursor.fetch()["exists"])
        cursor.close()
        // Check that creating, updating and deleting features still work
        val feature = NakshaFeature()
        val readFeature = ReadFeatures()
        readFeature.collectionIds.add(collectionName)
        readFeature.featureIds.add(feature.id)
        executeWrite(
            WriteRequest().add(
                Write().createFeature(null, collectionName,feature)
            )
        )
        val insertedFeatureResponse = executeRead(readFeature)
        assertEquals(1,insertedFeatureResponse.features.size)
        feature.properties["foo"] = "bar"
        executeWrite(
            WriteRequest().add(
                Write().updateFeature(null, collectionName,feature)
            )
        )
        val updatedFeatureResponse = executeRead(readFeature)
        assertEquals("bar", updatedFeatureResponse.features[0]?.properties!!["foo"])
        executeWrite(
            WriteRequest().add(
                Write().deleteFeatureById(null, collectionName,feature.id)
            )
        )
        val deletedFeatureResponse = executeRead(readFeature)
        assertEquals(0, deletedFeatureResponse.features.size)
    }

    @Test
    fun updateCollection() {
        val collectionName = "update_collection_test"
        val collection = NakshaCollection(id = collectionName)
        executeWrite(
            WriteRequest().add(
                Write().createCollection(null, collection)
            )
        )
        // update collection
        collection.storeDeleted = StoreMode.SUSPEND
        val response = executeWrite(
            WriteRequest().add(
                Write().updateCollection(null, collection)
            )
        )
        val responseCollection = response.features[0]!!.proxy(NakshaCollection::class)
        assertEquals(StoreMode.SUSPEND, responseCollection.storeDeleted)
        val selectCollectionFromVirt = ReadFeatures().apply {
            collectionIds += Naksha.VIRT_COLLECTIONS
            featureIds += collection.id
        }
        val colRead = executeRead(selectCollectionFromVirt).features[0]!!.proxy(NakshaCollection::class)
        assertEquals(StoreMode.SUSPEND, colRead.storeDeleted)
    }
}