@file:Suppress("DEPRECATION")

package naksha.model.request.ops

import naksha.geo.HereTile
import naksha.geo.PointCoord
import naksha.geo.SpPoint
import naksha.model.request.RequestQuery
import naksha.model.request.query.DoubleOp
import naksha.model.request.query.SpIntersects
import naksha.model.request.query.SpRefInHereTile
import naksha.model.request.query.TagAnd
import naksha.model.request.query.TagExists
import naksha.model.request.query.TagNot
import naksha.model.request.query.TagSetContains
import naksha.model.request.query.TagValueIsBool
import naksha.model.request.query.TagValueIsDouble
import naksha.model.request.query.TagValueIsNull
import naksha.model.request.query.TagValueIsString
import naksha.model.request.query.TagValueMatches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class QueryConverterTest {

    private companion object {
        const val TAGS = "tags"       // XyzMembers.XyzTags.name
        const val HERE_TILE = "here_tile" // XyzMembers.XyzHereTile.name
        const val GEO = "geo"         // XyzMembers.XyzGeometry.name
    }

    /**
     * Resolves the concrete operation subtype (operations read back from a list/child are typed as the
     * [Op] base) and asserts it is of the expected type.
     */
    private inline fun <reified T : Op> Op.expect(): T {
        val real = Op.detect(this) ?: error("operation '${op}' is not detectable")
        assertIs<T>(real)
        return real
    }

    private fun convert(query: RequestQuery): Op? = QueryConverter.convert(query)

    private fun tagQuery(query: naksha.model.request.query.ITagQuery): Op =
        convert(RequestQuery().apply { tags = query }) ?: error("expected a non-null op")

    // ------------------------------------------------------------< tags >-----------------------------------------------------------

    @Test
    fun tagExistsBecomesTagListContains() {
        val op = tagQuery(TagExists("sample")).expect<TagListContains>()
        assertEquals(TAGS, op.at)
        assertEquals("sample", op.item)
    }

    @Test
    fun tagValueIsNullBecomesTagIsNull() {
        val op = tagQuery(TagValueIsNull("ref")).expect<TagIsNull>()
        assertEquals(TAGS, op.at)
        assertEquals("ref", op.key)
    }

    @Test
    fun tagValueIsStringBecomesTagEquals() {
        val op = tagQuery(TagValueIsString("name", "john")).expect<TagEquals>()
        assertEquals(TAGS, op.at)
        assertEquals("name", op.key)
        assertEquals("john", op.value)
    }

    @Test
    fun tagValueIsBoolBecomesTagEquals() {
        val op = tagQuery(TagValueIsBool("flag", true)).expect<TagEquals>()
        assertEquals("flag", op.key)
        assertEquals(true, op.value)
    }

    @Test
    fun tagValueIsDoubleGtBecomesTagGt() {
        val op = tagQuery(TagValueIsDouble("speed", DoubleOp.GT, 5.0)).expect<TagGt>()
        assertEquals("speed", op.key)
        assertEquals(5.0, op.value)
    }

    @Test
    fun tagValueIsDoubleNeBecomesNotTagEquals() {
        val not = tagQuery(TagValueIsDouble("speed", DoubleOp.NE, 5.0)).expect<Not>()
        val eq = not.child.expect<TagEquals>()
        assertEquals("speed", eq.key)
        assertEquals(5.0, eq.value)
    }

    @Test
    fun tagValueMatchesBecomesTagMatches() {
        val op = tagQuery(TagValueMatches("code", "^[a-z][0-9]+$")).expect<TagMatches>()
        assertEquals(TAGS, op.at)
        assertEquals("code", op.key)
        assertEquals("^[a-z][0-9]+$", op.regex)
    }

    @Test
    fun tagSetContainsBecomesTagListContains() {
        val op = tagQuery(TagSetContains("flag:=true")).expect<TagListContains>()
        assertEquals(TAGS, op.at)
        assertEquals("flag:=true", op.item)
    }

    @Test
    fun tagAndBecomesAndWithConvertedChildren() {
        val and = tagQuery(TagAnd(TagExists("a"), TagExists("b"))).expect<And>()
        assertEquals(2, and.children.size)
        assertEquals("a", and.children[0]!!.expect<TagListContains>().item)
        assertEquals("b", and.children[1]!!.expect<TagListContains>().item)
    }

    @Test
    fun tagNotBecomesNot() {
        val not = tagQuery(TagNot(TagExists("a"))).expect<Not>()
        assertEquals("a", not.child.expect<TagListContains>().item)
    }

    // -----------------------------------------------------------< spatial >---------------------------------------------------------

    @Test
    fun spatialIntersectsBecomesIntersects() {
        val q = RequestQuery().apply { spatial = SpIntersects(SpPoint(PointCoord(1.0, 2.0))) }
        val op = convert(q)!!.expect<Intersects>()
        assertEquals(GEO, op.at)
    }

    @Test
    fun spatialRefInHereTileBecomesHereTileRange() {
        val tile = HereTile("122010112103")
        val q = RequestQuery().apply { spatial = SpRefInHereTile(tile) }
        val and = convert(q)!!.expect<And>()
        assertEquals(2, and.children.size)
        val gte = and.children[0]!!.expect<Gte>()
        val lte = and.children[1]!!.expect<Lte>()
        assertEquals(HERE_TILE, gte.at)
        assertEquals(HERE_TILE, lte.at)
        assertEquals(tile.maxLevelLowerBound().intKey, gte.value)
        assertEquals(tile.maxLevelUpperBound().intKey, lte.value)
    }

    // ----------------------------------------------------------< ref-tiles >--------------------------------------------------------

    @Test
    fun refTilesBecomeOrOfRanges() {
        val a = HereTile("122010112103")
        val b = HereTile("122010322102")
        val q = RequestQuery().apply { refTiles += listOf(a.intKey, b.intKey) }
        val or = convert(q)!!.expect<Or>()
        assertEquals(2, or.children.size)
        or.children[0]!!.expect<And>()
        or.children[1]!!.expect<And>()
    }

    @Test
    fun singleRefTileBecomesSingleRange() {
        val a = HereTile("122010112103")
        val q = RequestQuery().apply { refTiles += a.intKey }
        // A single tile is not wrapped in an OR.
        convert(q)!!.expect<And>()
    }

    // -----------------------------------------------------------< combine >---------------------------------------------------------

    @Test
    fun multipleCategoriesAreAndCombined() {
        val q = RequestQuery().apply {
            tags = TagExists("a")
            spatial = SpIntersects(SpPoint(PointCoord(1.0, 2.0)))
        }
        val and = convert(q)!!.expect<And>()
        assertEquals(2, and.children.size)
        and.children[0]!!.expect<TagListContains>()
        and.children[1]!!.expect<Intersects>()
    }

    @Test
    fun emptyQueryConvertsToNull() {
        assertNull(convert(RequestQuery()))
    }
}
