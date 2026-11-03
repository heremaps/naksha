package naksha.iml.parity

import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.iml.ImlTestFixture
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Phase 1 usability probes: what a newcomer runs into with the minimal README usage against a Hub database.
 * Each probe prints `PARITY|usability|<probe>|<outcome>`; the probes do not assert, they record.
 * Gated like HubReadbackTest (IML_PARITY_HUB_URL + NAKSHA_TEST_IML_DB_URL, loopback only). Read-only.
 */
class UsabilityProbeTest {
  private val dbUrl: String? = System.getenv("NAKSHA_TEST_IML_DB_URL")

  private fun probe(name: String, block: () -> Any?) {
    val outcome = runCatching(block).fold(
      { "ok: ${it.toString().take(200)}" },
      { "${it::class.qualifiedName}: ${it.message?.lineSequence()?.firstOrNull()?.take(200)}" })
    println("PARITY|usability|$name|$outcome")
  }

  @Test
  fun `newcomer pitfalls`() {
    assumeTrue(!System.getenv("IML_PARITY_HUB_URL").isNullOrBlank() && !dbUrl.isNullOrBlank(), "Set IML_PARITY_HUB_URL and NAKSHA_TEST_IML_DB_URL")
    ImlTestFixture.validateLocalUrl(dbUrl!!)
    val config = ImlConfig("iml-eval-usability", dbUrl)
    try {
      // 1. No appId on the thread-local context (README says to set one).
      val context = NakshaContext.currentContext()
      val previous = context.getAppIdOr("")
      probe("no-appId-useStorage+read") {
        // Fresh thread: no appId set, as in the first README snippet without withAppId(...).
        val result = arrayOfNulls<String>(1)
        val thread = Thread {
          result[0] = runCatching {
            val s = (Naksha.useStorage(config) as ImlStorage).newReadSession()
            s.use { (it.executeRead(ReadFeatures().apply { catalogId = "public"; collectionId = "ev-hist"; limit = 1 }) as SuccessResponse).features.size }
          }.fold({ "ok size=$it" }, { "${it::class.qualifiedName}: ${it.message}" })
        }
        thread.start(); thread.join()
        result[0]
      }
      context.withAppId(previous.ifBlank { "iml-eval-usability" })
      val storage = Naksha.useStorage(config) as ImlStorage

      // 2. setupStorage instead of useStorage (forces create+upgrade).
      probe("setupStorage") { Naksha.setupStorage(ImlConfig("iml-eval-usability-setup", dbUrl)) }

      // 3. Physical table name instead of the logical space ID.
      val table = java.sql.DriverManager.getConnection(dbUrl).use { c ->
        c.createStatement().use { st -> st.executeQuery("SELECT h_id FROM xyz_config.space_meta WHERE id='ev-hist'").use { it.next(); it.getString(1) } }
      }
      probe("collectionId=physical-table") {
        storage.newReadSession().use { s -> s.executeRead(ReadFeatures().apply { catalogId = "public"; collectionId = table }) }
      }

      // 4. Missing catalogId (IML users think in layers, not catalogs).
      probe("missing-catalogId") { storage.newReadSession().use { s -> s.executeRead(ReadFeatures().apply { collectionId = "ev-hist" }) } }

      // 5. Accessing response.features after the session is closed (two-phase lazy loading).
      probe("features-after-session-close") {
        val response = storage.newReadSession().use { s ->
          s.executeRead(ReadFeatures().apply { catalogId = "public"; collectionId = "ev-hist"; limit = 3 }) as SuccessResponse
        }
        Naksha.cache.clear(storage)
        response.features.map { it?.id }
      }

      // 6. What does a ReadFeatures without limit return? (default limit = hardCap = 16,777,216 rows)
      probe("default-limit") { storage.hardCap }

      // 7. Feature version as the user sees it vs the Hub's @ns version.
      probe("version-encoding") {
        storage.newReadSession().use { s ->
          val f = (s.executeRead(ReadFeatures().apply { catalogId = "public"; collectionId = "ev-hist"; featureIds.add("pt-2d") }) as SuccessResponse).features[0]
          val v = f?.properties?.xyz?.getRaw("version")
          "xyz.version=$v (hub @ns.version=5); v ushr 2 = ${(v as Number).toLong() ushr 2}; uuid=${f.properties.xyz.getRaw("uuid")}"
        }
      }

      // 8. Errors are thrown (not returned as ErrorResponse) for unknown layers.
      probe("unknown-layer") { storage.newReadSession().use { s -> s.executeRead(ReadFeatures().apply { catalogId = "public"; collectionId = "nope" }) } }
    } finally {
      Naksha.removeStorage(config)
    }
  }
}
