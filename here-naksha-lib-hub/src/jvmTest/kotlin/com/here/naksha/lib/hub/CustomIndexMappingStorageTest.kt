@file:Suppress("DEPRECATION", "OPT_IN_USAGE")
package com.here.naksha.lib.hub

import com.here.naksha.lib.core.models.naksha.CustomIndexMapping
import com.here.naksha.lib.core.models.naksha.CustomIndexMappingList
import com.here.naksha.lib.core.models.naksha.customIndexMapping
import com.here.naksha.lib.core.util.*
import naksha.model.util.RequestHelper
import naksha.psql.*
import naksha.model.*

import naksha.base.proxy
import naksha.geo.PointCoord
import naksha.geo.SpPoint
import naksha.model.TagMap
import naksha.model.TagList
import naksha.model.objects.*
import naksha.model.request.*
import naksha.model.request.ops.*
import naksha.model.request.query.*
import kotlin.test.*

class CustomIndexMappingStorageTest : CustomIndexNativeFixture() {
    private val types = listOf(MemberType.STRING, MemberType.STRING, MemberType.INT16, MemberType.INT32,
        MemberType.INT64, MemberType.FLOAT32, MemberType.FLOAT64, MemberType.BYTE_ARRAY,
        MemberType.SPATIAL, MemberType.TAG_LIST, MemberType.TAG_MAP)
    private fun definition(id: String) = NakshaCollection(id, catalog.id).apply {
        indices = IndexList()
        customIndexMapping = CustomIndexMappingList().apply {
            types.forEachIndexed { i, type -> add(CustomIndexMapping(JsonPath("properties", "v$i"), type)) }
        }
    }
    private fun createMapped(config: NakshaCollection) = executeWrite(
        RequestHelper.createWriteCollectionsRequest(CollectionIndexPolicy.toNativeCollection(config, config.id, catalog.id)))
    private fun stored(id: String) = executeRead(ReadCollections().withMapId(catalog.id).addCollectionId(id))
        .features.single()!!.proxy(NakshaCollection::class)
    private fun member(schema: NakshaCollection, index: Int) = assertNotNull(CustomIndexMappingCompiler.memberForPath(schema, JsonPath("properties", "v$index")))
    private fun ids(id: String, op: Op) = executeRead(ReadFeatures().withCatalogId(catalog.id).withCollectionId(id).apply {
        queryMembers = op
    }).features.map { it!!.id }.toSet()

    @Test fun booleanMembersWithoutIndicesRoundtripAndTranslateLikeLegacyFiltering() {
        val config = NakshaCollection("mapped_boolean", catalog.id).apply {
            indices = IndexList()
            customIndexMapping = CustomIndexMappingList(
                CustomIndexMapping(JsonPath("properties", "active"), MemberType.BOOLEAN))
        }
        createMapped(config)
        val schema = stored(config.id)
        val member = assertNotNull(CustomIndexMappingCompiler.memberForPath(schema, JsonPath("properties", "active")))
        assertEquals(MemberType.BOOLEAN, member.dataType)
        assertTrue(schema.indices!!.isEmpty())
        val writes = WriteRequest()
        for ((id, value) in listOf("active" to true, "inactive" to false, "null" to null)) {
            writes.add(Write().createFeature(schema, NakshaFeature(id).apply { properties["active"] = value }))
        }
        writes.add(Write().createFeature(schema, NakshaFeature("missing")))
        executeWrite(writes)
        val readback = executeRead(ReadFeatures().withCatalogId(catalog.id).withCollectionId(schema.id))
            .features.associateBy { it!!.id }
        assertEquals(true, readback.getValue("active")!!.properties["active"])
        assertEquals(false, readback.getValue("inactive")!!.properties["active"])
        assertNull(readback.getValue("null")!!.properties["active"])
        assertNull(readback.getValue("missing")!!.properties["active"])
        for ((op, expected) in listOf(AnyOp.IS_TRUE to "active", AnyOp.IS_FALSE to "inactive")) {
            val legacy = ReadFeatures().withCatalogId(catalog.id).withCollectionId(schema.id)
                .withPropertyQuery(PQuery(Property("properties", "active"), op))
            val translated = assertNotNull(PropertyMemberQueryTranslator.adapt(legacy, schema, config))
            if (op == AnyOp.IS_TRUE) assertIs<IsTrue>(translated.queryMembers)
            else assertIs<IsFalse>(translated.queryMembers)
            assertEquals(setOf(expected), executeRead(legacy).features.map { it!!.id }.toSet())
            assertEquals(setOf(expected), executeRead(translated).features.map { it!!.id }.toSet())
        }
    }

