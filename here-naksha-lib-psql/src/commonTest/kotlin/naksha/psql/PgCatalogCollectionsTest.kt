package naksha.psql

import naksha.base.Id
import naksha.base.Platform
import naksha.model.Tuple
import naksha.model.objects.NakshaAdmin
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.StandardIndices
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgCatalogCollectionsTest {
  @Test
  fun ordinaryCatalogDescriptorHasExplicitGenericLayoutAndIdentity() {
    val databaseId = Id("catalog_descriptor_database")
    val admin = NakshaAdmin(databaseId)
    PgCollection.resolveLayout(admin.collections)
    PgCollection.resolveLayout(admin.catalogs)
    val catalog = NakshaCatalog(databaseId, "foo")
    assertNull(catalog.tupleNumber)
    assertEquals(databaseId.number, catalog.databaseNumber)
    val pgCatalog = PgCatalog(Tuple.encodeObject(catalog, admin.catalogs, 1L).tuple)
    assertEquals(Id("foo").number, pgCatalog.id.number)
    assertEquals(0L, pgCatalog.tuple.version) // Encoding a new catalog sets the CREATE action bits.
    assertNull(pgCatalog.naksha.tupleNumber)
    assertEquals(databaseId.number, pgCatalog.naksha.databaseNumber)

    val descriptor = pgCatalog.collectionsDescriptor(admin.collections)
    assertEquals(pgCatalog.id, descriptor.catalogId)
    assertEquals("foo", descriptor.catalogId.text)
    assertEquals("foo", descriptor.naksha.catalogId)
    assertEquals(databaseId.text, descriptor.naksha.databaseId)
    assertEquals(Id.COLLECTIONS_COL_ID, descriptor.id)
    assertEquals(Id.COLLECTIONS_COL_ID.collectionName(), descriptor.name)
    assertEquals("${pgCatalog.quotedName}.${descriptor.quotedName}", descriptor.qualifiedName)
    assertEquals(0L, descriptor.tuple.version)
    assertEquals(databaseId.number, descriptor.tupleNumber.databaseNumber)
    assertEquals(pgCatalog.id.intValue, descriptor.tupleNumber.catalogNumber)
    assertEquals(pgCatalog.id.intValue, descriptor.naksha.catalogNumber)
    val child = naksha.model.objects.NakshaCollection("child", "foo")
    child.databaseId = databaseId.text
    PgCollection.resolveLayout(child)
    val childTuple = Tuple.encodeObject(child, descriptor.naksha, 1L).tuple
    assertEquals(pgCatalog.id.intValue, childTuple.tupleNumber.catalogNumber)
    assertEquals(databaseId.number, childTuple.tupleNumber.databaseNumber)
    assertEquals(Id.COLLECTIONS_COL_ID.intValue, childTuple.tupleNumber.collectionNumber)
    assertEquals(Id(child.id).number, childTuple.tupleNumber.featureNumber)
    assertEquals(Id.COLLECTIONS_COL_ID.intValue, descriptor.tupleNumber.collectionNumber)
    assertEquals(Id.COLLECTIONS_COL_ID.number, descriptor.tupleNumber.featureNumber)
    assertTrue(descriptor.storeHistory)
    assertTrue(descriptor.storeDeleted)
    assertEquals(1, descriptor.partitions)

    val members = assertNotNull(descriptor.naksha.members)
    val indices = assertNotNull(descriptor.naksha.indices)
    assertTrue(members.isSortedByIndex())
    assertEquals(StandardMembers.MANDATORY.size + 1, members.size)
    for (member in StandardMembers.MANDATORY + StandardMembers.Tags) {
      assertTrue(member.isSameAs(members[member.name], comparePath = true))
    }
    assertContentEquals(listOf("fn", "version", "nv", "gbfn", "id", "feature", "tags"),
      descriptor.columns.map { it.name })
    assertContentEquals(StandardMembers.Tn.path, assertNotNull(members[StandardMembers.Tn.name]).path)
    assertContentEquals(StandardMembers.Tags.path, assertNotNull(descriptor.column("tags")).jsonPath)
    assertNull(members[XyzMembers.XyzUpdatedAt.name])
    assertNull(descriptor.column(XyzMembers.XyzUpdatedAt.name))
    for (column in descriptor.columns) assertFalse("uuid" in column.jsonPath)

    assertEquals(1, indices.size)
    assertEquals(StandardIndices.Tags.name, assertNotNull(indices[0]).name)
    val index = descriptor.indices.single()
    assertEquals(StandardIndices.Tags.name, index.name)
    assertContentEquals(listOf(StandardMembers.Tags.name), index.on.map { it.first.name })
    assertTrue(index.includes.isEmpty())
    index.validate()
    assertTrue(index.definition().startsWith("USING gin ("))
    assertFalse("INCLUDE" in index.definition())

    val decoded = descriptor.tuple.decodeCollection()
    // Child metadata encoding materializes default-valued virtual flags.
    for (decodedMember in assertNotNull(decoded.members)) assertNotNull(decodedMember).isVirtual()
    // Physical index generation materializes the default-valued conditional flag.
    for (decodedIndex in assertNotNull(decoded.indices)) assertNotNull(decodedIndex).isConditional()
    assertEquals(Platform.toJSON(members), Platform.toJSON(assertNotNull(decoded.members)))
    assertEquals(Platform.toJSON(indices), Platform.toJSON(assertNotNull(decoded.indices)))
    assertTrue(assertNotNull(decoded.members).isSortedByIndex())
  }
}
