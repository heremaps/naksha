package naksha.psql

import naksha.psql.PgWriter.PgWriterCompanion.validateCollectionSchemaUnchanged
import naksha.base.NakshaException
import naksha.model.objects.Index
import naksha.model.objects.IndexList
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCollection
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PgCollectionSchemaTest {
  @Test
  fun rejectsNestedDefinitionChangesWithoutListCopies() {
    val existing = collection()
    val changedPath = collection()
    changedPath.members!![0]!!.path.add("nested")
    assertFailsWith<NakshaException> { validateCollectionSchemaUnchanged(existing, changedPath) }
    val changedKeys = collection()
    changedKeys.indices!![0]!!.on.add("count")
    assertFailsWith<NakshaException> { validateCollectionSchemaUnchanged(existing, changedKeys) }
    val changedOrder = collection()
    changedOrder.indices!![0]!!.order = naksha.base.StringList("DESC")
    assertFailsWith<NakshaException> { validateCollectionSchemaUnchanged(existing, changedOrder) }
    val changedIncludes = collection()
    changedIncludes.indices!![0]!!.withIncludes("count")
    assertFailsWith<NakshaException> { validateCollectionSchemaUnchanged(existing, changedIncludes) }
  }

    private fun collection() = NakshaCollection("schema_test").apply {
        members = MemberList(Member("name"), Member("count", MemberType.INT64))
        indices = IndexList().apply { add(Index("names", "name")) }
    }

    @Test
    fun acceptsEqualDefinitionsAndUnrelatedMetadataWithoutMutatingDefaults() {
        validateCollectionSchemaUnchanged(collection(), collection())
        val defaults = NakshaCollection("schema_test")
        validateCollectionSchemaUnchanged(defaults, NakshaCollection("schema_test").apply { setRaw("description", "changed") })
        assertNull(defaults.members)
        assertNull(defaults.indices)
    }

    @Test
    fun rejectsMemberAndIndexChanges() {
        val changedMember = collection().apply { members = MemberList(Member("name", MemberType.INT64), Member("count", MemberType.INT64)) }
        val removedMembers = collection().apply { members = MemberList() }
        val changedIndex = collection().apply { indices = IndexList().apply { add(Index("names", "count")) } }
        val removedIndices = collection().apply { indices = IndexList() }
        val reordered = collection().apply { members = MemberList(Member("count", MemberType.INT64), Member("name")) }
        for (incoming in listOf(changedMember, removedMembers, changedIndex, removedIndices, reordered)) {
            assertFailsWith<NakshaException> { validateCollectionSchemaUnchanged(collection(), incoming) }
        }
    }

    @Test
    fun rejectsIndexReorderingAndAbsentVersusEmptyDeclarations() {
        val existing = collection().apply { indices = IndexList().apply { add(Index("names", "name")); add(Index("counts", "count")) } }
        val reordered = collection().apply { indices = IndexList().apply { add(Index("counts", "count")); add(Index("names", "name")) } }
        assertFailsWith<NakshaException> { validateCollectionSchemaUnchanged(existing, reordered) }
        val absent = NakshaCollection("schema_test")
        assertFailsWith<NakshaException> { validateCollectionSchemaUnchanged(absent, NakshaCollection("schema_test").apply { members = MemberList() }) }
        assertFailsWith<NakshaException> { validateCollectionSchemaUnchanged(absent, NakshaCollection("schema_test").apply { indices = IndexList() }) }
    }
}