    @Test fun literalStringKeysRoundtripAndCanBeQueried() {
        val paths = listOf(JsonPath("properties", ""), JsonPath("properties", "*"))
        val config = NakshaCollection("literal_keys", catalog.id).apply {
            customIndexMapping = CustomIndexMappingList(*paths.map { CustomIndexMapping(it, MemberType.STRING) }.toTypedArray())
        }
        createMapped(config)
        val schema = stored(config.id)
        val feature = NakshaFeature("one").apply {
            properties[""] = "empty-key"
            properties["*"] = "literal-star"
        }
        executeWrite(WriteRequest().add(Write().createFeature(schema, feature)))
        val readback = executeRead(ReadFeatures().withCatalogId(catalog.id).withCollectionId(config.id)).features.single()!!
        for (path in paths) {
            val key = path.last() as String
            assertEquals(feature.properties[key], readback.properties[key])
            val member = assertNotNull(CustomIndexMappingCompiler.memberForPath(schema, path))
            assertEquals(setOf("one"), ids(config.id, Equals(member, feature.properties[key])))
        }
    }

    @Test fun createsAllSchemasAndExercisesTenTypesSupportedByUnpatchedStorage() {
        val source = definition("custom_all_types")
        createMapped(source)
        val schema = stored(source.id)
        assertEquals(XyzMembers.ALL.size + types.size, schema.members!!.size)
        assertEquals(types.size, schema.indices!!.size)
        assertFalse(schema.hasRaw("customIndexMapping"))
        assertNull(source.members)
        assertEquals(types.size, source.customIndexMapping!!.size)
        val point = SpPoint(PointCoord(13.0, 52.0))
        val values = listOf("a_%literal", 12.toByte(), 32000.toShort(), 2000000000, 9007199254740991L, 0.5f, 1.25,
            byteArrayOf(0, 1, -1), point, TagList("red", "blue"), TagMap("color" to "red"))
        val feature = NakshaFeature("one").apply {
            geometry = point
            properties.xyz.tags = TagList("xyz-preserved")
            values.forEachIndexed { i, value -> if (i != 1) properties["v$i"] = value }
        }
        executeWrite(WriteRequest().add(Write().createFeature(schema, feature)))
        val read = executeRead(ReadFeatures().withCatalogId(catalog.id).withCollectionId(schema.id)).features.single()!!
        for (i in 0..6) {
            if (i == 1) continue // v1 is not written.
            assertEquals(setOf("one"), ids(schema.id, Equals(member(schema, i), values[i])), "type ${types[i]}")
            if (i == 0) assertEquals(values[i], read.properties["v$i"])
            else assertEquals((values[i] as Number).toDouble(), (read.properties["v$i"] as Number).toDouble())
        }
        assertEquals(setOf("one"), ids(schema.id, StartsWith(member(schema, 0), "a_%")))
        assertEquals(setOf("one"), ids(schema.id, Equals(member(schema, 7), values[7])))
        assertEquals(setOf("one"), ids(schema.id, Intersects(member(schema, 8), point)))
        assertEquals(setOf("one"), ids(schema.id, TagListContains(member(schema, 9), "red")))
        assertEquals(setOf("one"), ids(schema.id, TagEquals(member(schema, 10), "color", "red")))
        assertNotNull(read.geometry)
        assertTrue(read.properties.xyz.tags!!.contains("xyz-preserved"))
        assertContentEquals(values[7] as ByteArray, read.properties["v7"] as ByteArray)
        assertNotNull(read.properties["v8"])
        assertNotNull(read.properties["v9"])
        assertNotNull(read.properties["v10"])
        println("CUSTOM_BINARY_JSON " + naksha.base.Platform.toJSON(read.properties["v7"]))
        storage.adminConnection().use { connection ->
            connection.execute("SELECT indexname FROM pg_indexes WHERE schemaname = $1 AND tablename = $2", arrayOf(catalog.id, schema.id)).use { rows ->
                val names = mutableSetOf<String>()
                while (rows.next()) names.add(rows.column("indexname").toString())
                schema.indices!!.forEach { assertTrue(names.contains(schema.id + "\$ci_" + it!!.name), "missing index ${it.name}: $names") }
            }
        }
    }

