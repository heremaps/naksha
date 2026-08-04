package naksha.psql

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import naksha.base.Action.Action_C.CREATE
import naksha.base.FeatureType.FeatureType_C.COLLECTION
import naksha.base.Id
import naksha.base.NakshaError
import naksha.model.Naksha
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
import naksha.model.request.ReadCollections
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.*
import kotlin.time.Clock

class CollectionTests : PgTestBase(collection = null, catalogId = "") {

    @Test
    fun shouldDropCollection() {
        // When: creating empty collection
        executeWriteAndLoadTuples(WriteRequest().add(
            Write().createCollection(NakshaCollection(Id("drop_collection_test"), catalog)))
        )
        val collection = storage.useReadSession(newSessionOptions()) {
            it.getCollectionById(catalog, Id("drop_collection_test"))
        }
        assertNotNull(collection)
        assertNotNull(collection.tupleNumber)
        assertSame(CREATE, collection.tupleNumber?.action)

        // Then: this collection is queryable and empty
        val readAllFromCollection = ReadFeatures(collection)
        val collectionContent = executeReadAndLoadTuple(readAllFromCollection)
        assertEquals(0, collectionContent.length)
        assertEquals(0, collectionContent.asFeatures.size)

        // And: Collections contain the created collection
        val selectCollectionFromVirt = ReadCollections(catalog).readCollection(collection.id)
        val virtBeforeDelete = executeReadAndLoadTuple(selectCollectionFromVirt)
        assertEquals(1, virtBeforeDelete.length)
        val collectionFromSession = storage.useReadSession(newSessionOptions()) {
            it.getCollectionById(catalog, Id("drop_collection_test"))
        }
        assertNotNull(collectionFromSession)
        assertEquals(collection.tupleNumber, collectionFromSession.tupleNumber)

        // When: Collection gets deleted
        executeWriteAndLoadTuples(WriteRequest().add(
            Write().deleteCollection(collection))
        )

        // Then: it is not present in Collections anymore
        val virtAfterDelete = executeReadAndLoadTuple(selectCollectionFromVirt)
        assertEquals(0, virtAfterDelete.length)
        val collectionFromSessionAfterDelete = storage.useReadSession(newSessionOptions()) {
            it.getCollectionById(catalog, Id("drop_collection_test"))
        }
        assertNull(collectionFromSessionAfterDelete)

        // And: reading from this collection fails
        assertFails("ERROR: relation \"${collection.id}\" does not exist") {
            executeReadAndLoadTuple(readAllFromCollection)
        }
    }

