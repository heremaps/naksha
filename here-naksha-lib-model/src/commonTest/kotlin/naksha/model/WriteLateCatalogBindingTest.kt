package naksha.model

import naksha.base.Id
import naksha.base.Version
import naksha.model.objects.NakshaCollection
import naksha.model.request.Write
import naksha.model.request.WriteOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WriteLateCatalogBindingTest {
  @Test
  fun deleteByIdAllowsLateCatalogBindingWithoutRetainingAPreviousTarget() {
    val write = Write().deleteFeatureById(NakshaCollection("features", "previous"), "first")
    write.deleteFeatureById(NakshaCollection("features"), "second")
    assertNull(write.catalogId)
    assertEquals(Id("features"), write.collectionId)
    assertEquals(Id("second"), write.id)
    assertEquals(WriteOp.DELETE, write.op)
    assertFalse(write.atomic)
    assertNull(write.version)
    write.catalogId = Id("resolved")
    assertEquals(Id("resolved"), write.catalogId)
  }

  @Test
  fun lateCatalogBindingPreservesAnExplicitAtomicVersion() {
    val write = Write().deleteFeatureById(NakshaCollection("features"), "feature", Version(100L))
    assertNull(write.catalogId)
    assertTrue(write.atomic)
    assertEquals(100L, write.version)
  }
}
