package naksha.model

import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaException
import naksha.model.objects.Index
import naksha.model.objects.IndexList
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CollectionLayoutTest {
    @Test
    fun unresolvedDefaultsAreValidButCannotBeAccessedOrInjected() {
        val collection = NakshaCollection("unresolved")
        assertEquals(Unit, collection.validateLayoutDeclarations())
        assertNull(collection.members)
        assertNull(collection.indices)

        val error = assertFailsWith<NakshaException> { collection.validatedMembers }
        assertEquals(ILLEGAL_STATE, error.error.code)
        assertTrue(error.message.orEmpty().contains("Storage must resolve"))
        assertNull(collection.members)
        assertNull(collection.indices)
    }

    @Test
    fun rejectsEitherUnpairedDeclarationWithoutMutation() {
        for (membersDeclared in listOf(true, false)) {
            val collection = NakshaCollection("unpaired")
            val members = if (membersDeclared) MemberList() else null
            val indices = if (membersDeclared) null else IndexList()
            collection.members = members
            collection.indices = indices

            assertEquals(ILLEGAL_ARGUMENT, assertFailsWith<NakshaException> {
                collection.validateLayoutDeclarations()
            }.error.code)
            assertEquals(ILLEGAL_ARGUMENT, assertFailsWith<NakshaException> {
                collection.validatedMembers
            }.error.code)
            assertSame(members, collection.members)
            assertSame(indices, collection.indices)
            assertTrue(members == null || members.isEmpty())
            assertTrue(indices == null || indices.isEmpty())
        }
    }

    @Test
    fun rejectsEitherUnpairedDeclarationAfterValidation() {
        for (removeMembers in listOf(true, false)) {
            val collection = NakshaCollection("previously_validated").withMembers().withIndices()
            val members = collection.validatedMembers
            val indices = collection.indices
            if (removeMembers) collection.members = null else collection.indices = null

            assertEquals(ILLEGAL_ARGUMENT, assertFailsWith<NakshaException> {
                collection.validateLayoutDeclarations()
            }.error.code)
            assertEquals(ILLEGAL_ARGUMENT, assertFailsWith<NakshaException> {
                collection.validatedMembers
            }.error.code)
            assertSame(if (removeMembers) null else members, collection.members)
            assertSame(if (removeMembers) indices else null, collection.indices)
        }
    }

    @Test
    fun explicitEmptyLayoutAddsOnlyGenericMandatoryMembers() {
        val collection = NakshaCollection("minimal").withMembers().withIndices()
        val declared = collection.members
        val indices = collection.indices
        collection.validateLayoutDeclarations()
        assertTrue(assertNotNull(declared).isEmpty())

        val members = collection.validatedMembers
        assertSame(declared, members)
        assertEquals(StandardMembers.MANDATORY.size, members.size)
        for (mandatory in StandardMembers.MANDATORY) {
            val member = assertNotNull(members[mandatory.name])
            assertNotSame(mandatory, member)
            assertTrue(mandatory.isSameAs(member, comparePath = true))
        }
        assertContentEquals(listOf("tn"), members[StandardMembers.Tn.name]?.path)
        assertNull(members[XyzMembers.XyzUpdatedAt.name])
        assertSame(indices, collection.indices)
        assertTrue(assertNotNull(indices).isEmpty())
        assertSame(members, collection.validatedMembers)
    }

    @Test
    fun injectedDescriptorsAreIndependentAcrossCollectionsAndStandards() {
        val first = NakshaCollection("first").withMembers().withIndices().validatedMembers
        val second = NakshaCollection("second").withMembers().withIndices().validatedMembers
        for (mandatory in StandardMembers.MANDATORY) {
            val firstMember = assertNotNull(first[mandatory.name])
            val secondMember = assertNotNull(second[mandatory.name])
            val path = mandatory.path.toList()
            val conditional = mandatory.conditional
            assertNotSame(mandatory, firstMember)
            assertNotSame(secondMember, firstMember)
            firstMember.conditional = !conditional
            firstMember.path = JsonPath("properties", "relocated")
            assertEquals(conditional, mandatory.conditional)
            assertEquals(conditional, secondMember.conditional)
            assertContentEquals(path, mandatory.path)
            assertContentEquals(path, secondMember.path)
        }
    }

    @Test
    fun explicitCustomLayoutAndMandatoryRelocationArePreserved() {
        val custom = Member("custom", MemberType.INT32, JsonPath("properties", "value"))
        val relocated = Member(StandardMembers.Tn, JsonPath("properties", "tuple"))
        val index = Index("custom_idx", custom)
        val collection = NakshaCollection("custom").withMembers(custom, relocated).withIndices(index)
        val declared = collection.members
        val indices = collection.indices

        collection.validateLayoutDeclarations()
        assertEquals(2, assertNotNull(declared).size)
        val members = collection.validatedMembers
        assertSame(declared, members)
        assertSame(custom, members[custom.name])
        assertSame(relocated, members[StandardMembers.Tn.name])
        assertContentEquals(listOf("properties", "tuple"), relocated.path)
        assertEquals(StandardMembers.MANDATORY.size + 1, members.size)
        assertSame(indices, collection.indices)
        assertEquals(1, assertNotNull(indices).size)
        assertSame(index, indices[0])
    }

    @Test
    fun revalidatesInPlaceMandatoryTypeChanges() {
        val collection = NakshaCollection("mutable").withMembers().withIndices()
        val members = collection.validatedMembers
        assertNotNull(members[StandardMembers.Tn.name]).dataType = MemberType.STRING

        assertEquals(ILLEGAL_STATE, assertFailsWith<NakshaException> {
            collection.validatedMembers
        }.error.code)
    }

    @Test
    fun memberHelpersRemainExplicitAndDoNotDeclareIndices() {
        for (xyz in listOf(true, false)) {
            val collection = NakshaCollection("helper")
            if (xyz) collection.withXyzMembers() else collection.withMinimalMembers()
            assertNotNull(collection.members)
            assertNull(collection.indices)
            assertEquals(ILLEGAL_ARGUMENT, assertFailsWith<NakshaException> {
                collection.validateLayoutDeclarations()
            }.error.code)
            collection.withMinimalIndices()
            collection.validateLayoutDeclarations()
            val members = collection.validatedMembers
            val expected = if (xyz) XyzMembers.XyzTn else StandardMembers.Tn
            assertContentEquals(expected.path, members[StandardMembers.Tn.name]?.path)
            assertTrue(assertNotNull(collection.indices).isEmpty())
        }
    }
}
