package naksha.psql

import naksha.base.Id
import naksha.model.Naksha
import naksha.base.NakshaError
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.objects.Index
import naksha.model.objects.IndexList
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
                sql = "SELECT column_name FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2",
                args = arrayOf(Id(catalog.id).catalogName(), Id(collection.id).collectionName())
            ).use { cursor ->
                while (cursor.next()) columns.add(cursor["column_name"])
                assertEquals(XyzMembers.ALL.size + 1, columns.size)
                // Note: We will not find TN in the database, because `lib-psql` stores `fn` and `version` instead.
                assertTrue(XyzMembers.ALL.all { column -> if (column eq XyzTn) true else columns.contains(column.id.columnName()) })
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
        collectionTableNames(collection)
        checkIndicesCreatedForTable(collection.id)
    }

    private fun checkIndicesCreatedForTable(tableName: String) {
        val root = Id(tableName).collectionName()
        storage.adminConnection().use { conn ->
            conn.execute(
                sql = "SELECT indexname FROM pg_indexes WHERE schemaname = $1 AND tablename = $2",
                args = arrayOf(Id(catalog.id).catalogName(), root)
            ).use { cursor ->
                val existingIndices = mutableListOf<String>()
                while (cursor.next()) existingIndices.add(cursor["indexname"])
                for (index in XyzIndices.ALL) {
                    val expectedName = index.id.indexName(root)
                    assertTrue(expectedName in existingIndices,
                        "Index $expectedName not found; existing indices: $existingIndices")
                }
            }
        }
        assertIndexInheritance(tableName)
    }

    /** Verifies every root index has a valid, ready native descendant on every table in the partition tree. @since 3.0 */
    private fun assertIndexInheritance(collectionId: String) {
        val schema = Id(catalog.id).catalogName()
        val root = Id(collectionId).collectionName()
        storage.adminConnection().use { conn ->
            conn.execute("""
                WITH RECURSIVE root_indices AS (
                    SELECT i.indexrelid, i.indisvalid, i.indisready
                    FROM pg_catalog.pg_index i
                    WHERE i.indrelid = pg_catalog.to_regclass($1)
                ), index_tree AS (
                    SELECT indexrelid AS root_index, indexrelid FROM root_indices
                    UNION ALL
                    SELECT t.root_index, h.inhrelid
                    FROM index_tree t JOIN pg_catalog.pg_inherits h ON h.inhparent = t.indexrelid
                    WHERE NOT h.inhdetachpending
                )
                SELECT NOT EXISTS (
                    SELECT 1 FROM root_indices r
                    CROSS JOIN pg_catalog.pg_partition_tree(pg_catalog.to_regclass($1)) p
                    WHERE NOT r.indisvalid OR NOT r.indisready OR NOT EXISTS (
                        SELECT 1 FROM index_tree t
                        JOIN pg_catalog.pg_index i ON i.indexrelid = t.indexrelid
                        WHERE t.root_index = r.indexrelid AND i.indrelid = p.relid
                            AND i.indisvalid AND i.indisready
                    )
                ) AND (SELECT count(*) FROM root_indices) >= 4 AS ok
            """.trimIndent(), arrayOf("${PgUtil.quoteIdent(schema)}.${PgUtil.quoteIdent(root)}")).use { cursor ->
                assertTrue(cursor.fetch()["ok"], "Missing or invalid inherited index on $schema.$root")
            }
        }
    }

    /** Returns the root and all native descendants, verifying HEAD and finite-range naming/topology. @since 3.0 */
    private fun collectionTableNames(collection: NakshaCollection): List<String> {
        val schema = Id(catalog.id).catalogName()
        val root = Id(collection.id).collectionName()
        val names = mutableListOf<String>()
        val children = mutableListOf<String>()
        var leaves = 0
        storage.adminConnection().use { conn ->
            conn.execute("""
                SELECT c.relname, p.level, p.isleaf
                FROM pg_catalog.pg_partition_tree(pg_catalog.to_regclass($1)) p
                JOIN pg_catalog.pg_class c ON c.oid = p.relid
                JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = $2 ORDER BY p.level, c.relname
            """.trimIndent(), arrayOf("${PgUtil.quoteIdent(schema)}.${PgUtil.quoteIdent(root)}", schema)).use { cursor ->
                while (cursor.next()) {
                    val name: String = cursor["relname"]
                    names.add(name)
                    if (cursor.get<Int>("level") == 1) children.add(name)
                    if (cursor.get<Boolean>("isleaf")) leaves++
                }
            }
        }
        assertTrue(root in names, "Missing collection root $schema.$root")
        assertTrue("$root\$head" in children, "Missing HEAD partition on $schema.$root")
        if (collection.storeHistory == StoreMode.OFF) {
            assertEquals(listOf("$root\$head"), children, "History-OFF must have only HEAD")
        } else {
            assertTrue(children.size >= 5, "Expected HEAD and at least four finite ranges: $children")
        }
        for (child in children) {
            if (child == "$root\$head") continue
            val range = assertNotNull(child.removePrefix("$root$").toLongOrNull(), "Invalid finite range name: $child")
            assertEquals(historyTableName(root, range shl collection.shift, collection.shift), child)
        }
        val partitions = maxOf(1, collection.partitions)
        assertEquals(children.size * partitions, leaves, "Missing performance partitions: $names")
        for (child in children) {
            if (partitions == 1) continue
            for (partition in 0 until partitions) assertTrue("$child$$partition" in names)
        }
        assertEquals(1 + children.size + if (partitions > 1) leaves else 0, names.size)
        return names
    }

    @Test
    fun collectionShouldHasNoHistoryDBTable() {
        val collectionName = "check_no_hst_table_test"
        val collection = NakshaCollection(
            id = collectionName,
            catalogId = catalog.id,
            storeHistory = StoreMode.OFF
        )
        executeWrite(
            WriteRequest().add(
                Write().createCollection(collection)
            )
        )
        collectionTableNames(collection)

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
            catalogId = catalog.id,
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
        val delTableName = "${Id(collectionId).collectionName()}\$del"
        storage.adminConnection().use { conn ->
            val SQL = "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = $1 AND table_name = $2)"
            conn.execute(SQL, arrayOf(Id(catalog.id).catalogName(), delTableName)).use { cursor ->
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
        var collection = NakshaCollection(id = collectionName, catalogId = catalog.id)
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
        val collection = NakshaCollection(id = collectionName, catalogId = catalog.id)
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
        val collection = NakshaCollection(id = collectionName, catalogId = catalog.id)
        // create collection using upsert
        val response = executeWrite(
            WriteRequest().add(
                Write().upsertCollection(collection, atomic = false)
            )
        )
        val createdCollection = response.features[0]!!.proxy(NakshaCollection::class)
        assertEquals(StoreMode.ON, createdCollection.storeDeleted)
        collection.storeDeleted = StoreMode.SUSPEND
        // update collection using upsert
        val updateResponse = executeWrite(
            WriteRequest().add(
                Write().upsertCollection(collection, atomic = false)
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
                Write().deleteCollectionById(collectionId = collectionName, catalogId = catalog.id)
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
     * When [NakshaCollection.members] is **null** (the default / undefined), the collection is in
     * backward-compatible mode: it must be created with all default XYZ columns **and** the default
     * XYZ indices — no members or indices declared means the full default schema. The mandatory
     * (intrinsic) indices are provided by the storage and are not part of this optional set.
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
                arrayOf(Id(catalog.id).catalogName(), Id(collection.id).collectionName())
            ).use { cursor -> while (cursor.next()) columns.add(cursor["column_name"]) }
            // Note: `lib-psql` does split `tn` into `fn` and `version` columns, so we have one more column than members!
            assertEquals(
                XyzMembers.ALL.size + 1, columns.size,
                "Expected to find ${XyzMembers.ALL+1} columns, found: ${columns.size}, being: $columns"
            )
            assertTrue(XyzMembers.ALL.all { XyzMembers.XyzTn eq it || it.id.columnName() in columns })

            checkIndicesCreatedForTable(collection.id)
        }
    }

    /**
     * When [NakshaCollection.members] is explicitly an **empty list** together with an explicitly
     * empty [NakshaCollection.indices] list, the collection must be created with all standard head
     * columns (full schema) and only the intrinsic indices (`$pkey`, `$unique`, `$id`, `$version`) — no
     * default optional indices. (An empty members list opts out of the XYZ backward-compat default,
     * so an explicit index list is required; here it is empty.)
     */
    @Test
    fun membersEmpty_shouldCreateOnlyMandatoryColumnsAndNoDefaultIndices() {
        val collection = NakshaCollection("members_empty_test", catalog.id).apply {
            members = MemberList() // explicitly empty
            indices = IndexList()
        }
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        storage.adminConnection().use { conn ->
            // Columns: must be exactly the full head column set (same as members=null).
            val columns = mutableListOf<String>()
            conn.execute(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2",
                arrayOf(Id(catalog.id).catalogName(), Id(collection.id).collectionName())
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
                arrayOf(Id(catalog.id).catalogName(), Id(collection.id).collectionName())
            ).use { cursor -> while (cursor.next()) indexNames.add(cursor["indexname"]) }
            assertNoOptionalIndices(indexNames, collection.id)
            assertEquals(4, indexNames.size, "Expected only intrinsic indices: $indexNames")
        }
        assertIndexInheritance(collection.id)
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
                arrayOf(Id(catalog.id).catalogName(), Id(collection.id).collectionName())
            ).use { cursor -> while (cursor.next()) columns.add(cursor["column_name"]) }
            val expectedColumns = expectedHeadColumnNames(collection) // mandatory head columns + score
            assertEquals(expectedColumns.size, columns.size,
                "Expected ${expectedColumns.size} columns (head + 1 custom), got: $columns")
            assertTrue(expectedColumns.all { it in columns })
            val customColName = Id("score").columnName()
            assertTrue(customColName in columns, "Custom column '$customColName' not found in: $columns")

            // Indices: only the declared custom index must be present (no optional/default indices).
            val indexNames = mutableListOf<String>()
            conn.execute(
                "SELECT indexname FROM pg_indexes WHERE schemaname = $1 AND tablename = $2",
                arrayOf(Id(catalog.id).catalogName(), Id(collection.id).collectionName())
            ).use { cursor -> while (cursor.next()) indexNames.add(cursor["indexname"]) }
            assertNoOptionalIndices(indexNames, collection.id)
            // Custom index must be present.
            val customIndexId = Id("idx_score").indexName(Id(collection.id).collectionName())
            assertTrue(customIndexId in indexNames,
                "Custom index '$customIndexId' not found in: $indexNames")
            assertEquals(5, indexNames.size, "Expected intrinsic indices plus idx_score: $indexNames")
        }
        assertIndexInheritance(collection.id)
    }

    /**
     * When a custom [MemberType.TAG_LIST] member is indexed, the collection must materialize it as a text[] column and create a GIN index over it.
     */
    @Test
    fun membersSet_shouldCreateJsonbColumnAndGinIndex() {
        val collection = NakshaCollection("members_set_test", catalog.id).apply {
            addMember(Member("labels", MemberType.TAG_LIST))
            addIndex(Index("idx_labels", "labels"))
        }
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        storage.adminConnection().use { conn ->
            // Column: materialized as text[].
            var dataType: String? = null
            conn.execute(
                "SELECT data_type FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2 AND column_name = $3",
                arrayOf(Id(catalog.id).catalogName(), Id(collection.id).collectionName(), Id("labels").columnName())
            ).use { cursor -> if (cursor.next()) dataType = cursor["data_type"] }
            assertEquals("ARRAY", dataType, "TAG_LIST member 'labels' must be materialized as a text[] array")

            // Index: GIN index over the array column.
            val customIndexId = Id("idx_labels").indexName(Id(collection.id).collectionName())
            var indexDef: String? = null
            conn.execute(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = $1 AND tablename = $2 AND indexname = $3",
                arrayOf(Id(catalog.id).catalogName(), Id(collection.id).collectionName(), customIndexId)
            ).use { cursor -> if (cursor.next()) indexDef = cursor["indexdef"] }
            assertNotNull(indexDef, "SET index '$customIndexId' not found")
            assertTrue(indexDef!!.contains("USING gin", ignoreCase = true),
                "SET index must be a GIN index, got: $indexDef")
        }
        assertIndexInheritance(collection.id)
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
            indices = IndexList()
        }
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        val expected = expectedOrderedColumnNames(collection)
        for (tableName in collectionTableNames(collection)) {
            val columns = mutableListOf<String>()
            storage.adminConnection().use { conn ->
                conn.execute(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = \$1 AND table_name = \$2 ORDER BY ordinal_position",
                    arrayOf(Id(catalog.id).catalogName(), tableName)
                ).use { cursor -> while (cursor.next()) columns.add(cursor["column_name"]) }
            }
            assertTrue(columns.isNotEmpty(), "No columns found for table '$tableName'")
            assertEquals(expected, columns,
                "Column order mismatch in '$tableName'.\nExpected: $expected\nActual: $columns")
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
        val standardSpecial = listOf(StandardIndices.PublishNumber, StandardIndices.PublishTime, StandardIndices.GlobalVersion)
        val root = Id(collectionId).collectionName()
        for (idx in XyzIndices.ALL + standardSpecial) {
            assertFalse(idx.id.indexName(root) in indexNames, "Unexpected optional index '${idx.name}', found: $indexNames")
        }
        for (suffix in listOf("pkey", "unique", "id", "version")) {
            assertTrue("$root$$suffix" in indexNames, "Missing intrinsic index $suffix: $indexNames")
        }
    }

    private fun expectedHeadColumnNames(collection: NakshaCollection): Set<String> =
        expectedOrderedColumnNames(collection).toSet()

    /** Physical columns in DDL order, mirroring PgCollection.generateColumns: fn/version/nv then sorted members. */
    private fun expectedOrderedColumnNames(collection: NakshaCollection): List<String> {
        val members = collection.validatedMembers
        if (!members.isSortedByIndex()) members.verify()
        val names = mutableListOf(PgColumn.FnColumn.name, PgColumn.VersionColumn.name, PgColumn.NextVersionColumn.name)
        for (member in members) {
            if (member == null) continue
            val name = member.name
            if (name == StandardMembers.Tn.name || name == StandardMembers.NextVersion.name) continue
            names.add(member.id.columnName())
        }
        return names
    }
}
