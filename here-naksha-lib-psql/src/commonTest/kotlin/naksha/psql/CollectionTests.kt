package naksha.psql

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import naksha.model.Naksha
import naksha.base.NakshaError
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.objects.Index
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.StandardIndices
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzIndices
import naksha.model.objects.XyzMembers
import naksha.model.objects.XyzMembers.XyzMembers_C.XyzTn
import naksha.model.request.ErrorResponse
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.*
import kotlin.time.Clock

class CollectionTests : PgTestBase(collection = null, catalogId = "") {

    @Test
    fun shouldDropCollection() {
        // Given: collection that will be tested
        val collection = NakshaCollection("drop_collection_test", catalog.id)

        // When: creating empty collection
        executeWrite(
            WriteRequest().add(
                Write().createCollection(collection)
            )
        )

        // Then: this collection is queryable and empty
        val readAllFromCollection = ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
        }
        val collectionContent = executeRead(readAllFromCollection)
        assertEquals(0, collectionContent.features.size)

        // And: Virtual Collections contain the created collection
        val selectCollectionFromVirt = ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = Naksha.COLLECTIONS_COL_ID
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
        val collection = NakshaCollection("check_db_columns_test", catalog.id)
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
                assertEquals(XyzMembers.ALL.size + 1, columns.size)
                // Note: We will not find TN in the database, because `lib-psql` stores `fn` and `version` instead.
                assertTrue(XyzMembers.ALL.all { column -> if (column eq XyzTn) true else columns.contains(column.name) })
            }
        }
    }

    @Test
    fun collectionShouldHaveIndices() {
        val collection = NakshaCollection("check_db_indices_test", catalog.id)
        executeWrite(
            WriteRequest().add(
                Write().createCollection(collection)
            )
        )
        val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        checkIndicesCreatedForTable(collection.id)
        // fix1 removed the META/DELETED tables: metadata is materialized as member columns and
        // tombstones remain in HEAD. Optional indices still belong on every history year partition.
        checkIndicesCreatedForTable("${collection.id}\$hst\$$currentYear")
        checkIndicesCreatedForTable("${collection.id}\$hst\$${currentYear + 1}")
    }

    @Test
    fun slimIndicesShouldPlaceNextVersionOnlyOnHistory() {
        val collection = NakshaCollection("slim_indices_test", catalog.id).withIndices(
            XyzIndices.XyzTags,
            StandardIndices.Geometry,
            StandardIndices.NextVersion,
        )
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        val indexes = catalogIndices(collection.id)
        val headNames = indexes.filter { it.tableName == collection.id }.map { it.indexName }.toSet()
        assertContains(headNames, "${collection.id}\$ci_tags")
        assertContains(headNames, "${collection.id}\$ci_geo")
        assertFalse(headNames.contains("${collection.id}\$ci_next_version"))
        assertUnrequestedXyzIndicesAbsent(collection.id, headNames)

        val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        for (year in listOf(currentYear, currentYear + 1)) {
            val tableName = "${collection.id}\$hst\$$year"
            val tableIndexes = indexes.filter { it.tableName == tableName }
            val names = tableIndexes.map { it.indexName }.toSet()
            assertContains(names, "$tableName\$ci_tags")
            assertContains(names, "$tableName\$ci_geo")
            assertContains(names, "$tableName\$ci_next_version")
            assertUnrequestedXyzIndicesAbsent(tableName, names)

            val nextVersionDefinition = assertNotNull(
                tableIndexes.singleOrNull { it.indexName == "$tableName\$ci_next_version" }
            ).indexDefinition
            assertTrue(
                nextVersionDefinition.contains("(nv, fn) INCLUDE (version)"),
                "Unexpected next_version definition: $nextVersionDefinition",
            )
        }
    }

    @Test
    fun nextVersionIndexShouldBeIgnoredWhenHistoryIsDisabled() {
        val collection = NakshaCollection(
            id = "slim_indices_no_history_test",
            mapId = catalog.id,
            storeHistory = StoreMode.OFF,
        ).withIndices(
            XyzIndices.XyzTags,
            StandardIndices.Geometry,
            StandardIndices.NextVersion,
        )
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        val indexes = catalogIndices(collection.id)
        assertTrue(indexes.none { it.indexName.contains("\$ci_next_version") })
        assertTrue(indexes.none { it.tableName.contains("\$hst") })
    }

    private fun checkIndicesCreatedForTable(tableName: String) {
        storage.adminConnection().use { conn ->
            conn.execute(
                sql = "SELECT indexname FROM pg_indexes WHERE tablename = $1;",
                args = arrayOf(tableName)
            ).use { cursor ->
                val existingIndices = mutableListOf<String>()
                while (cursor.next()) existingIndices.add(cursor["indexname"])
                XyzIndices.ALL.forEach { index ->
                    val expectedName = "${tableName}\$ci_${index.name}"
                    check(expectedName in existingIndices) {
                        "Index $expectedName not found; existing indices: $existingIndices"
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
            mapId = catalog.id,
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
        readFeatureRequest.catalogId = catalog.id
        readFeatureRequest.collectionId = collectionName
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
            mapId = catalog.id,
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
        readFeature.catalogId = catalog.id
        readFeature.collectionId= collectionId
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
        var collection = NakshaCollection(id = collectionName, mapId = catalog.id)
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
            catalogId = catalog.id
            collectionId = Naksha.COLLECTIONS_COL_ID
            featureIds += collection.id
        }
        val colRead = assertNotNull(executeRead(selectCollectionFromVirt).features[0]).proxy(NakshaCollection::class)
        assertEquals(StoreMode.SUSPEND, colRead.storeDeleted)
    }

    @Test
    fun updateNotExistingCollection() {
        val collectionName = "not_existing_collection_test"
        val collection = NakshaCollection(id = collectionName, mapId = catalog.id)
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
        val collection = NakshaCollection(id = collectionName, mapId = catalog.id)
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
                Write().deleteCollectionById(collectionId = collectionName, mapId = catalog.id)
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
                Write().createCollection(NakshaCollection(collectionId, catalog.id))
            )
        )

        // When: create same collection once again
        val response = storage.newWriteSession(newSessionOptions()).use { session ->
            session.execute(
                WriteRequest().add(
                    Write().createCollection(NakshaCollection(collectionId, catalog.id))
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
        val collection = NakshaCollection("members_null_test", catalog.id)
        // members are null by default — do NOT set them, then they will automatically become XyzMember.ALL!
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        storage.adminConnection().use { conn ->
            // Columns: must include all headColumns (28 columns).
            val columns = mutableListOf<String>()
            conn.execute(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2",
                arrayOf(catalog.id, collection.id)
            ).use { cursor -> while (cursor.next()) columns.add(cursor["column_name"]) }
            // Note: `lib-psql` does split `tn` into `fn` and `version` columns, so we have one more column than members!
            assertEquals(
                XyzMembers.ALL.size + 1, columns.size,
                "Expected to find ${XyzMembers.ALL+1} columns, found: ${columns.size}, being: $columns"
            )
            assertTrue(XyzMembers.ALL.all { XyzMembers.XyzTn eq it || it.name in columns })

            // Indices: must include all default optional indices on the HEAD table.
            val indexNames = mutableListOf<String>()
            conn.execute(
                "SELECT indexname FROM pg_indexes WHERE schemaname = $1 AND tablename = $2",
                arrayOf(catalog.id, collection.id)
            ).use { cursor -> while (cursor.next()) indexNames.add(cursor["indexname"]) }
            for (index in XyzIndices.ALL) {
                val expected = "${collection.id}\$ci_${index.name}"
                assertTrue(expected in indexNames, "Expected index '$expected' to be present, found: $indexNames")
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
        val collection = NakshaCollection("members_empty_test", catalog.id).apply {
            members = MemberList() // explicitly empty
        }
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        storage.adminConnection().use { conn ->
            // Columns: must be exactly the full head column set (same as members=null).
            val columns = mutableListOf<String>()
            conn.execute(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2",
                arrayOf(catalog.id, collection.id)
            ).use { cursor -> while (cursor.next()) columns.add(cursor["column_name"]) }
            val expectedColumns = expectedHeadColumnNames(collection)
            assertEquals(
                expectedColumns.size, columns.size,
                "Expected all ${expectedColumns.size} head columns, got: $columns"
            )
            assertTrue(expectedColumns.all { it in columns })

            // Indices: no optional/default indices must be present (the collection declares none).
            val indexNames = mutableListOf<String>()
            conn.execute(
                "SELECT indexname FROM pg_indexes WHERE schemaname = $1 AND tablename = $2",
                arrayOf(catalog.id, collection.id)
            ).use { cursor -> while (cursor.next()) indexNames.add(cursor["indexname"]) }
            assertNoOptionalIndices(indexNames, collection.id)
        }
    }

    /**
     * When [NakshaCollection.members] is a **non-empty list**, the collection must be created with
     * all standard head columns plus the declared custom member columns, and only the custom indices
     * declared via [NakshaCollection.indices] — no other default optional indices.
     */
    @Test
    fun membersNonEmpty_shouldCreateMandatoryPlusCustomColumnsAndCustomIndicesOnly() {
        val collection = NakshaCollection("members_custom_test", catalog.id).apply {
            addMember(Member("score", MemberType.INT64))
            addIndex(Index("idx_score", "score"))
        }
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        storage.adminConnection().use { conn ->
            // Columns: full head columns + 1 custom column (score).
            val columns = mutableListOf<String>()
            conn.execute(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2",
                arrayOf(catalog.id, collection.id)
            ).use { cursor -> while (cursor.next()) columns.add(cursor["column_name"]) }
            val expectedColumns = expectedHeadColumnNames(collection) // mandatory head columns + score
            assertEquals(expectedColumns.size, columns.size,
                "Expected ${expectedColumns.size} columns (head + 1 custom), got: $columns")
            assertTrue(expectedColumns.all { it in columns })
            val customColName = PgMemberHelper.pgColumnName("score")
            assertTrue(customColName in columns, "Custom column '$customColName' not found in: $columns")

            // Indices: only the declared custom index must be present (no optional/default indices).
            val indexNames = mutableListOf<String>()
            conn.execute(
                "SELECT indexname FROM pg_indexes WHERE schemaname = $1 AND tablename = $2",
                arrayOf(catalog.id, collection.id)
            ).use { cursor -> while (cursor.next()) indexNames.add(cursor["indexname"]) }
            assertNoOptionalIndices(indexNames, collection.id)
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
        val collection = NakshaCollection("members_set_test", catalog.id).apply {
            addMember(Member("labels", MemberType.TAG_LIST))
            addIndex(Index("idx_labels", "labels"))
        }
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        storage.adminConnection().use { conn ->
            // Column: materialized as jsonb.
            var dataType: String? = null
            conn.execute(
                "SELECT data_type FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2 AND column_name = $3",
                arrayOf(catalog.id, collection.id, PgMemberHelper.pgColumnName("labels"))
            ).use { cursor -> if (cursor.next()) dataType = cursor["data_type"] }
            assertEquals("ARRAY", dataType, "TAG_LIST member 'labels' must be materialized as a text[] array")

            // Index: GIN index over the jsonb column.
            val customIndexId = "${collection.id}\$ci_idx_labels"
            var indexDef: String? = null
            conn.execute(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = $1 AND tablename = $2 AND indexname = $3",
                arrayOf(catalog.id, collection.id, customIndexId)
            ).use { cursor -> if (cursor.next()) indexDef = cursor["indexdef"] }
            assertNotNull(indexDef, "SET index '$customIndexId' not found")
            assertTrue(indexDef!!.contains("USING gin", ignoreCase = true),
                "SET index must be a GIN index, got: $indexDef")
        }
    }

    /**
     * fix1 has no index types; the index access method is inferred from the target member's [MemberType]
     * ([PgIndex.indexAndOpsOf]). An index on a non-indexable member type (e.g. [MemberType.BOOLEAN]) must
     * therefore be rejected when the collection is created.
     */
    @Test
    fun membersSet_indexOnNonIndexableMemberShouldFail() {
        val collection = NakshaCollection("members_set_invalid_test", catalog.id).apply {
            addMember(Member("flag", MemberType.BOOLEAN))
            addIndex(Index("idx_flag", "flag"))
        }
        executeWriteErrorResponse(WriteRequest().add(Write().createCollection(collection)))
    }

    /** Verifies the physical column order matches PgCollection.generateColumns (HEAD and HISTORY identical). */
    @Test
    fun membersCustom_shouldOrderColumnsForMinimalPadding() {
        val collection = NakshaCollection("members_order_test", catalog.id).apply {
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
            addMember(Member("i_json",  MemberType.TAG_MAP))
            addMember(Member("j_tag_list", MemberType.TAG_LIST))
        }
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        val expected = expectedOrderedColumnNames(collection)
        for (tableName in listOf(collection.id, "${collection.id}\$hst")) {
            val columns = mutableListOf<String>()
            storage.adminConnection().use { conn ->
                conn.execute(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = \$1 AND table_name = \$2 ORDER BY ordinal_position",
                    arrayOf(catalog.id, tableName)
                ).use { cursor -> while (cursor.next()) columns.add(cursor["column_name"]) }
            }
            assertTrue(columns.isNotEmpty(), "No columns found for table '$tableName'")
            val actual = columns.filter { it in expected.toSet() }
            assertEquals(expected, actual,
                "Column order mismatch in '$tableName'.\nExpected: $expected\nActual  : $actual\nFull list: $columns")
        }
    }

    /**
     * When a collection is created with an explicit [NakshaCollection.members] list and an
     * [NakshaCollection.indices] list that references a member name that was not declared, the
     * storage must reject the request with an error — the collection must NOT be created.
     */
    @Test
    fun createCollection_shouldFailWhenIndexReferencesUnknownMember() {
        val collection = NakshaCollection("members_bad_index_test", catalog.id).apply {
            // Declare one real custom member ...
            addMember(Member("score", MemberType.INT64))
            // ... but index a name that does not exist as a member.
            addIndex(Index("idx_ghost", "ghost_column"))
        }
        executeWriteErrorResponse(WriteRequest().add(Write().createCollection(collection)))
    }

    /** Asserts no optional/default indices (XYZ + pn/pt/gv) were auto-created; only declared ones are materialized. */
    private fun assertNoOptionalIndices(indexNames: List<String>, collectionId: String) {
        for (idx in XyzIndices.ALL + StandardIndices.SPECIAL) {
            val forms = listOf(idx.name, "$collectionId\$i_${idx.name}", "$collectionId\$ci_${idx.name}")
            assertTrue(forms.none { it in indexNames }, "Unexpected optional index '${idx.name}', found: $indexNames")
        }
    }

    private fun assertUnrequestedXyzIndicesAbsent(tableName: String, indexNames: Set<String>) {
        val allowed = setOf(XyzIndices.XyzTags.name, StandardIndices.Geometry.name)
        for (index in XyzIndices.ALL) {
            if (index.name !in allowed) {
                assertFalse(
                    indexNames.contains("$tableName\$ci_${index.name}"),
                    "Unexpected optional index '${index.name}' on '$tableName': $indexNames",
                )
            }
        }
    }

    private fun catalogIndices(collectionId: String): List<CatalogIndex> =
        storage.adminConnection().use { conn ->
            conn.execute(
                sql = """
                    SELECT tablename, indexname, indexdef
                    FROM pg_indexes
                    WHERE schemaname = $1
                      AND tablename LIKE $2
                    ORDER BY tablename, indexname
                """.trimIndent(),
                args = arrayOf(catalog.id, "$collectionId%"),
            ).use { cursor ->
                val result = mutableListOf<CatalogIndex>()
                while (cursor.next()) {
                    result += CatalogIndex(
                        tableName = cursor["tablename"],
                        indexName = cursor["indexname"],
                        indexDefinition = cursor["indexdef"],
                    )
                }
                result
            }
        }

    private data class CatalogIndex(
        val tableName: String,
        val indexName: String,
        val indexDefinition: String,
    )

    private fun expectedHeadColumnNames(collection: NakshaCollection): Set<String> =
        expectedOrderedColumnNames(collection).toSet()

    /** Physical columns in DDL order, mirroring PgCollection.generateColumns: _fn/_version/_nv then sorted members. */
    private fun expectedOrderedColumnNames(collection: NakshaCollection): List<String> {
        val members = collection.useMembers()
        if (!members.isSortedByIndex()) members.sortByDataTypeAndAssignIndex()
        val names = mutableListOf(PgColumn.FnColumn.name, PgColumn.VersionColumn.name, PgColumn.NextVersionColumn.name)
        for (member in members) {
            if (member == null) continue
            val name = member.name
            if (name == StandardMembers.Tn.name || name == StandardMembers.NextVersion.name) continue
            names.add(PgMemberHelper.pgColumnName(name))
        }
        return names
    }
}
