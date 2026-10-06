@file:Suppress("DEPRECATION", "OPT_IN_USAGE")

package com.here.naksha.lib.core.util

import com.here.naksha.lib.core.models.naksha.customIndexMapping
import naksha.base.NakshaException
import naksha.base.NakshaError
import naksha.model.objects.*
import naksha.model.request.*
import naksha.model.request.ops.*
import naksha.model.request.query.*
import kotlin.jvm.JvmStatic
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Complete-expression translation only. A null result means keep the original request unchanged. */
class PropertyMemberQueryTranslator private constructor() {
    companion object {
        @JvmStatic @JvmOverloads
        fun adapt(original: ReadFeatures, schema: NakshaCollection, configured: NakshaCollection = schema): ReadFeatures? {
            if (original::class != ReadFeatures::class) return null
            // Even lazy getter defaults belong to the owned request, never the fallback input.
            val request = original.copy<ReadFeatures>(true)
            if (request.queryMembers != null ||
                request.guids.isNotEmpty() || request.queryHistory || request.versions != 1 ||
                request.version != null || request.minVersion != null) return null
            // Recursive raw-map copying cannot reconstruct IPropertyQuery's concrete classes.
            // Capture and clone the supported typed tree explicitly before removing legacy query data.
            if (!original.hasRaw("query")) return null
            // Unknown callbacks can transform properties before filtering. Do not move a
            // predicate ahead of them or change a manually attached legacy query's meaning.
            val filter = original.resultFilters.singleOrNull() as? PropertyFilter ?: return null
            if (filter.req !== original) return null
            val expression = original.query.properties ?: return null
            val propertyOp = translate(expression, schema, configured) ?: return null
            val other = try { QueryConverter.convert(request.query.apply {
                tags = original.query.tags
                spatial = original.query.spatial
            }) }
                catch (e: NakshaException) {
                    if (e.error.code == NakshaError.UNSUPPORTED_OPERATION) return null
                    throw e
                }
            val predicates = mutableListOf(propertyOp)
            if (other != null) predicates.add(other)
            if (request.featureIds.isNotEmpty()) {
                if (request.featureIds.any { it == null }) return null
                predicates.add(IsAnyOf(StandardMembers.Id, *request.featureIds.filterNotNull().toTypedArray()))
            }
            val captured = ReadFeatures().withPropertyQuery(copyExpression(expression))
            // Filters may be arbitrary caller objects. Preserve their identity and order explicitly.
            request.resultFilters = ResultFilterList()
            for (filter in original.resultFilters) {
                request.resultFilters.add(if (filter is PropertyFilter) PropertyFilter(captured) else filter)
            }
            request.removeRaw("query")
            request.featureIds.clear()
            request.guids.clear()
            request.queryMembers = if (predicates.size == 1) predicates[0] else And(*predicates.toTypedArray())
            return request
        }

        private fun copyExpression(query: IPropertyQuery): IPropertyQuery = when (query) {
            is PQuery -> PQuery(Property().apply { path = JsonPath(*query.property.path.filterNotNull().toTypedArray()) }, query.op, query.value)
            is PAnd -> PAnd(*query.filterNotNull().map { copyExpression(it) }.toTypedArray())
            is POr -> POr(*query.filterNotNull().map { copyExpression(it) }.toTypedArray())
            else -> error("Only translated expressions can be captured")
        }

        @JvmStatic @JvmOverloads
        fun translate(query: IPropertyQuery, schema: NakshaCollection, configured: NakshaCollection = schema): Op? = when (query) {
            is PAnd -> if (query.any { it == null }) null else translateChildren(query.filterNotNull(), schema, configured, true)
            is POr -> if (query.any { it == null }) null else translateChildren(query.filterNotNull(), schema, configured, false)
            is PQuery -> translateLeaf(query, schema, configured)
            else -> null
        }

        private fun translateChildren(children: List<IPropertyQuery>, schema: NakshaCollection, configured: NakshaCollection, and: Boolean): Op? {
            if (children.isEmpty()) return null
            val translated = children.map { translate(it, schema, configured) ?: return null }
            return if (and) And(*translated.toTypedArray()) else Or(*translated.toTypedArray())
        }

        private fun translateLeaf(query: PQuery, schema: NakshaCollection, configured: NakshaCollection): Op? {
            val mapping = configured.customIndexMapping?.firstOrNull {
                it?.jsonPath != null && CustomIndexMappingCompiler.samePath(it.jsonPath!!, query.property.path)
            } ?: return null
            val member = CustomIndexMappingCompiler.memberForPath(schema, mapping.jsonPath!!) ?: return null
            // Boolean REST predicates encode their value in the operation, not query.value.
            if (member.dataType == MemberType.BOOLEAN) {
                return when (query.op) {
                    AnyOp.IS_TRUE -> IsTrue(member)
                    AnyOp.IS_FALSE -> IsFalse(member)
                    else -> null
                }
            }
            val value = query.value ?: return null
            if (member.dataType == MemberType.STRING && value is String) {
                return when (query.op) {
                    StringOp.EQUALS -> Equals(member, value)
                    StringOp.STARTS_WITH -> StartsWith(member, value)
                    else -> null
                }
            }
            if (member.dataType == MemberType.TAG_LIST && query.op == AnyOp.CONTAINS) {
                // JSON-looking strings have parsing semantics in PropertyFilter; retain that path.
                if (value is String && !value.trim().startsWith("[") && !value.trim().startsWith("{")) {
                    return TagListContains(member, value)
                }
                return null
            }
            if (value !is Number || !value.toDouble().isFinite()) return null
            val number = value.toDouble()
            return when (member.dataType) {
                MemberType.INT16 -> integerComparison(query.op, member, number, Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong())
                MemberType.INT32 -> integerComparison(query.op, member, number, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                MemberType.INT64 -> if (abs(number) < 9_007_199_254_740_992.0)
                    integerComparison(query.op, member, number, Long.MIN_VALUE, Long.MAX_VALUE) else null
                MemberType.FLOAT32 -> if (number.toFloat().toDouble() == number) comparison(query.op, member, number.toFloat())?.let {
                    if (query.op == DoubleOp.EQ) it else And(it, Lte(member, Float.POSITIVE_INFINITY))
                } else null
                MemberType.FLOAT64 -> comparison(query.op, member, number)?.let {
                    // SQL orders NaN above infinity; PropertyFilter's Double comparisons reject it.
                    if (query.op == DoubleOp.EQ) it else And(it, Lte(member, Double.POSITIVE_INFINITY))
                }
                else -> null
            }
        }

        private fun integerComparison(op: AnyOp, member: Member, value: Double, min: Long, max: Long): Op? {
            if (op !in listOf(DoubleOp.EQ, DoubleOp.GT, DoubleOp.GTE, DoubleOp.LT, DoubleOp.LTE)) return null
            fun typed(value: Long): Number = when (member.dataType) {
                MemberType.INT16 -> value.toShort()
                MemberType.INT32 -> value.toInt()
                else -> value
            }
            fun never(): Op = And(Gt(member, typed(0)), Lt(member, typed(0)))
            fun always(): Op = Not(IsNull(member))
            if (value < min.toDouble()) return if (op == DoubleOp.GT || op == DoubleOp.GTE) always() else never()
            if (value > max.toDouble()) return if (op == DoubleOp.LT || op == DoubleOp.LTE) always() else never()
            return when (op) {
                DoubleOp.EQ -> if (floor(value) == value) Equals(member, typed(value.toLong())) else never()
                DoubleOp.GT -> Gt(member, typed(floor(value).toLong()))
                DoubleOp.GTE -> Gte(member, typed(ceil(value).toLong()))
                DoubleOp.LT -> Lt(member, typed(ceil(value).toLong()))
                DoubleOp.LTE -> Lte(member, typed(floor(value).toLong()))
                else -> null
            }
        }

        private fun comparison(op: AnyOp, member: Member, value: Number): Op? = when (op) {
            DoubleOp.EQ -> Equals(member, value)
            DoubleOp.GT -> Gt(member, value)
            DoubleOp.GTE -> Gte(member, value)
            DoubleOp.LT -> Lt(member, value)
            DoubleOp.LTE -> Lte(member, value)
            else -> null
        }
    }
}
