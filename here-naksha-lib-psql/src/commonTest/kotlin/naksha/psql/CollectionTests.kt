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
import naksha.model.objects.Index
import naksha.model.objects.IndexType
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
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
            catalogId = collection.catalogId
            collectionId += collection.id
        }
        val collectionContent = executeRead(readAllFromCollection)
        assertEquals(0, collectionContent.features.size)

        // And: Virtual Collections contain the created collection
        val selectCollectionFromVirt = ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId += Naksha.COLLECTIONS_COL_ID
            featureIds += collection.id
        }
        val virtBeforeDelete = executeRead(selectCollectionFromVirt)
        assertEquals(1, virtBeforeDelete.features.size)

        // When: Collection gets deleted
        executeWrite(
            WriteRequest().add(
                Write().deleteCollectionById(collection.catalogId, collection.id)
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
                // HEAD has no `next_version` column (intrinsically HEAD); the table should match `headColumns`.
                assertEquals(PgColumn.headColumns.size, columns.size)
                assertTrue(PgColumn.headColumns.all { column -> columns.contains(column.name) })
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
        // The closed-enum opt-in list was removed; PgMap.createPgCollection always applies PgIndex.DEFAULT_INDICES.
        executeWrite(
            WriteRequest().add(
                Write().createCollection(collection)
            )
        )
        val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        checkIndicesCreatedForTable(collection.id, indices)
        checkIndicesCreatedForTable("${collection.id}\$meta", indices)
        // Note: $del table no longer exists; deleted rows are kept in HEAD with (version & 3) == 2.
        checkIndicesCreatedForTable("${collection.id}\$hst\$$currentYear", indices)
        checkIndicesCreatedForTable("${collection.id}\$hst\$${currentYear + 1}", indices)
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
        readFeatureRequest.catalogId = map.id
        readFeatureRequest.collectionId.add(collectionName)
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
        readFeature.catalogId = map.id
        readFeature.collectionId.add(collectionId)
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
            catalogId = map.id
            collectionId += Naksha.COLLECTIONS_COL_ID
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

    // -------------------------------------------------------------------------
    // Members-mode DDL tests
    // -------------------------------------------------------------------------

    /**
     * When [NakshaCollection.members] is **null** (the default / undefined), the collection must be
     * created with all default columns and all default optional indices — backward-compatible behaviour.
     */
    @Test
    fun membersUndefined_shouldCreateAllColumnsAndDefaultIndices() {
        val collection = NakshaCollection("members_null_test", map.id)
        // members is null by default — do NOT set it
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        storage.adminConnection().use { conn ->
            // Columns: must include all headColumns (28 columns, no next_version).
            val columns = mutableListOf<String>()
            conn.execute(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2",
                arrayOf(map.id, collection.id)
            ).use { cursor -> while (cursor.next()) columns.add(cursor["column_name"]) }
            assertEquals(
                PgColumn.headColumns.size, columns.size,
                "Expected all ${PgColumn.headColumns.size} head columns, got: $columns"
            )
            assertTrue(PgColumn.headColumns.all { it.name in columns })

            // Indices: must include all default optional indices on the HEAD table.
            val indexNames = mutableListOf<String>()
            conn.execute(
                "SELECT indexname FROM pg_indexes WHERE schemaname = $1 AND tablename = $2",
                arrayOf(map.id, collection.id)
            ).use { cursor -> while (cursor.next()) indexNames.add(cursor["indexname"]) }
            val defaultIndices = PgIndex.DEFAULT_INDICES.filter { !it.internal }
            for (pgIdx in defaultIndices) {
                val expectedId = pgIdx.id(collection.id)
                assertTrue(expectedId in indexNames || PgIndex.id_unique.id(collection.id) in indexNames,
                    "Expected default index '${pgIdx.name}' (id='$expectedId') to be present, found: $indexNames")
            }
        }
    }

    /**
     * When [NakshaCollection.members] is explicitly an **empty list**, the collection must be
     * created with all standard head columns (full schema) and no default optional indices —
     * only the internal indices (`id_unique`, `version`).
     */
    @Test
    fun membersEmpty_shouldCreateOnlyMandatoryColumnsAndNoDefaultIndices() {
        val collection = NakshaCollection("members_empty_test", map.id).apply {
            members = MemberList() // explicitly empty
        }
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        storage.adminConnection().use { conn ->
            // Columns: must be exactly the full head column set (same as members=null).
            val columns = mutableListOf<String>()
            conn.execute(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2",
                arrayOf(map.id, collection.id)
            ).use { cursor -> while (cursor.next()) columns.add(cursor["column_name"]) }
            assertEquals(
                PgColumn.headColumns.size, columns.size,
                "Expected all ${PgColumn.headColumns.size} head columns, got: $columns"
            )
            assertTrue(PgColumn.headColumns.all { it.name in columns })

            // Indices: no default optional indices must be present.
            val indexNames = mutableListOf<String>()
            conn.execute(
                "SELECT indexname FROM pg_indexes WHERE schemaname = $1 AND tablename = $2",
                arrayOf(map.id, collection.id)
            ).use { cursor -> while (cursor.next()) indexNames.add(cursor["indexname"]) }
            val defaultIndices = PgIndex.DEFAULT_INDICES.filter { !it.internal }
            for (pgIdx in defaultIndices) {
                val expectedId = pgIdx.id(collection.id)
                assertFalse(expectedId in indexNames,
                    "Default index '${pgIdx.name}' (id='$expectedId') should NOT be present, found: $indexNames")
            }
        }
    }

    /**
     * When [NakshaCollection.members] is a **non-empty list**, the collection must be created with
     * all standard head columns plus the declared custom member columns, and only the custom indices
     * declared via [NakshaCollection.indices] — no other default optional indices.
     */
    @Test
    fun membersNonEmpty_shouldCreateMandatoryPlusCustomColumnsAndCustomIndicesOnly() {
        val collection = NakshaCollection("members_custom_test", map.id).apply {
            addMember(Member("score", MemberType.INT64))
            addIndex(Index("idx_score", IndexType.BTREE, "score"))
        }
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        storage.adminConnection().use { conn ->
            // Columns: full head columns + 1 custom column (score).
            val columns = mutableListOf<String>()
            conn.execute(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2",
                arrayOf(map.id, collection.id)
            ).use { cursor -> while (cursor.next()) columns.add(cursor["column_name"]) }
            val expectedCount = PgColumn.headColumns.size + 1 // +1 for score
            assertEquals(expectedCount, columns.size,
                "Expected ${PgColumn.headColumns.size} head columns + 1 custom column, got: $columns")
            assertTrue(PgColumn.headColumns.all { it.name in columns })
            val customColName = PgMemberHelper.pgColumnName("score")
            assertTrue(customColName in columns, "Custom column '$customColName' not found in: $columns")

            // Indices: no default optional indices; only the declared custom index must be present.
            val indexNames = mutableListOf<String>()
            conn.execute(
                "SELECT indexname FROM pg_indexes WHERE schemaname = $1 AND tablename = $2",
                arrayOf(map.id, collection.id)
            ).use { cursor -> while (cursor.next()) indexNames.add(cursor["indexname"]) }
            val defaultIndices = PgIndex.DEFAULT_INDICES.filter { !it.internal }
            for (pgIdx in defaultIndices) {
                val expectedId = pgIdx.id(collection.id)
                assertFalse(expectedId in indexNames,
                    "Default index '${pgIdx.name}' (id='$expectedId') should NOT be present, found: $indexNames")
            }
            // Custom index must be present.
            val customIndexId = "${collection.id}\$ci_idx_score"
            assertTrue(customIndexId in indexNames,
                "Custom index '$customIndexId' not found in: $indexNames")
        }
    }

    /**
     * When a custom [MemberType.TAG_LIST] member is declared with a [IndexType.TAG_LIST] index, the collection
     * must materialize the member as a `jsonb` column and create a GIN index over it.
     */
    @Test
    fun membersSet_shouldCreateJsonbColumnAndGinIndex() {
        val collection = NakshaCollection("members_set_test", map.id).apply {
            addMember(Member("labels", MemberType.TAG_LIST))
            addIndex(Index("idx_labels", IndexType.TAG_LIST, "labels"))
        }
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        storage.adminConnection().use { conn ->
            // Column: materialized as jsonb.
            var dataType: String? = null
            conn.execute(
                "SELECT data_type FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2 AND column_name = $3",
                arrayOf(map.id, collection.id, PgMemberHelper.pgColumnName("labels"))
            ).use { cursor -> if (cursor.next()) dataType = cursor["data_type"] }
            assertEquals("jsonb", dataType, "SET member 'labels' must be materialized as jsonb")

            // Index: GIN index over the jsonb column.
            val customIndexId = "${collection.id}\$ci_idx_labels"
            var indexDef: String? = null
            conn.execute(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = $1 AND tablename = $2 AND indexname = $3",
                arrayOf(map.id, collection.id, customIndexId)
            ).use { cursor -> if (cursor.next()) indexDef = cursor["indexdef"] }
            assertNotNull(indexDef, "SET index '$customIndexId' not found")
            assertTrue(indexDef!!.contains("USING gin", ignoreCase = true),
                "SET index must be a GIN index, got: $indexDef")
        }
    }

    /**
     * A [IndexType.TAG_LIST] index must be rejected when it targets a member that is not a [MemberType.TAG_LIST].
     */
    @Test
    fun membersSet_indexOnNonTagListMemberShouldFail() {
        val collection = NakshaCollection("members_set_invalid_test", map.id).apply {
            addMember(Member("score", MemberType.INT64))
            addIndex(Index("idx_tag_list_score", IndexType.TAG_LIST, "score"))
        }
        executeWriteErrorResponse(WriteRequest().add(Write().createCollection(collection)))
    }

    /**
     * Verifies that when a collection with custom members of every type is created, the physical
     * column order in both the HEAD table and the HISTORY table follows the standard pattern:
     *
     * HEAD:    [standard head columns], <custom members sorted by type>
     * HISTORY: [all standard columns including next_version], <custom members sorted by type>
     *
     * Custom members are sorted: INT64, FLOAT64, INT32, FLOAT32, INT16, INT8, BOOLEAN, STRING, BYTE_ARRAY, FLAT_MAP/TAGS.
     */
    @Test
    fun membersCustom_shouldOrderColumnsForMinimalPadding() {
        val collection = NakshaCollection("members_order_test", map.id).apply {
            // Add one member of every type (deliberately in wrong order to prove sorting works).
            addMember(Member("z_str",   MemberType.STRING))
            addMember(Member("a_i32",   MemberType.INT32))
            addMember(Member("b_i64",   MemberType.INT64))
            addMember(Member("c_f64",   MemberType.FLOAT64))
            addMember(Member("d_bytes", MemberType.BYTE_ARRAY))
            addMember(Member("e_bool",  MemberType.BOOLEAN))
            addMember(Member("f_i8",    MemberType.INT8))
            addMember(Member("g_i16",   MemberType.INT16))
            addMember(Member("h_f32",   MemberType.FLOAT32))
            addMember(Member("i_json",  MemberType.TAGS))
            addMember(Member("j_tag_list", MemberType.TAG_LIST))
        }
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        // Expected custom column order after sorting (type bucket, then name):
        // INT64: b_i64 | FLOAT64: c_f64 | INT32: a_i32 | FLOAT32: h_f32 | INT16: g_i16 | INT8: f_i8 | BOOLEAN: e_bool | STRING: z_str | BYTE_ARRAY: d_bytes | FLAT_MAP: i_json
        // Custom columns are slotted into their type bucket after the last standard column of the same group.
        // Buckets with no matching standard column are flushed immediately after the preceding bucket.
        // Bucket mapping: INT64=0, FLOAT64=1, INT32=2, FLOAT32=3, INT16=4, INT8=5, BOOLEAN=6, STRING=7, BYTE_ARRAY=8, TAGS=9, SET=11
        val customByBucket = mapOf(
            0 to listOf("b_i64"),        // INT64 → after gbn
            1 to listOf("c_f64"),        // FLOAT64 → after cv3
            2 to listOf("a_i32"),        // INT32 → after cc
            3 to listOf("h_f32"),        // FLOAT32 → no std col, flush after INT32 group
            4 to listOf("g_i16"),        // INT16 → no std col
            5 to listOf("f_i8"),         // INT8 → no std col
            6 to listOf("e_bool"),       // BOOLEAN → no std col, all four flush after cc
            7 to listOf("z_str"),        // STRING → after cs3
            8 to listOf("d_bytes"),      // BYTE_ARRAY → after attachment
            9 to listOf("i_json"),       // TAGS → after BYTE_ARRAY group
            11 to listOf("j_set"),       // SET → after the standard tags column (jsonb bucket)
        )

        fun assertColumnOrder(tableName: String, expectNextVersion: Boolean) {
            val columns = mutableListOf<String>()
            storage.adminConnection().use { conn ->
                conn.execute(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = \$1 AND table_name = \$2 ORDER BY ordinal_position",
                    arrayOf(map.id, tableName)
                ).use { cursor -> while (cursor.next()) columns.add(cursor["column_name"]) }
            }
            assertTrue(columns.isNotEmpty(), "No columns found for table '$tableName'")

            // Build expected full order: standard columns interleaved with custom members in their type bucket.
            // PgType → bucket mapping (matches pgTypeSortOrder in PgTable).
            val pgTypeBucket = mapOf(
                PgType.INT64 to 0, PgType.DOUBLE to 1, PgType.INT to 2,
                PgType.FLOAT to 3, PgType.SHORT to 4, PgType.BOOLEAN to 6,
                PgType.STRING to 7, PgType.BYTE_ARRAY to 8,
            )
            val baseStdCols = if (expectNextVersion) PgColumn.allColumns else PgColumn.headColumns
            // Determine last standard column index per bucket.
            val lastIdxForBucket = mutableMapOf<Int, Int>()
            for ((idx, col) in baseStdCols.withIndex()) {
                lastIdxForBucket[pgTypeBucket[col.type] ?: 11] = idx
            }
            val standardBuckets = lastIdxForBucket.keys.sorted()
            val remaining = customByBucket.toMutableMap()
            val expected = mutableListOf<String>()
            for ((idx, col) in baseStdCols.withIndex()) {
                expected.add(col.name)
                val bucket = pgTypeBucket[col.type] ?: 11
                if (lastIdxForBucket[bucket] == idx) {
                    // Flush this bucket.
                    remaining.remove(bucket)?.let { expected.addAll(it) }
                    // Flush intermediate gap buckets before next standard bucket.
                    val nextStd = standardBuckets.firstOrNull { it > bucket }
                    for (b in remaining.keys.sorted()) {
                        if (nextStd != null && b >= nextStd) break
                        remaining.remove(b)?.let { expected.addAll(it) }
                    }
                }
            }
            // Flush any remaining (beyond last standard bucket, e.g. TAGS).
            for (b in remaining.keys.sorted()) remaining[b]?.let { expected.addAll(it) }

            // Filter actual to only the columns we care about (skip any extras not in expected).
            val expectedSet = expected.toSet()
            val actual = columns.filter { it in expectedSet }
            assertEquals(expected, actual,
                "Column order mismatch in '$tableName'.\nExpected: $expected\nActual  : $actual\nFull list: $columns")
        }

        val headTable    = collection.id
        val historyTable = "${collection.id}\$hst"
        assertColumnOrder(headTable,    expectNextVersion = false)
        assertColumnOrder(historyTable, expectNextVersion = true)
    }

    /**
     * When a collection is created with an explicit [NakshaCollection.members] list and an
     * [NakshaCollection.indices] list that references a member name that was not declared, the
     * storage must reject the request with an error — the collection must NOT be created.
     */
    @Test
    fun createCollection_shouldFailWhenIndexReferencesUnknownMember() {
        val collection = NakshaCollection("members_bad_index_test", map.id).apply {
            // Declare one real custom member ...
            addMember(Member("score", MemberType.INT64))
            // ... but index a name that does not exist as a member.
            addIndex(Index("idx_ghost", IndexType.BTREE, "ghost_column"))
        }
        executeWriteErrorResponse(WriteRequest().add(Write().createCollection(collection)))
    }
}