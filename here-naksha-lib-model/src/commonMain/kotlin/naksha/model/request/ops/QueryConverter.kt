@file:Suppress("OPT_IN_USAGE", "DEPRECATION")

package naksha.model.request.ops

import naksha.base.IntList
import naksha.base.BaseEnum
import naksha.geo.HereTile
import naksha.base.NakshaError.NakshaErrorCompanion.UNSUPPORTED_OPERATION
import naksha.base.NakshaException
import naksha.model.objects.XyzMembers
import naksha.model.request.RequestQuery
import naksha.model.request.query.DoubleOp
import naksha.model.request.query.ISpatialQuery
import naksha.model.request.query.ITagQuery
import naksha.model.request.query.SpAnd
import naksha.model.request.query.SpIntersects
import naksha.model.request.query.SpNot
import naksha.model.request.query.SpOr
import naksha.model.request.query.SpRefInHereTile
import naksha.model.request.query.TagAnd
import naksha.model.request.query.TagExists
import naksha.model.request.query.TagNot
import naksha.model.request.query.TagOr
import naksha.model.request.query.TagQuery
import naksha.model.request.query.TagSetContains
import naksha.model.request.query.TagValueIsBool
import naksha.model.request.query.TagValueIsDouble
import naksha.model.request.query.TagValueIsNull
import naksha.model.request.query.TagValueIsString
import naksha.model.request.query.TagValueMatches
import kotlin.js.JsExport
import naksha.model.request.query.SpBuffer as QuerySpBuffer
import naksha.model.request.query.SpTransformation as QuerySpTransformation

/**
 * Converts the deprecated, fixed-column XYZ queries _([RequestQuery])_ into the generalized,
 * member-based [operations][Op].
 *
 * @since 3.0
 */
