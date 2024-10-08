package naksha.psql

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.base.PgTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

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
        checkAllDefaultIndicesCreatedForTable(collection.id+"\$meta")
        checkAllDefaultIndicesCreatedForTable(collection.id+"\$del")
        checkAllDefaultIndicesCreatedForTable(collection.id+"\$hst\$y"+currentYear)
        checkAllDefaultIndicesCreatedForTable(collection.id+"\$hst\$y"+(currentYear+1))
        checkAllDefaultIndicesCreatedForTable(collection.id+"\$meta")
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
}