    @Test fun childOnlyConversionAgreesWithReadbackAndPropertyFiltering() {
        // Documents the native behaviour: a value that does not fit the member type is not stored.
        val rating = Member("rating", MemberType.INT32, JsonPath("properties", "address", "rating"))
        val definition = NakshaCollection("child_only_behavior", catalog.id).apply {
            members = MemberList(rating)
            indices = IndexList(Index("rating_idx", rating.name))
        }
        executeWrite(WriteRequest().add(Write().createCollection(definition)))
        val schema = stored(definition.id)
        val feature = NakshaFeature("one").apply {
            properties["address"] = TagMap("city" to "Berlin", "rating" to 4.5)
        }
        executeWrite(WriteRequest().add(Write().createFeature(schema, feature)))
        assertEquals(setOf("one"), ids(schema.id, IsNull(rating)))
        val readback = executeRead(ReadFeatures().withCatalogId(catalog.id).withCollectionId(schema.id)).features.single()!!
        val address = readback.properties["address"].proxy(TagMap::class)!!
        assertEquals("Berlin", address["city"])
        assertNull(address["rating"])
        val legacy = ReadFeatures().withCatalogId(catalog.id).withCollectionId(schema.id)
            .withPropertyQuery(PQuery(Property("properties", "address", "rating"), DoubleOp.EQ, 4.5))
        assertTrue(executeRead(legacy).features.isEmpty())
        val config = NakshaCollection(schema.id, catalog.id).apply {
            customIndexMapping = CustomIndexMappingList(CustomIndexMapping(rating.path, rating.dataType))
        }
        val translated = assertNotNull(PropertyMemberQueryTranslator.adapt(legacy, schema, config))
        assertTrue(executeRead(translated).features.isEmpty())
    }

    @Test fun translatedQueriesAgreeWithLegacyResultsAndKeepFallback() {
        val definition = NakshaCollection("custom_translate", catalog.id).apply {
            indices = IndexList()
            customIndexMapping = CustomIndexMappingList(CustomIndexMapping(JsonPath("properties", "score"), MemberType.INT32))
        }
        createMapped(definition)
        val schema = stored(definition.id)
        val writes = WriteRequest()
        listOf<Any?>(50, 51, -5, null, "bad", 2147483648L).forEachIndexed { i, value ->
            writes.add(Write().createFeature(schema, NakshaFeature("f$i").apply { properties["score"] = value }))
        }
        writes.add(Write().createFeature(schema, NakshaFeature("missing")))
        executeWrite(writes)
        val readback = executeRead(ReadFeatures().withCatalogId(catalog.id).withCollectionId(schema.id)).features.associateBy { it!!.id }
        for (id in listOf("f3", "f4", "f5", "missing")) assertNull(readback.getValue(id)!!.properties["score"], id)
        for (op in listOf(DoubleOp.EQ, DoubleOp.GT, DoubleOp.GTE, DoubleOp.LT, DoubleOp.LTE)) {
            for (bound in listOf(50.0, 50.5, -1e100, 1e100)) {
                val legacy = ReadFeatures().withCatalogId(catalog.id).withCollectionId(schema.id)
                    .withPropertyQuery(PQuery(Property("properties", "score"), op, bound))
                val translated = assertNotNull(PropertyMemberQueryTranslator.adapt(legacy, schema, definition))
                assertEquals(executeRead(legacy).features.map { it!!.id }.toSet(), executeRead(translated).features.map { it!!.id }.toSet(), "$op $bound")
            }
        }
        val unsupported = ReadFeatures().withPropertyQuery(PQuery(Property("properties", "score"), AnyOp.IS_NULL))
        assertNull(PropertyMemberQueryTranslator.adapt(unsupported, schema, definition))
    }

