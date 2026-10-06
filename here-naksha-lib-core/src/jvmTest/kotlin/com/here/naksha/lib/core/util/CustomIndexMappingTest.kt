@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.core.util

import com.here.naksha.lib.core.models.naksha.CustomIndexMapping
import com.here.naksha.lib.core.models.naksha.CustomIndexMappingList
import com.here.naksha.lib.core.models.naksha.customIndexMapping
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.proxy
import naksha.model.objects.*
import kotlin.test.*

class CustomIndexMappingTest {
    private fun mapping(key: String, type: MemberType = MemberType.STRING) =
        CustomIndexMapping(JsonPath("properties", key), type)

    private fun definition(vararg mappings: CustomIndexMapping) = NakshaCollection("mapped", "catalog").apply {
        customIndexMapping = CustomIndexMappingList(*mappings)
    }

    private fun native(definition: NakshaCollection) =
        CollectionIndexPolicy.toNativeCollection(definition, "physical", "target")

    @Test fun namesFollowSortedPaths() {
        val compiled = native(definition(mapping("status"), mapping("rating", MemberType.INT32)))
        assertEquals("cm01", CustomIndexMappingCompiler.memberForPath(compiled, JsonPath("properties", "rating"))!!.name)
        assertEquals("cm02", CustomIndexMappingCompiler.memberForPath(compiled, JsonPath("properties", "status"))!!.name)
        val reordered = native(definition(mapping("rating", MemberType.INT32), mapping("status")))
        assertEquals("cm01", CustomIndexMappingCompiler.memberForPath(reordered, JsonPath("properties", "rating"))!!.name)
        assertEquals(compiled.indices!!.map { it!!.name }, reordered.indices!!.map { it!!.name })
    }

    @Test fun extendsXyzDefaultsAndLeavesConfigurationUnchanged() {
        val input = definition(mapping("score", MemberType.INT32))
        val original = Platform.toJSON(input)
        val compiled = native(input)
        assertNotSame(input, compiled)
        assertEquals(original, Platform.toJSON(input))
        assertEquals("physical", compiled.id)
        assertEquals("target", compiled.catalogId)
        assertFalse(compiled.hasRaw("customIndexMapping"))
        assertEquals(XyzMembers.ALL.size + 1, compiled.members!!.size)
        assertEquals(listOf("tags", "geo", "fn_nv", "ci01"), compiled.indices!!.map { it!!.name })
    }

    @Test fun unmappedDefinitionsAreNormalizedInPlaceAsBefore() {
        val input = NakshaCollection("plain")
        val result = native(input)
        assertSame(input, result)
        assertEquals("physical", input.id)
        assertEquals(listOf("tags", "geo", "fn_nv"), input.indices!!.map { it!!.name })
        assertNull(input.members)
    }

    @Test fun booleanMappingsCreateMembersWithoutIndices() {
        val compiled = native(definition(mapping("score", MemberType.INT32), mapping("active", MemberType.BOOLEAN)))
        assertEquals(MemberType.BOOLEAN, compiled.members!!.get("cm01")!!.dataType)
        assertEquals(MemberType.INT32, compiled.members!!.get("cm02")!!.dataType)
        assertEquals(listOf("tags", "geo", "fn_nv", "ci02"), compiled.indices!!.map { it!!.name })
    }

    @Test fun rejectsInvalidMappings() {
        val invalid = listOf(
            definition(mapping("a").apply { this["memberName"] = "user_name" }),
            definition(mapping("a", MemberType.TUPLE_NUMBER)),
            definition(mapping("a", MemberType.INT8)),
            definition(mapping("a"), mapping("a")),
            definition(mapping("address", MemberType.TAG_MAP), CustomIndexMapping(JsonPath("properties", "address", "city"), MemberType.STRING)),
            definition(CustomIndexMapping(JsonPath("geometry"), MemberType.SPATIAL)),
        )
        for (definition in invalid) {
            val error = assertFailsWith<NakshaException> { native(definition) }
            assertEquals(NakshaError.ILLEGAL_ARGUMENT, error.error.code)
        }
    }

    @Test fun rejectsMalformedJson() {
        for (raw in listOf(
            "{}", "42", "[null]", "[42]", "[{}]",
            """[{"jsonPath":"properties.a","dataType":"string"}]""",
            """[{"jsonPath":["properties","a"]}]""",
            """[{"jsonPath":[],"dataType":"string"}]""",
            """[{"jsonPath":["properties",-1],"dataType":"string"}]""",
            """[{"jsonPath":["properties",true],"dataType":"string"}]""",
            """[{"jsonPath":["properties","a"],"dataType":"unknown"}]"""
        )) {
            assertFailsWith<NakshaException>(raw) {
                val definition = Platform.fromJSON("""{"id":"mapped","customIndexMapping":$raw}""").proxy(NakshaCollection::class)!!
                native(definition)
            }
        }
    }

    @Test fun capacityIncludesDefaultMembers() {
        val capacity = MemberList.MAX_MEMBERS - XyzMembers.ALL.size
        assertEquals(MemberList.MAX_MEMBERS, native(definition(*(0 until capacity).map { mapping("p$it") }.toTypedArray())).members!!.size)
        assertFailsWith<NakshaException> { native(definition(*(0..capacity).map { mapping("p$it") }.toTypedArray())) }
    }

    @Test fun acceptsLiteralKeysAndArrayIndexes() {
        val paths = listOf(JsonPath("properties", ""), JsonPath("properties", "*"),
            JsonPath("properties", "items", 0), JsonPath("properties", "items", "0"))
        val compiled = native(definition(*paths.map { CustomIndexMapping(it, MemberType.STRING) }.toTypedArray()))
        for (path in paths) assertNotNull(CustomIndexMappingCompiler.memberForPath(compiled, path))
    }
}