@JsExport
class QueryConverter private constructor() {
    companion object PgQueryConverter_C {
        /**
         * Convert the given [request-query][RequestQuery] into a single [operation][Op].
         *
         * @param query the deprecated request-query to convert.
         * @return the equivalent [operation][Op]; or `null`, if there is nothing to convert.
         * @throws NakshaException with [UNSUPPORTED_OPERATION], if a query has no generalized equivalent.
         * @since 3.0
         */
        fun convert(query: RequestQuery): Op? {
            val ops = ArrayList<Op>()
            query.tags?.let { ops.add(convertTag(it)) }
            query.spatial?.let { ops.add(convertSpatial(it)) }
            convertRefTiles(query.refTiles)?.let { ops.add(it) }
            return when (ops.size) {
                0 -> null
                1 -> ops[0]
                else -> And(*ops.toTypedArray())
            }
        }

        // --------------------------------------------------------< tags >---------------------------------------------------------

        /**
         * Convert a single _(possibly nested)_ [tag-query][ITagQuery] into an [operation][Op].
         *
         */
        private fun convertTag(query: ITagQuery): Op = when (query) {
            // Logical combinators become their generalized counterparts.
            is TagAnd -> And(*query.filterNotNull().map { convertTag(it) }.toTypedArray())
            is TagOr -> Or(*query.filterNotNull().map { convertTag(it) }.toTypedArray())
            is TagNot -> Not(convertTag(query.query))

            // Element containment in a tag-list.
            is TagSetContains -> {
                val element = query.element ?: throw NakshaException(
                    UNSUPPORTED_OPERATION, "TagSetContains without an element can't be converted"
                )
                TagListContains(XyzMembers.XyzTags, element)
            }

            is TagExists -> TagListContains(XyzMembers.XyzTags, query.name)

            // TagValueIsNull tests that the key exists and is explicitly assigned the value `null`.
            is TagValueIsNull -> TagIsNull(XyzMembers.XyzTags, query.name)

            is TagValueIsBool -> TagEquals(XyzMembers.XyzTags, query.name, query.value)
            is TagValueIsString -> TagEquals(XyzMembers.XyzTags, query.name, query.value)
            is TagValueIsDouble -> convertTagValueIsDouble(query)

            // Regular-expression matching on the tag value.
            is TagValueMatches -> TagMatches(XyzMembers.XyzTags, query.name, query.regex)

            is TagQuery -> throw NakshaException(
                UNSUPPORTED_OPERATION, "Can't convert tag-query of type '${query::class.simpleName}'"
            )
            else -> throw NakshaException(
                UNSUPPORTED_OPERATION, "Can't convert tag-query of type '${query::class.simpleName}'"
            )
        }

        /**
         * Convert a [TagValueIsDouble] into the matching tag operation, depending on its [operation][DoubleOp].
         */
        private fun convertTagValueIsDouble(query: TagValueIsDouble): Op {
            val tags = XyzMembers.XyzTags
            val name = query.name
            val value = query.value
            return when (query.op) {
                DoubleOp.EQ -> TagEquals(tags, name, value)
                DoubleOp.NE -> Not(TagEquals(tags, name, value))
                DoubleOp.GT -> TagGt(tags, name, value)
                DoubleOp.GTE -> TagGte(tags, name, value)
                DoubleOp.LT -> TagLt(tags, name, value)
                DoubleOp.LTE -> TagLte(tags, name, value)
                else -> throw NakshaException(
                    UNSUPPORTED_OPERATION, "Unsupported double operation '${query.op}' in TagValueIsDouble"
                )
            }
        }

        // -------------------------------------------------------< spatial >-------------------------------------------------------

        /**
         * Convert a single _(possibly nested)_ [spatial-query][ISpatialQuery] into an [operation][Op].
         */
        private fun convertSpatial(query: ISpatialQuery): Op = when (query) {
            is SpAnd -> And(*query.filterNotNull().map { convertSpatial(it) }.toTypedArray())
            is SpOr -> Or(*query.filterNotNull().map { convertSpatial(it) }.toTypedArray())
            is SpNot -> Not(convertSpatial(query.query))
            is SpIntersects -> {
                val transformers = flattenTransformation(query.transformation)
                Intersects(XyzMembers.XyzGeometry, query.geometry, *transformers.toTypedArray())
            }
            is SpRefInHereTile -> tileRange(query.getHereTile())
            else -> throw NakshaException(
                UNSUPPORTED_OPERATION, "Can't convert spatial-query of type '${query::class.simpleName}'"
            )
        }

        /**
         * Flatten the old single-link transformation chain _(each transformation has an optional
         * [child][QuerySpTransformation.childTransformation] executed before it)_ into the ordered list of
         * generalized [transformations][SpTransformation] expected by [Intersects], deepest child first.
         */
        private fun flattenTransformation(transformation: QuerySpTransformation?): List<SpTransformation> {
            val chain = ArrayList<QuerySpTransformation>()
            var current = transformation
            while (current != null) {
                chain.add(current)
                current = current.childTransformation
            }
            return chain.asReversed().map { convertTransformation(it) }
        }

        /**
         * Convert a single old [transformation][QuerySpTransformation] into its generalized counterpart.
         */
        private fun convertTransformation(transformation: QuerySpTransformation): SpTransformation =
            when (transformation) {
                is QuerySpBuffer -> SpBuffer(
                    distance = transformation.distance,
                    geography = transformation.geography,
                    quadSegments = transformation.quadSegments,
                    joinStyle = transformation.joinStyle?.value?.let { BaseEnum.getDefined(it, SpJoinStyle::class) },
                    joinLimit = transformation.joinLimit,
                    endCap = transformation.endCap?.value?.let { BaseEnum.getDefined(it, SpEndCap::class) },
                    side = transformation.side?.value?.let { BaseEnum.getDefined(it, SpSide::class) }
                )
                else -> throw NakshaException(
                    UNSUPPORTED_OPERATION,
                    "Can't convert spatial transformation of type '${transformation::class.simpleName}'"
                )
            }

        // ------------------------------------------------------< ref-tiles >------------------------------------------------------

        /**
         * Convert the deprecated [refTiles][RequestQuery.refTiles] list into a single [operation][Op] that
         * matches features whose reference point lies in any of the given tiles; `null`, if the list is empty.
         */
        private fun convertRefTiles(refTiles: IntList): Op? {
            val ranges = refTiles.filterNotNull().map { tileRange(HereTile(it)) }
            return when (ranges.size) {
                0 -> null
                1 -> ranges[0]
                else -> Or(*ranges.toTypedArray())
            }
        }

        /**
         * Build the here-tile range operation `here_tile BETWEEN lower AND upper` for the given [tile]
         */
        private fun tileRange(tile: HereTile): Op = And(
            Gte(XyzMembers.XyzHereTile, tile.maxLevelLowerBound().intKey),
            Lte(XyzMembers.XyzHereTile, tile.maxLevelUpperBound().intKey)
        )

    }
}