    @Test fun selectiveRequestsUseTenGeneratedIndexesOnUnpatchedStorage() {
        val definition = definition("custom_index_plans")
        createMapped(definition)
        val schema = stored(definition.id)
        fun values(i: Int): List<Any> = listOf("value_$i", (i % 128).toByte(), i.toShort(), i, i.toLong(),
            i + 0.5f, i + 0.25, byteArrayOf((i shr 8).toByte(), i.toByte()),
            SpPoint(PointCoord(i / 1000.0, 10.0)), TagList("tag_$i"), TagMap("key" to "value_$i"))
        for (batch in 0 until 10) {
            val request = WriteRequest()
            for (i in batch * 300 until (batch + 1) * 300) {
                val feature = NakshaFeature("plan_$i")
                values(i).forEachIndexed { p, value -> if (p != 1) feature.properties["v$p"] = value }
                request.add(Write().createFeature(schema, feature))
            }
            executeWrite(request)
        }
        storage.adminConnection().use { it.execute("ANALYZE \"${catalog.id}\".\"${schema.id}\"").close() }
        val values = values(1234)
        (storage.newReadSession(newSessionOptions()) as PgSession).use { session ->
            for (i in types.indices) {
                if (i == 1) continue // v1 is not written.
                val member = member(schema, i)
                val op = when (i) {
                    8 -> Intersects(member, values[i] as SpPoint)
                    9 -> TagListContains(member, "tag_1234")
                    10 -> TagEquals(member, "key", "value_1234")
                    else -> Equals(member, values[i])
                }
                val request = ReadFeatures().withCatalogId(catalog.id).withCollectionId(schema.id).apply { queryMembers = op }
                val query = PgQueryBuilder(session, request).build()
                storage.adminConnection().use { connection ->
                    connection.execute("SET search_path=\"${catalog.id}\",\"naksha~admin\",public").close()
                    val plan = session.explain(connection, false, query.sql, query.argTypes, query.argValues)
                    println("CUSTOM_INDEX_PLAN ${types[i]} ${member.name}\n${query.sql}\n$plan")
                    val indexName = schema.indices!!.first { it!!.on.single() == member.name }!!.name
                    assertTrue(plan.contains(schema.id + "\$ci_" + indexName), "No generated index for ${types[i]}:\n$plan")
                }
            }
        }
    }

    @Test fun historyRetainsCustomValuesAndIndexesAfterUpdates() {
        val definition = NakshaCollection("h".repeat(41), catalog.id, partitions = 2).apply {
            shift = 30 // Seven-digit history key plus distribution suffix reaches exactly 63 bytes.
            indices = IndexList()
            customIndexMapping = CustomIndexMappingList(CustomIndexMapping(JsonPath("properties", "score"), MemberType.INT32))
        }
        createMapped(definition)
        val schema = stored(definition.id)
        val first = executeWrite(WriteRequest().add(Write().createFeature(schema, NakshaFeature("historical").apply { properties["score"] = 10 }))).features.single()!!
        val changed = first.copy<NakshaFeature>(true).apply { properties["score"] = 20 }
        executeWrite(WriteRequest().add(Write().updateFeature(schema, changed, true)))
        val request = ReadFeatures().withCatalogId(catalog.id).withCollectionId(schema.id).apply {
            queryHistory = true; versions = 2
            queryMembers = Gte(CustomIndexMappingCompiler.memberForPath(schema, JsonPath("properties", "score"))!!, 10)
        }
        assertEquals(setOf(10, 20), executeRead(request).features.map { (it!!.properties["score"] as Number).toInt() }.toSet())
        val legacy = ReadFeatures().withCatalogId(catalog.id).withCollectionId(schema.id)
            .withPropertyQuery(PQuery(Property("properties", "score"), DoubleOp.GTE, 10)).apply { queryHistory = true; versions = 2 }
        assertNull(PropertyMemberQueryTranslator.adapt(legacy, schema, definition))
        assertEquals(2, executeRead(legacy).features.size)
        storage.adminConnection().use { connection ->
            connection.execute("SELECT indexname FROM pg_indexes WHERE schemaname=$1 AND tablename LIKE $2", arrayOf(catalog.id, schema.id + "\$hst%" )).use { rows ->
                val names = mutableListOf<String>(); while(rows.next()) names.add(rows.column("indexname").toString())
                assertTrue(names.any { it.contains("\$ci_ci01") }, names.toString())
                assertTrue(names.all { it.encodeToByteArray().size <= 63 })
                assertTrue(names.any { it.contains("\$ci_ci01") && it.encodeToByteArray().size == 63 }, names.toString())
            }
        }
    }

