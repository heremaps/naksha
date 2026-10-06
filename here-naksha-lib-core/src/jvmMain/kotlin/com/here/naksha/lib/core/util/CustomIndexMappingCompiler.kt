@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.core.util

import com.here.naksha.lib.core.models.naksha.CustomIndexMapping
import com.here.naksha.lib.core.models.naksha.CustomIndexMappingList
import com.here.naksha.lib.core.models.naksha.customIndexMapping
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaException
import naksha.base.PlatformList
import naksha.base.PlatformListApi
import naksha.base.PlatformMap
import naksha.model.objects.*
import kotlin.jvm.JvmStatic

/**
 * Validates `customIndexMapping` of a collection definition and expands it into native members (`cmNN`) and
 * single-member indices (`ciNN`). Names follow the sorted JSON paths, so reordering the mappings does not rename members.
 */
class CustomIndexMappingCompiler private constructor() {
    companion object {
        private val supportedTypes = listOf(
            MemberType.BOOLEAN, MemberType.STRING, MemberType.INT16, MemberType.INT32, MemberType.INT64,
            MemberType.FLOAT32, MemberType.FLOAT64, MemberType.BYTE_ARRAY, MemberType.SPATIAL,
            MemberType.TAG_MAP, MemberType.TAG_LIST
        )

        @JvmStatic
        fun hasMappings(definition: NakshaCollection?): Boolean =
            definition != null && !mappingList(definition).isNullOrEmpty()

        /** Validates the public mapping shape without changing the definition. */
        @JvmStatic
        fun validate(definition: NakshaCollection) {
            validatedMappings(definition)
        }

        /** Adds the generated members and indices to the given, caller-owned, definition. */
        @JvmStatic
        fun expand(definition: NakshaCollection): NakshaCollection {
            val mappings = validatedMappings(definition).sortedWith { a, b -> comparePaths(a.jsonPath!!, b.jsonPath!!) }
            if (mappings.isEmpty()) return definition
            val members = definition.useMembers()
            mappings.forEachIndexed { i, mapping ->
                val suffix = (i + 1).toString().padStart(2, '0')
                val member = Member("cm$suffix", mapping.dataType!!, mapping.jsonPath!!)
                for (other in members) {
                    if (other != null && !other.isVirtual() && (isPrefix(other.path, member.path) || isPrefix(member.path, other.path))) {
                        throw invalid("customIndexMapping", "path ${member.path} overlaps member '${other.name}'")
                    }
                }
                definition.addMember(member)
                // Native storage supports boolean members, but no indices on them.
                if (member.dataType != MemberType.BOOLEAN) definition.addIndex(Index("ci$suffix", member.name))
            }
            return definition
        }

        /** Returns the non-virtual member materializing exactly the given path. */
        @JvmStatic
        fun memberForPath(definition: NakshaCollection, path: JsonPath): Member? =
            definition.members?.firstOrNull { it != null && !it.isVirtual() && samePath(it.path, path) }

        private fun mappingList(definition: NakshaCollection): CustomIndexMappingList? {
            val raw = definition.getRaw("customIndexMapping")
            if (raw != null && raw !is PlatformList) throw invalid("customIndexMapping", "must be an array")
            if (raw is PlatformList) for (i in 0 until PlatformListApi.array_get_length(raw)) {
                if (PlatformListApi.array_get(raw, i) !is PlatformMap) throw invalid("customIndexMapping[$i]", "must be an object")
            }
            return definition.customIndexMapping
        }

        private fun validatedMappings(definition: NakshaCollection): List<CustomIndexMapping> {
            val result = mappingList(definition).orEmpty().mapIndexed { i, item ->
                val field = "customIndexMapping[$i]"
                val mapping = item ?: throw invalid(field, "must not be null")
                for (key in mapping.keys) {
                    if (key != "jsonPath" && key != "dataType") throw invalid("$field.$key", "unknown field; names are generated internally")
                }
                val type = mapping.dataType ?: throw invalid("$field.dataType", "is required")
                if (type !in supportedTypes) throw invalid("$field.dataType", "unsupported member type '$type'")
                val path = mapping.jsonPath ?: throw invalid("$field.jsonPath", "is required")
                if (path.isEmpty()) throw invalid("$field.jsonPath", "must not be empty")
                try {
                    path.validate()
                } catch (error: NakshaException) {
                    throw invalid("$field.jsonPath", error.error.msg)
                }
                if (path.any { it is Int && it < 0 }) throw invalid("$field.jsonPath", "array indexes must be nonnegative")
                mapping
            }
            for (i in result.indices) for (j in 0 until i) {
                val a = result[j].jsonPath!!
                val b = result[i].jsonPath!!
                // A value below another mapped value is materialized inconsistently, see the overlap test.
                if (isPrefix(a, b) || isPrefix(b, a)) throw invalid("customIndexMapping", "paths $a and $b overlap")
            }
            return result
        }

        private fun isPrefix(prefix: JsonPath, path: JsonPath): Boolean =
            prefix.size <= path.size && (0 until prefix.size).all { prefix[it] == path[it] }

        internal fun comparePaths(a: JsonPath, b: JsonPath): Int {
            for (i in 0 until minOf(a.size, b.size)) {
                val left = a[i]
                val right = b[i]
                val difference = when {
                    left is String && right is String -> left.compareTo(right)
                    left is Int && right is Int -> left.compareTo(right)
                    left is String -> -1
                    else -> 1
                }
                if (difference != 0) return difference
            }
            return a.size.compareTo(b.size)
        }

        internal fun samePath(a: JsonPath, b: JsonPath): Boolean = a.toList() == b.toList()

        private fun invalid(field: String, detail: String) = NakshaException(ILLEGAL_ARGUMENT, "$field: $detail")
    }
}
