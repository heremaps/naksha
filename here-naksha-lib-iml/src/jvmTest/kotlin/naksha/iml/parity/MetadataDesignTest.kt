package naksha.iml.parity

import naksha.base.Platform
import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.iml.parity.EdgeSupport.case
import naksha.iml.parity.Parity.observe
import naksha.model.Naksha
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Phase 4, topic "the metadata cache vs Hub's space config".
 *
 * lib-iml builds its layer descriptors from `xyz_config.space_meta` only (`ImlMetadata.kt:113-130`). The Hub's authoritative
 * layer configuration (versionsToKeep, extends, readOnly, searchableProperties, …) lives in its space config store
 * (DynamoDB in this stack; `SpaceConfigClient`). This test puts the two side by side for the Phase 1 spaces:
 * what does a Naksha client learn about a layer through lib-iml, compared with what the Hub knows?
 *
 * Uses the seeded spaces `ev-v1`, `ev-hist`, `ev-ext`, `ev-big` (`scripts/seed-all.sh`, `scripts/seed-p2.py`). Read-only.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Timeout(value = 5, unit = java.util.concurrent.TimeUnit.MINUTES)
class MetadataDesignTest {
  private lateinit var config: ImlConfig
  private lateinit var storage: ImlStorage

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    val (c, s) = Parity.open("iml-eval-p4-meta")
    config = c
    storage = s
  }

  @AfterAll
  fun tearDown() {
    if (::config.isInitialized) Naksha.removeStorage(config)
  }

  @Test
  fun `layer descriptor vs Hub space config`() {
    val o = LinkedHashMap<String, Any?>()
    storage.newReadSession().use { session ->
      val catalog = checkNotNull(session.getCatalogById("public"))
      for (space in listOf("ev-v1", "ev-hist", "ev-ext", "ev-big")) {
        val hub = Parity.hubOk("/spaces/$space").json as Map<*, *>
        val collection = checkNotNull(session.getCollectionById(catalog, space))
        val described = Platform.fromJSON(Platform.toJSON(collection)) as Map<*, *>
        val meta = EdgeSupport.scalar("SELECT meta::text FROM xyz_config.space_meta WHERE id = ?", space)
        val row = linkedMapOf<String, Any?>(
          "hub.versionsToKeep" to hub["versionsToKeep"],
          "hub.extends" to hub["extends"],
          "hub.searchableProperties" to hub["searchableProperties"],
          "hub.readOnly" to hub["readOnly"],
          "hub.keys" to hub.keys.sortedBy { it.toString() },
          "space_meta.meta" to meta,
          "lib.storeHistory" to collection.storeHistory.toString(),
          "lib.storeDeleted" to collection.storeDeleted.toString(),
          "lib.maxAge" to collection.maxAge,
          "lib.indices" to collection.indices?.size,
          "lib.properties" to (described["properties"] as Map<*, *>?)?.keys?.sortedBy { it.toString() },
          "lib.descriptor keys" to described.keys.sortedBy { it.toString() }
        )
        o[space] = row
        case(
          "P4-meta", "descriptor of $space",
          "Hub: versionsToKeep=${hub["versionsToKeep"]}, extends=${Platform.toJSON(hub["extends"])}, searchable=${Platform.toJSON(hub["searchableProperties"])}",
          "lib: storeHistory=${collection.storeHistory}, storeDeleted=${collection.storeDeleted}; space_meta.meta=$meta; no versionsToKeep/extends/searchable in the descriptor",
          if (hub["versionsToKeep"] == 1 || hub["extends"] != null) "Medium" else "Low"
        )
      }
    }
    o["xyz_config tables"] = EdgeSupport.db { conn ->
      conn.createStatement().use { st ->
        st.executeQuery("SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'xyz_config' AND c.relkind IN ('r','p') ORDER BY 1").use { rs ->
          generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
        }
      }
    }
    observe("p4-metadata-descriptors", o)
  }
}
