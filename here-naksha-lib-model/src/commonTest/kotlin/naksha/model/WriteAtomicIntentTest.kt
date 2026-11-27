@file:Suppress("DEPRECATION")

package naksha.model

import naksha.base.TupleNumber
import naksha.base.Version
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.Write
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WriteAtomicIntentTest {
  private val catalog = NakshaCatalog("atomic_catalog")
  private val collection = NakshaCollection("atomic_collection", catalog.id)
  private val feature = NakshaFeature("atomic_feature")

  @Test
  fun booleanHelpersReplaceAtomicIntentAndKeepMissingVersion() {
    val helpers = listOf<(Write, Boolean) -> Write>(
      { w, a -> w.updateCatalog(catalog, a) },
      { w, a -> w.upsertCatalog(catalog, a) },
      { w, a -> w.deleteCatalog(catalog, a) },
      { w, a -> w.updateMap(catalog, a) },
      { w, a -> w.upsertMap(catalog, a) },
      { w, a -> w.deleteMap(catalog, a) },
      { w, a -> w.updateCollection(collection, a) },
      { w, a -> w.upsertCollection(collection, a) },
      { w, a -> w.deleteCollection(collection, a) },
      { w, a -> w.updateFeature(collection, feature, a) },
      { w, a -> w.updateFeature(catalog.id, collection.id, feature, a) },
      { w, a -> w.upsertFeature(collection, feature, a) },
      { w, a -> w.deleteFeature(collection, feature, a) }
    )
    for (helper in helpers) {
      val write = Write().apply { atomic = true; version = 100L }
      assertSame(write, helper(write, false))
      assertFalse(write.atomic)
      assertNull(write.version)
      helper(write, true)
      assertTrue(write.atomic)
      assertNull(write.version)
    }
    val tn = TupleNumber(1L, 2, 3, 4L, 100L)
    catalog.tupleNumber = tn
    collection.tupleNumber = tn
    feature.tupleNumber = tn
    for (helper in helpers) {
      val write = helper(Write(), true)
      assertTrue(write.atomic)
      assertEquals(tn.version, write.version)
    }
  }

  @Test
  fun versionHelpersReplaceAtomicIntentAndUseExplicitVersion() {
    val helpers = listOf<(Write, Version?) -> Write>(
      { w, v -> w.deleteCatalogById(catalog.id, v) },
      { w, v -> w.deleteMapById(catalog.id, v) },
      { w, v -> w.deleteCollectionById(catalog.id, collection.id, v) },
      { w, v -> w.deleteFeatureById(collection, feature.id, v) },
      { w, v -> w.deleteFeatureById(catalog.id, collection.id, feature.id, v) },
      { w, v -> w.upsertFeature(catalog.id, collection.id, feature, v) }
    )
    for (helper in helpers) {
      val write = Write().apply { atomic = true; version = 200L }
      helper(write, null)
      assertFalse(write.atomic)
      assertNull(write.version)
      helper(write, Version(100L))
      assertTrue(write.atomic)
      assertEquals(100L, write.version)
    }
  }

  @Test
  fun createAndPurgeHelpersClearAtomicExpectation() {
    val helpers = listOf<(Write) -> Write>(
      { it.createCatalog(catalog) },
      { it.createMap(catalog) },
      { it.createCollection(collection) },
      { it.createFeature(collection, feature) },
      { it.createFeature(catalog.id, collection.id, feature) },
      { it.purgeFeature(collection, feature, true) },
      { it.purgeFeature(collection, feature, false) },
      { it.purgeFeatureById(collection, feature.id, Version(100L)) },
      { it.purgeFeatureById(collection, feature.id) }
    )
    for (helper in helpers) {
      val write = Write().apply { atomic = true; version = 200L }
      assertSame(write, helper(write))
      assertFalse(write.atomic)
      assertNull(write.version)
      assertNull(write.getRaw("atomic"))
    }
  }
}
