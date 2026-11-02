package naksha.psql

import naksha.base.NakshaException
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PgIndexTest {
    private val name = PgColumn(3, Member("name", MemberType.STRING))
    private val geo = PgColumn(4, Member("geo", MemberType.SPATIAL))
    private val tags = PgColumn(5, Member("tags", MemberType.TAG_LIST))
    private val map = PgColumn(6, Member("tagmap", MemberType.TAG_MAP))
    private val number = PgColumn(7, Member("number", MemberType.INT64))

    @Test
    fun rejectsUnsupportedKeyCombinationsInEitherOrder() {
        for (keys in listOf(listOf(name, geo), listOf(geo, name), listOf(tags, name),
            listOf(name, tags), listOf(number, geo), listOf(geo, tags))) {
            val index = PgIndex("unsupported", keys.map { it to PgOrder.ASC }.toTypedArray())
            val failure = assertFailsWith<NakshaException> { index.validate() }
            assertTrue(failure.message!!.contains("unsupported"))
        }
        assertFailsWith<NakshaException> { PgIndex("empty", emptyArray()).validate() }
    }

    @Test
    fun rejectsGinIncludesRatherThanDiscardingRequestedColumns() {
        for (key in listOf(tags, map)) {
            val index = PgIndex("gin_covering", arrayOf(key to PgOrder.ASC), arrayOf(PgColumn.FnColumn))
            val failure = assertFailsWith<NakshaException> { index.validate() }
            assertTrue(failure.message!!.contains("INCLUDE"))
            assertEquals(1, index.includes.size)
            assertEquals(PgColumn.FnColumn, index.includes[0])
        }
    }

    @Test
    fun acceptsSupportedKeysAndPayloadsAndRevalidatesMutations() {
        for (keys in listOf(listOf(name, number), listOf(geo, number), listOf(tags, number), listOf(map))) {
            PgIndex("valid", keys.map { it to PgOrder.ASC }.toTypedArray()).validate()
        }
        for (key in listOf(name, geo)) {
            PgIndex("covering", arrayOf(key to PgOrder.ASC), arrayOf(name, PgColumn.FnColumn)).validate()
        }
        val mutable = PgIndex("mutable", arrayOf(tags to PgOrder.ASC))
        mutable.validate()
        mutable.includes = arrayOf(number)
        assertFailsWith<NakshaException> { mutable.validate() }
    }
}