    @Test
    fun collectionShouldHaveAllColumns() {
        val collection = NakshaCollection(Id("check_db_columns_test"), catalog)
        executeWriteAndLoadTuples(
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
                assertTrue(XyzMembers.ALL.all { column -> if (column eq XyzTn) true else columns.contains(column.id) })
            }
        }
    }

    @Test
    fun collectionShouldHaveIndices() {
        val collection = NakshaCollection(Id("check_db_indices_test"), catalog)
        executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().createCollection(collection)
            )
        )
        val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        checkIndicesCreatedForTable(collection.id.text)
        // fix1 removed the META/DELETED tables: metadata is materialized as member columns and
        // tombstones remain in HEAD. Optional indices still belong on every history year partition.
        checkIndicesCreatedForTable("${collection.id}\$hst\$$currentYear")
        checkIndicesCreatedForTable("${collection.id}\$hst\$${currentYear + 1}")
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
        val collectionId = Id(collectionName)
        val collection = NakshaCollection(
            catalog = catalog,
            id = collectionId,
            storeHistory = StoreMode.OFF
        )
        executeWriteAndLoadTuples(
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
        val createFeaturesResponse = executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().createFeature(collection, feature)
            )
        )
        assertEquals(1, createFeaturesResponse.length)
        feature = assertNotNull(createFeaturesResponse.asFeatures[0])

        val readFeatureRequest = ReadFeatures()
        readFeatureRequest.catalogId = catalog.id
        readFeatureRequest.collectionId = collectionId
        readFeatureRequest.featureIds.add(feature.id)
        val readFeaturesResponse = executeReadAndLoadTuple(readFeatureRequest)
        assertEquals(1, readFeaturesResponse.length)
        feature = assertNotNull(readFeaturesResponse.asFeatures[0])
        feature.properties["foo"] = "bar"


        val updateResponse = executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().updateFeature(collection, feature, true)
            )
        )
        assertEquals(1, updateResponse.length)
        feature = assertNotNull(updateResponse.asFeatures[0])

        // Ensure that the updated feature has the "foo" property
        val readUpdatedFeatureResponse = executeReadAndLoadTuple(readFeatureRequest)
        assertEquals(1, readUpdatedFeatureResponse.length)
        val readFeature = assertNotNull(readUpdatedFeatureResponse.asFeatures[0])
        assertEquals("bar", readFeature.properties["foo"])

        // Delete the feature.
        executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().deleteFeatureById(collection, feature.id)
            )
        )

        // Ensure that it is deleted.
        val deletedFeatureResponse = executeReadAndLoadTuple(readFeatureRequest)
        assertEquals(0, deletedFeatureResponse.length)
    }

    @Test
    fun collectionShouldNotHaveDeleteTable() {
        val collectionId = Id("check_no_del_table_test")
        var collection = NakshaCollection(
            catalog = catalog,
            id = collectionId,
            storeDeleted = StoreMode.OFF
        )

        // Create the collection and read the response, we need the XYZ namespace!
        val createCollectionResponse = executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().createCollection(collection)
            )
        )
        assertEquals(1, createCollectionResponse.length)
        collection = createCollectionResponse.asFeatures[0]!!.proxy(NakshaCollection::class)

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
        val featureCreateResponse = executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().createFeature(collection, feature)
            )
        )
        assertEquals(1, featureCreateResponse.length)
        feature = featureCreateResponse.asFeatures[0]!!

        val readFeature = ReadFeatures()
        readFeature.catalogId = catalog.id
        readFeature.collectionId = collectionId
        readFeature.featureIds.add(feature.id)
        val readFeatureResponse = executeReadAndLoadTuple(readFeature)
        assertEquals(1, readFeatureResponse.length)
        // TODO: Deep compare the features, they should be identical!
        feature = featureCreateResponse.asFeatures[0]!!

        feature.properties["foo"] = "bar"
        val writeResponse = executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().updateFeature(collection, feature, true)
            )
        )
        assertEquals(1, writeResponse.length)
        Naksha.cache.clear()
        val updatedFeatureResponse = executeReadAndLoadTuple(readFeature)
        assertEquals(1, updatedFeatureResponse.length)
        assertEquals("bar", updatedFeatureResponse.asFeatures[0]?.properties?.get("foo"))
        executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().deleteFeatureById(collection, feature.id)
            )
        )
        val deletedFeatureResponse = executeReadAndLoadTuple(readFeature)
        assertEquals(0, deletedFeatureResponse.length)
    }

    @Test
    fun updateCollection() {
        val collectionName = "update_collection_test"
        val collectionId = Id(collectionName)
        var collection = NakshaCollection(collectionId, catalog)
        val createResponse = executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().createCollection(collection)
            )
        )
        assertEquals(1, createResponse.length)
        collection = assertNotNull(createResponse.asFeatures[0]).proxy(NakshaCollection::class)

        // update collection
        collection.storeDeleted = StoreMode.SUSPEND
        val updateResponse = executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().updateCollection(collection ,true)
            )
        )
        assertEquals(1, updateResponse.length)
        val responseCollection = assertNotNull(updateResponse.asFeatures[0]).proxy(NakshaCollection::class)
        assertEquals(StoreMode.SUSPEND, responseCollection.storeDeleted)
        val selectCollectionFromVirt = ReadFeatures().apply {
            this.catalogId = catalog.id
            this.collectionId = Id.COLLECTIONS_COL_ID
            this.featureIds += collection.id
        }
        val colRead = assertNotNull(executeReadAndLoadTuple(selectCollectionFromVirt).asFeatures[0]).proxy(NakshaCollection::class)
        assertEquals(StoreMode.SUSPEND, colRead.storeDeleted)
    }

    @Test
    fun updateNotExistingCollection() {
        val collectionName = "not_existing_collection_test"
        val collectionId = Id(collectionName)
        val collection = NakshaCollection(collectionId, catalog)
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
        val collectionId = Id(collectionName)
        val collection = NakshaCollection(collectionId, catalog)
        // create collection using upsert
        val response = executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().upsertCollection(collection)
            )
        )
        val createdCollection = response.asFeatures[0]!!.proxy(NakshaCollection::class)
        assertEquals(StoreMode.ON, createdCollection.storeDeleted)
        collection.storeDeleted = StoreMode.SUSPEND
        // update collection using upsert
        val updateResponse = executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().upsertCollection(collection)
            )
        )
        val updatedCollection = updateResponse.asFeatures[0]!!.proxy(NakshaCollection::class)
        assertEquals(StoreMode.SUSPEND, updatedCollection.storeDeleted)
    }

    @Test
    fun dropNotExistingCollection() {
        // given
        val collectionName = "not_existing_collection_name"
        val collectionId = Id(collectionName)

        // when
        val response = executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().deleteCollectionById(catalog, collectionId)
            )
        )

        // then
        assertEquals(0, response.length)
        assertTrue(response.asFeatures.isEmpty())
        assertEquals(0, response.asFeatures.size)
    }

    @Test
    fun testCreateExistingCollection() {
        // Given: collection in db
        val collectionId = Id("test_create_existing_collection")
        executeWriteAndLoadTuples(
            WriteRequest().add(
                Write().createCollection(NakshaCollection(collectionId, catalog))
            )
        )

        // When: create same collection once again
        val response = storage.newWriteSession(newSessionOptions()).use { session ->
            session.execute(
                WriteRequest().add(
                    Write().createCollection(NakshaCollection(collectionId, catalog))
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
        val collection = NakshaCollection(Id("members_null_test"), catalog)
        // members are null by default — do NOT set them, then they will automatically become XyzMember.ALL!
        executeWriteAndLoadTuples(WriteRequest().add(Write().createCollection(collection)))

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
            assertTrue(XyzMembers.ALL.all { XyzMembers.XyzTn eq it || it.id in columns })

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
        val collection = NakshaCollection(Id("members_empty_test"), catalog).apply {
            members = MemberList() // explicitly empty
        }
        executeWriteAndLoadTuples(WriteRequest().add(Write().createCollection(collection)))

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
            assertNoOptionalIndices(indexNames, collection.id.text)
        }
    }

    /**
     * When [NakshaCollection.members] is a **non-empty list**, the collection must be created with
     * all standard head columns plus the declared custom member columns, and only the custom indices
     * declared via [NakshaCollection.indices] — no other default optional indices.
     */
    @Test
    fun membersNonEmpty_shouldCreateMandatoryPlusCustomColumnsAndCustomIndicesOnly() {
        val collection = NakshaCollection(Id("members_custom_test"), catalog).apply {
            addMember(Member("score", MemberType.INT64))
            addIndex(Index("idx_score", "score"))
        }
        executeWriteAndLoadTuples(WriteRequest().add(Write().createCollection(collection)))

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
            assertNoOptionalIndices(indexNames, collection.id.text)
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
        val collection = NakshaCollection(Id("members_set_test"), catalog).apply {
            addMember(Member("labels", MemberType.TAG_LIST))
            addIndex(Index("idx_labels", "labels"))
        }
        executeWriteAndLoadTuples(WriteRequest().add(Write().createCollection(collection)))

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
        val collection = NakshaCollection(Id("members_set_invalid_test"), catalog).apply {
            addMember(Member("flag", MemberType.BOOLEAN))
            addIndex(Index("idx_flag", "flag"))
        }
        executeWriteErrorResponse(WriteRequest().add(Write().createCollection(collection)))
    }

    /** Verifies the physical column order matches PgCollection.generateColumns (HEAD and HISTORY identical). */
    @Test
    fun membersCustom_shouldOrderColumnsForMinimalPadding() {
        val collection = NakshaCollection(Id("members_order_test"), catalog).apply {
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
        executeWriteAndLoadTuples(WriteRequest().add(Write().createCollection(collection)))

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
        val collection = NakshaCollection(Id("members_bad_index_test"), catalog).apply {
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

    private fun expectedHeadColumnNames(collection: NakshaCollection): Set<String> =
        expectedOrderedColumnNames(collection).toSet()

    /** Physical columns in DDL order, mirroring PgCollection.generateColumns: _fn/_version/_nv then sorted members. */
    private fun expectedOrderedColumnNames(collection: NakshaCollection): List<String> {
        val members = collection.useMembers()
        if (!members.isSortedByIndex()) members.sortByDataTypeAndAssignIndex()
        val names = mutableListOf(PgColumn.FnColumn.name, PgColumn.VersionColumn.name, PgColumn.NextVersionColumn.name)
        for (member in members) {
            if (member == null) continue
            val name = member.id
            if (name == StandardMembers.TnMember.id || name == StandardMembers.NextVersionMember.id) continue
            names.add(PgMemberHelper.pgColumnName(name))
        }
        return names
    }
}