    @Test fun floatsAndTagQueriesMatchDecodedLegacySemantics() {
        val definition = NakshaCollection("custom_numeric_edges", catalog.id).apply {
            indices = IndexList()
            customIndexMapping = CustomIndexMappingList(
                CustomIndexMapping(JsonPath("properties", "score"), MemberType.FLOAT64),
                CustomIndexMapping(JsonPath("properties", "labels"), MemberType.TAG_LIST))
        }
        createMapped(definition)
        val schema = stored(definition.id)
        val writes = WriteRequest()
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, -0.0, 0.5, Double.POSITIVE_INFINITY).forEachIndexed { i, value ->
            writes.add(Write().createFeature(schema, NakshaFeature("edge$i").apply {
                properties["score"] = value
                properties["labels"] = TagList("red", "blue", "red=value")
            }))
        }
        executeWrite(writes)
        val expressions = listOf(DoubleOp.EQ, DoubleOp.LT, DoubleOp.LTE, DoubleOp.GT, DoubleOp.GTE).map {
            PQuery(Property("properties", "score"), it, 0.0)
        } + listOf(PQuery(Property("properties", "labels"), AnyOp.CONTAINS, "red"),
            PQuery(Property("properties", "labels"), AnyOp.CONTAINS, "red=value"))
        for (expression in expressions) {
            val legacy = ReadFeatures().withCatalogId(catalog.id).withCollectionId(schema.id).withPropertyQuery(expression)
            val translated = assertNotNull(PropertyMemberQueryTranslator.adapt(legacy, schema, definition))
            assertEquals(executeRead(legacy).features.map { it!!.id }.toSet(), executeRead(translated).features.map { it!!.id }.toSet())
        }
    }

    @Test fun existingCollectionSchemaCannotBeChanged() {
        val config = NakshaCollection("custom_immutable", catalog.id).apply {
            customIndexMapping = CustomIndexMappingList(CustomIndexMapping(JsonPath("properties", "score"), MemberType.INT32))
        }
        createMapped(config)
        fun upsert(definition: NakshaCollection): Response = storage.newWriteSession().use { writer ->
            val native = CollectionIndexPolicy.toNativeCollection(definition, definition.id, catalog.id)
            val response = writer.execute(WriteRequest().add(Write().upsertCollection(native)))
            if (response is SuccessResponse) writer.commit() else writer.rollback()
            response
        }
        assertIs<SuccessResponse>(upsert(config))
        val changedType = NakshaCollection(config.id, catalog.id).apply {
            customIndexMapping = CustomIndexMappingList(CustomIndexMapping(JsonPath("properties", "score"), MemberType.STRING))
        }
        val added = NakshaCollection(config.id, catalog.id).apply {
            customIndexMapping = CustomIndexMappingList(
                CustomIndexMapping(JsonPath("properties", "score"), MemberType.INT32),
                CustomIndexMapping(JsonPath("properties", "label"), MemberType.STRING))
        }
        val movedPath = NakshaCollection(config.id, catalog.id).apply {
            customIndexMapping = CustomIndexMappingList(CustomIndexMapping(JsonPath("properties", "different"), MemberType.INT32))
        }
        for (changed in listOf(changedType, added, movedPath, NakshaCollection(config.id, catalog.id))) {
            val response = assertIs<ErrorResponse>(upsert(changed))
            assertEquals(naksha.base.NakshaError.CONFLICT, response.error.code)
        }
        assertEquals(XyzMembers.ALL.size + 1, stored(config.id).members!!.size)
    }

    @Test fun pathChangesAreComparedByTypedSegments() {
        val changes = listOf(
            JsonPath("properties", "items", 0) to JsonPath("properties", "items", "0"),
            JsonPath("properties", "a, b") to JsonPath("properties", "a", "b"))
        changes.forEachIndexed { i, (oldPath, newPath) ->
            fun native(path: JsonPath) = CollectionIndexPolicy.toNativeCollection(NakshaCollection("custom_typed_path_$i", catalog.id).apply {
                customIndexMapping = CustomIndexMappingList(CustomIndexMapping(path, MemberType.STRING))
            }, "custom_typed_path_$i", catalog.id)
            executeWrite(WriteRequest().add(Write().createCollection(native(oldPath))))
            val response = storage.newWriteSession().use { writer ->
                val result = writer.execute(WriteRequest().add(Write().upsertCollection(native(newPath))))
                if (result is SuccessResponse) writer.commit() else writer.rollback()
                result
            }
            assertEquals(naksha.base.NakshaError.CONFLICT, assertIs<ErrorResponse>(response).error.code, "$oldPath -> $newPath")
        }
    }
}
