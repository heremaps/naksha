package naksha.psql

import naksha.base.Action
import naksha.base.Id
import naksha.jbon.MemberValuesBook
import naksha.model.Tuple
import naksha.model.objects.JsonPath
import naksha.model.objects.IndexList
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaAdmin
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Database-free storage round-trip tests exercise physical/book mapping, selector-only tombstones and descriptor replacement. @since 3.0 */
class PgMemberLayoutTest {
  private fun collection(): PgCollection {
    val admin = NakshaAdmin(Id("layout_test"))
    PgCollection.resolveLayout(admin.collections)
    val definition = NakshaCollection("roads", "layout_catalog")
    definition.databaseId = "layout_test"
    definition.members = MemberList(Member("value", MemberType.INT32, JsonPath("properties", "value")))
    definition.indices = IndexList()
    PgCollection.resolveLayout(definition)
    return PgCollection(Id(definition.catalogId!!), Tuple.encodeObject(definition, admin.collections, 4L).tuple)
  }

  @Test
  fun indexedRowsRestoreCanonicalSlotsAndSplitTupleNumber() {
    val col = collection()
    val feature = NakshaFeature("feature_text").apply { properties.setRaw("value", 123); properties.setRaw("extra", "remainder") }
    val tn = col.tuple.tupleNumber
    val tuple = Tuple.encodeWithLayout(feature, tn.databaseNumber, col.catalogId.intValue, col.id.intValue,
      col.memberLayout, 100L, requestedAction = Action.CREATE).tuple
    val rows = PgRows().withCollection(col).apply { size = 1; setRow(0, tuple) }
    val restored = assertNotNull(rows.getTuple(0))
    val restoredBook = assertIs<MemberValuesBook>(restored.membersBook)
    assertSame(col.memberLayout.bookLayout, restoredBook.layout)
    assertEquals(tuple.tupleNumber, restored.tupleNumber)
    assertEquals(123, restored.membersBook["value"])
    assertEquals("feature_text", restored.id.text)
    assertEquals("remainder", restored.decodeObject(null).getPath("properties", "extra"))
    val tnSlot = col.memberLayout.bookLayout.indexOfName(StandardMembers.TN)
    assertEquals(-1, col.columnForBookSlot(tnSlot))
    for (i in col.columns.indices) {
      val slot = col.bookSlotForColumn(i)
      if (slot >= 0) {
        assertEquals(col.columns[i].name, col.memberLayout.nameAt(slot))
        assertEquals(i, col.columnForBookSlot(slot))
      }
    }
    rows.setColumn("value", 0, 456)
    assertEquals(123, restored.membersBook["value"])
    assertEquals(456, assertNotNull(rows.getTuple(0)).membersBook["value"])
  }

  @Test
  fun emptyTombstonesKeepOnlyIdentityAndReplacementCompilesNewSnapshot() {
    val col = collection()
    val feature = NakshaFeature("hashed_identity")
    val tuple = Tuple.encodeWithLayout(feature, col.tuple.tupleNumber.databaseNumber, col.catalogId.intValue,
      col.id.intValue, col.memberLayout, 100L, requestedAction = Action.DELETE).tuple
    val empty = Tuple(byteArrayOf(), tuple.membersBook)
    val rows = PgRows().withCollection(col).apply { size = 1; setRow(0, empty) }
    val restored = assertNotNull(rows.getTuple(0, "hashed_identity"))
    assertEquals("hashed_identity", restored.id.text)
    assertEquals(0, restored.featureBytes.size)
    assertNull(restored.membersBook["value"])
    assertEquals(Action.DELETE, restored.action)
    col.naksha.members!!.findMember(Member("value", MemberType.INT32))!!.path[1] = "changed"
    assertEquals("value", col.memberLayout.nameAt(col.memberLayout.bookLayout.indexOfName("value")))
    val changedFeature = NakshaFeature("42").apply { properties.setRaw("value", 123); properties.setRaw("changed", 456) }
    val replacement = PgCollection(col.catalogId, col.tuple, col.naksha)
    val updated = Tuple.encodeWithLayout(changedFeature, col.tuple.tupleNumber.databaseNumber, col.catalogId.intValue,
      col.id.intValue, replacement.memberLayout, 104L, requestedAction = Action.CREATE).tuple
    assertEquals(456, updated.membersBook["value"])
  }
}
