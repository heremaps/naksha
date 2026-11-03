package naksha.psql

import naksha.base.Id
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.StringList
import naksha.model.Tuple
import naksha.model.objects.Index
import naksha.model.objects.IndexList
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaAdmin
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzIndices
import naksha.model.objects.XyzMembers
import naksha.psql.PgWriter.PgWriterCompanion.prepareCollectionLayout
import naksha.psql.PgWriter.PgWriterCompanion.validateCollectionSchemaUnchanged
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PgCollectionLayoutTest {
  @Test
  fun unresolvedModelDoesNotSelectStorageDefaults() {
    val collection = NakshaCollection("unresolved")
    collection.validateLayoutDeclarations()
    val error = assertFailsWith<NakshaException> { collection.validatedMembers }
    assertEquals(ILLEGAL_STATE, error.error.code)
    assertNull(collection.members)
    assertNull(collection.indices)
  }

  @Test
  fun defaultSelectionCopiesMembersAndNestedPaths() {
    val memberSnapshots = XyzMembers.ALL.map { Platform.toJSON(it) }
    val first = NakshaCollection("first")
    val second = NakshaCollection("second")
    prepareCollectionLayout(first)
    prepareCollectionLayout(second)
    val firstMembers = assertNotNull(first.members)
    val secondMembers = assertNotNull(second.members)
    assertEquals(XyzMembers.ALL.size, firstMembers.size)
    assertTrue(firstMembers.isSortedByIndex())

    // Check backing objects before mutating copies, so a shallow copy cannot corrupt global defaults.
    for (global in XyzMembers.ALL) {
      val firstMember = assertNotNull(firstMembers[global.name])
      val secondMember = assertNotNull(secondMembers[global.name])
      assertTrue(global.isSameAs(firstMember, comparePath = true))
      assertNotSame(global.platformObject(), firstMember.platformObject())
      assertNotSame(secondMember.platformObject(), firstMember.platformObject())
      assertNotSame(global.path.platformObject(), firstMember.path.platformObject())
      assertNotSame(secondMember.path.platformObject(), firstMember.path.platformObject())
    }
    for (member in firstMembers) assertNotNull(member).path.add("changed")
    for (i in XyzMembers.ALL.indices) {
      val global = XyzMembers.ALL[i]
      assertEquals(memberSnapshots[i], Platform.toJSON(global))
      assertContentEquals(global.path, assertNotNull(secondMembers[global.name]).path)
    }
  }

  @Test
  fun defaultSelectionCopiesIndicesAndNestedKeys() {
    val snapshots = XyzIndices.ALL.map { Platform.toJSON(it) }
    val first = NakshaCollection("first")
    val second = NakshaCollection("second")
    prepareCollectionLayout(first)
    prepareCollectionLayout(second)
    val firstIndices = assertNotNull(first.indices)
    val secondIndices = assertNotNull(second.indices)
    assertEquals(XyzIndices.ALL.size, firstIndices.size)
    for (i in XyzIndices.ALL.indices) {
      val global = XyzIndices.ALL[i]
      val firstIndex = assertNotNull(firstIndices[i])
      val secondIndex = assertNotNull(secondIndices[i])
      assertEquals(snapshots[i], Platform.toJSON(firstIndex))
      assertNotSame(global.platformObject(), firstIndex.platformObject())
      assertNotSame(secondIndex.platformObject(), firstIndex.platformObject())
      assertNotSame(global.on.platformObject(), firstIndex.on.platformObject())
      assertNotSame(secondIndex.on.platformObject(), firstIndex.on.platformObject())
    }
    for (index in firstIndices) assertNotNull(index).on.add("changed")
    for (i in XyzIndices.ALL.indices) {
      assertEquals(snapshots[i], Platform.toJSON(XyzIndices.ALL[i]))
      assertContentEquals(XyzIndices.ALL[i].on, assertNotNull(secondIndices[i]).on)
    }
  }

  @Test
  fun defaultLayoutIsPersistedBeforePhysicalSchemaGeneration() {
    val collection = NakshaCollection("default_layout")
    prepareCollectionLayout(collection)
    val physical = persist(collection)
    val members = assertNotNull(physical.naksha.members)
    assertEquals(XyzMembers.ALL.size, members.size)
    assertEquals(XyzIndices.ALL.size, physical.indices.size)
    assertContentEquals(XyzMembers.XyzTn.path, assertNotNull(members[StandardMembers.Tn.name]).path)
    for (global in XyzMembers.ALL) {
      assertTrue(global.isSameAs(members[global.name], comparePath = true))
    }
    val indices = assertNotNull(physical.naksha.indices)
    for (i in physical.indices.indices) {
      assertEquals(assertNotNull(indices[i]).name, physical.indices[i].name)
    }
  }

  @Test
  fun explicitEmptyLayoutHasOnlyGenericMandatoryColumnsAndNoIndices() {
    val collection = NakshaCollection("minimal_layout").withMembers().withIndices()
    val declaredMembers = collection.members
    val declaredIndices = collection.indices
    prepareCollectionLayout(collection)
    assertNotSame(declaredMembers, collection.members)
    assertTrue(assertNotNull(declaredMembers).isEmpty())
    assertSame(declaredIndices, collection.indices)
    val physical = persist(collection)
    val members = assertNotNull(physical.naksha.members)
    assertEquals(StandardMembers.MANDATORY.size, members.size)
    assertContentEquals(listOf("tn"), assertNotNull(members[StandardMembers.Tn.name]).path)
    assertNull(members[XyzMembers.XyzUpdatedAt.name])
    assertTrue(assertNotNull(physical.naksha.indices).isEmpty())
    assertTrue(physical.indices.isEmpty())
    assertEquals(StandardMembers.MANDATORY.size + 1, physical.columns.size)
    assertEquals(PgColumn.FnColumn.name, physical.columns[0].name)
    assertEquals(PgColumn.VersionColumn.name, physical.columns[1].name)
    for (mandatory in StandardMembers.MANDATORY) {
      assertTrue(mandatory.isSameAs(members[mandatory.name], comparePath = true))
      if (mandatory.name != StandardMembers.Tn.name) assertNotNull(physical.column(mandatory.name))
    }
    for (column in physical.columns) assertTrue("uuid" !in column.jsonPath)
  }

  @Test
  fun explicitLayoutIsValidatedSortedAndCompletedBeforeEncoding() {
    val collection = customCollection()
    val declared = assertNotNull(collection.members)
    val indices = collection.indices
    val small = assertNotNull(declared[0])
    val wide = assertNotNull(declared[1])
    prepareCollectionLayout(collection)
    val members = assertNotNull(collection.members)
    assertNotSame(declared, members)
    assertEquals(2, declared.size)
    assertSame(indices, collection.indices)
    assertEquals(StandardMembers.MANDATORY.size + 2, members.size)
    assertTrue(members.isSortedByIndex())
    for (i in StandardMembers.MANDATORY.indices) {
      assertEquals(StandardMembers.MANDATORY[i].name, assertNotNull(members[i]).name)
    }
    assertTrue(wide.isSameAs(members[StandardMembers.MANDATORY.size], comparePath = true))
    assertTrue(small.isSameAs(members[StandardMembers.MANDATORY.size + 1], comparePath = true))
    val physical = persist(collection)
    assertContentEquals(listOf("properties", "small"), assertNotNull(physical.column("small")).jsonPath)
    assertContentEquals(listOf("properties", "wide"), assertNotNull(physical.column("wide")).jsonPath)
    assertEquals(2, physical.indices.size)
    assertContentEquals(listOf("wide", "small"), physical.indices[0].on.map { it.first.name })
    assertContentEquals(listOf(PgOrder.ASC, PgOrder.DESC), physical.indices[0].on.map { it.second })
    assertContentEquals(listOf("id"), physical.indices[0].includes.map { it.name })
  }

  @Test
  fun mixedNullDeclarationsFailBeforeMutationInEitherDirection() {
    for (membersDeclared in listOf(true, false)) {
      val collection = NakshaCollection("mixed_layout")
      val members = if (membersDeclared) MemberList(Member("custom")) else null
      val indices = if (membersDeclared) null else IndexList().apply { add(Index("custom_idx", "id")) }
      collection.members = members
      collection.indices = indices
      val before = Platform.toJSON(collection)
      assertEquals(ILLEGAL_ARGUMENT, assertFailsWith<NakshaException> {
        prepareCollectionLayout(collection)
      }.error.code)
      assertSame(members, collection.members)
      assertSame(indices, collection.indices)
      assertEquals(before, Platform.toJSON(collection))
    }
  }

  @Test
  fun invalidExplicitMandatoryTypeIsRejected() {
    val collection = NakshaCollection("invalid_layout")
      .withMembers(Member(StandardMembers.Tn.name, MemberType.STRING)).withIndices()
    assertEquals(ILLEGAL_STATE, assertFailsWith<NakshaException> {
      prepareCollectionLayout(collection)
    }.error.code)
  }

  @Test
  fun defaultUpdateMatchesPersistedDefaultLayout() {
    val existing = NakshaCollection("default_update")
    prepareCollectionLayout(existing)
    val stored = persist(existing).naksha
    validateCollectionSchemaUnchanged(stored, stored)
    val before = Platform.toJSON(stored)
    val incoming = NakshaCollection(existing.id)
    prepareCollectionLayout(incoming, stored)
    validateCollectionSchemaUnchanged(stored, incoming)
    assertEquals(before, Platform.toJSON(stored))
    persist(incoming)
  }

  @Test
  fun defaultUpdateCannotReplacePersistedCustomLayout() {
    val existing = customCollection()
    prepareCollectionLayout(existing)
    val stored = persist(existing).naksha
    val before = Platform.toJSON(stored)
    assertEquals(ILLEGAL_ARGUMENT, assertFailsWith<NakshaException> {
      prepareCollectionLayout(NakshaCollection(existing.id), stored)
    }.error.code)
    assertEquals(before, Platform.toJSON(stored))
  }

  @Test
  fun explicitReorderingIsRejectedBeforeNormalization() {
    val existing = customCollection()
    prepareCollectionLayout(existing)
    val stored = persist(existing).naksha
    for (reorderMembers in listOf(true, false)) {
      val incoming = stored.copy<NakshaCollection>(recursive = true)
      if (reorderMembers) {
        incoming.members = MemberList().apply {
          for (member in assertNotNull(stored.members).reversed()) add(member?.copy<Member>(recursive = true))
        }
      } else {
        incoming.indices = IndexList().apply {
          for (index in assertNotNull(stored.indices).reversed()) add(index?.copy<Index>(recursive = true))
        }
      }
      validateCollectionSchemaUnchanged(incoming, incoming)
      val before = Platform.toJSON(incoming)
      val members = incoming.members
      val indices = incoming.indices
      assertEquals(ILLEGAL_ARGUMENT, assertFailsWith<NakshaException> {
        prepareCollectionLayout(incoming, stored)
      }.error.code)
      assertSame(members, incoming.members)
      assertSame(indices, incoming.indices)
      assertEquals(before, Platform.toJSON(incoming))
    }
  }

  @Test
  fun explicitEquivalentRoundTrippedLayoutIsAccepted() {
    val existing = customCollection()
    prepareCollectionLayout(existing)
    val stored = persist(existing).naksha
    val incoming = persist(existing).naksha
    incoming.setRaw("description", "metadata-only update")
    prepareCollectionLayout(incoming, stored)
    validateCollectionSchemaUnchanged(stored, incoming)
    val updated = persist(incoming).naksha
    validateCollectionSchemaUnchanged(stored, updated)
    assertEquals("metadata-only update", updated.getRaw("description"))
  }

  private fun customCollection(): NakshaCollection = NakshaCollection("custom_layout")
    .withMembers(
      Member("small", MemberType.INT8, JsonPath("properties", "small")),
      Member("wide", MemberType.INT64, JsonPath("properties", "wide"))
    )
    .withIndices(
      Index("wide_idx", "wide", "small").withOrder(StringList("ASC", "DESC")).withIncludes("id"),
      Index("small_idx", "small")
    )

  private fun persist(collection: NakshaCollection): PgCollection {
    val admin = NakshaAdmin(Id("layout_test_database"))
    PgCollection.resolveLayout(admin.collections)
    collection.catalogId = admin.id
    // Materialize the default-valued flag before taking raw metadata snapshots.
    for (index in assertNotNull(collection.indices)) assertNotNull(index).isConditional()
    val members = Platform.toJSON(assertNotNull(collection.members))
    val indices = Platform.toJSON(assertNotNull(collection.indices))
    val tuple = Tuple.encodeObject(collection, admin.collections, version = 1L).tuple
    val decoded = tuple.decodeCollection()
    assertEquals(members, Platform.toJSON(assertNotNull(decoded.members)))
    assertEquals(indices, Platform.toJSON(assertNotNull(decoded.indices)))
    val physical = PgCollection(Id(admin.id), tuple)
    assertEquals(members, Platform.toJSON(assertNotNull(physical.naksha.members)))
    assertEquals(indices, Platform.toJSON(assertNotNull(physical.naksha.indices)))
    assertEquals(members, Platform.toJSON(assertNotNull(collection.members)))
    assertEquals(indices, Platform.toJSON(assertNotNull(collection.indices)))
    return physical
  }
}
