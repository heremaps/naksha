@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaException
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Validates the [custom index mappings][NakshaCollection.customIndexMapping] of a collection and generates their
 * deterministic members (`cmNN`) and indices (`ciNN`).
 * @since 3.0
 */
@JsExport
class CustomIndexMappings private constructor() {

    companion object CustomIndexMappings_C {

        /**
         * The member types that can be mapped, these are the types the property query translator fully supports.
         * Other types, e.g. [MemberType.BYTE_ARRAY] or [MemberType.SPATIAL], would only be queryable by the legacy
         * in-memory filter, and are therefore rejected.
         * @since 3.0
         */
        @JvmField @JsStatic
        val SUPPORTED_TYPES: List<MemberType> = listOf(
            MemberType.BOOLEAN, MemberType.STRING, MemberType.INT16, MemberType.INT32, MemberType.INT64,
            MemberType.FLOAT32, MemberType.FLOAT64, MemberType.TAG_MAP, MemberType.TAG_LIST
        )

        /**
         * Tests if the given collection declares custom index mappings.
         * @param collection the collection to test.
         * @return _true_ if at least one mapping is declared.
         * @since 3.0
         */
        @JvmStatic @JsStatic
        fun hasMappings(collection: NakshaCollection?): Boolean {
            val mappings = collection?.customIndexMapping ?: return false
            return mappings.isNotEmpty()
        }

        /**
         * Validates the mappings of the given collection, without changing it.
         * @param collection the collection to validate.
         * @throws NakshaException with [ILLEGAL_ARGUMENT] for a missing or unsupported data type, an invalid JSON path, a
         * path that is mapped twice or already used by another member, a generated member or index name that is already
         * used differently, or too many members (including the mandatory ones). Unknown fields of a mapping are ignored.
         * @since 3.0
         */
        @JvmStatic @JsStatic
        fun validate(collection: NakshaCollection) {
            val members = collection.members ?: MemberList(XyzMembers.ALL)
            val indices = collection.indices
            val generated = generatedMembers(collection)
            // The storage adds the missing mandatory members, see NakshaCollection.useMembers.
            var count = members.size
            for (mandatory in StandardMembers.MANDATORY) {
                if (members.get(mandatory.name) == null) count++
            }
            for (i in generated.indices) {
                val member = generated[i]
                for (j in 0 until i) {
                    if (samePath(generated[j].path, member.path)) throw invalid("customIndexMapping", "path ${member.path} is mapped twice")
                }
                // An existing generated member is only reused, if it is exactly the generated one.
                val existing = members.get(member.name)
                if (existing == null) count++
                else if (existing.isVirtual() || !existing.isSameAs(member, comparePath = true)) {
                    throw invalid("customIndexMapping", "member name '${member.name}' is already used by a different member")
                }
                for (other in members) {
                    if (other == null || other.isVirtual() || other.name == member.name) continue
                    if (samePath(other.path, member.path)) {
                        throw invalid("customIndexMapping", "path ${member.path} is already used by member '${other.name}'")
                    }
                }
                val indexName = indexName(member) ?: continue
                val index = indices?.firstOrNull { it?.name == indexName } ?: continue
                if (index.on.size != 1 || index.on[0] != member.name) {
                    throw invalid("customIndexMapping", "index name '$indexName' is already used by a different index")
                }
            }
            if (count > MemberList.MAX_MEMBERS) {
                throw invalid("customIndexMapping", "a collection can't have more than ${MemberList.MAX_MEMBERS} members")
            }
        }

        /**
         * Returns the members generated for the mappings of the given collection, named `cmNN` in the order of their
         * sorted paths. Other members of the collection are not included.
         * @param collection the collection with the mappings.
         * @return the generated members.
         * @throws NakshaException with [ILLEGAL_ARGUMENT], if a mapping is invalid.
         * @since 3.0
         */
        @JvmStatic @JsStatic
        fun generatedMembers(collection: NakshaCollection): List<Member> {
            val mappings = validatedMappings(collection).sortedWith { a, b -> comparePaths(a.jsonPath!!, b.jsonPath!!) }
            val members = ArrayList<Member>(mappings.size)
            for (i in mappings.indices) {
                val mapping = mappings[i]
                val suffix = (if (i + 1 < 10) "0" else "") + (i + 1)
                members.add(Member("cm$suffix", mapping.dataType!!, mapping.jsonPath!!))
            }
            return members
        }

        /**
         * Returns the name of the index generated for the given generated member; booleans are not indexed.
         * @param member a member returned by [generatedMembers].
         * @return the index name or _null_, if the member is not indexed.
         * @since 3.0
         */
        @JvmStatic @JsStatic
        fun indexName(member: Member): String? =
            if (MemberType.BOOLEAN == member.dataType) null else "ci" + member.name.substring(2)

        /**
         * Tests if the given paths are equal.
         * @since 3.0
         */
        @JvmStatic @JsStatic
        fun samePath(a: JsonPath, b: JsonPath): Boolean = a.size == b.size && isPrefix(a, b)

        /**
         * Compares paths segment by segment, names before array indexes.
         * @since 3.0
         */
        @JvmStatic @JsStatic
        fun comparePaths(a: JsonPath, b: JsonPath): Int {
            for (i in 0 until minOf(a.size, b.size)) {
                val left = a[i]
                val right = b[i]
                val difference = if (left is String && right is String) left.compareTo(right)
                else if (left is Int && right is Int) left.compareTo(right)
                else if (left is String) -1 else 1
                if (difference != 0) return difference
            }
            return a.size.compareTo(b.size)
        }

        private fun isPrefix(prefix: JsonPath, path: JsonPath): Boolean {
            if (prefix.size > path.size) return false
            for (i in 0 until prefix.size) {
                if (prefix[i] != path[i]) return false
            }
            return true
        }

        private fun validatedMappings(collection: NakshaCollection): List<CustomIndexMapping> {
            val result = ArrayList<CustomIndexMapping>()
            val mappings = collection.customIndexMapping ?: return result
            for (i in 0 until mappings.size) {
                val field = "customIndexMapping[$i]"
                val mapping = mappings[i] ?: throw invalid(field, "must not be null")
                val type = mapping.dataType ?: throw invalid("$field.dataType", "is required")
                if (type !in SUPPORTED_TYPES) throw invalid("$field.dataType", "unsupported member type '$type'")
                val path = mapping.jsonPath ?: throw invalid("$field.jsonPath", "is required")
                if (path.isEmpty()) throw invalid("$field.jsonPath", "must not be empty")
                try {
                    path.validate()
                } catch (error: NakshaException) {
                    throw invalid("$field.jsonPath", error.error.msg)
                }
                for (segment in path) {
                    if (segment is Int && segment < 0) throw invalid("$field.jsonPath", "array indexes must be nonnegative")
                }
                result.add(mapping)
            }
            return result
        }

        private fun invalid(field: String, detail: String) = NakshaException(ILLEGAL_ARGUMENT, "$field: $detail")
    }
}
