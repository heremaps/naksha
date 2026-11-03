package naksha.iml.parity

import naksha.base.Platform
import naksha.geo.SpLineString
import naksha.geo.SpPoint
import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.iml.ImlTestFixture
import naksha.iml.ImlWriteSession
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.geo.LineStringCoord
import naksha.geo.PointCoord
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.DriverManager
import java.util.UUID

/**
 * Phase 1 (b): can lib-iml alone bootstrap an IML-compatible database (writeEnabled + create), and can XYZ Hub then
 * serve what lib-iml wrote?
 *
 * Isolation: the metadata schema is a fresh scratch schema (`imlb_meta_<rand>`); the layer table is created by lib-iml
 * in `public` (the only schema the local Hub connector reads), named by lib-iml, and dropped at the end together with
 * the scratch schema and the Hub space. Hub data of the seeded spaces is not touched.
 * Records results as `PARITY|bootstrap|...` lines; asserts nothing beyond the setup succeeding.
 */
class BootstrapCompatTest {
  private val hubUrl: String? = System.getenv("IML_PARITY_HUB_URL")
  private val dbUrl: String? = System.getenv("NAKSHA_TEST_IML_DB_URL")
  private val http = HttpClient.newHttpClient()

  private fun report(message: String) = println("PARITY|bootstrap|$message")

  private fun hub(method: String, path: String, body: String? = null, type: String = "application/json"): Pair<Int, String> {
    val builder = HttpRequest.newBuilder(URI(hubUrl!!.trimEnd('/') + path)).header("Accept", "application/json")
    val request = if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
    else builder.header("Content-Type", type).method(method, HttpRequest.BodyPublishers.ofString(body)).build()
    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    return response.statusCode() to response.body()
  }

  private fun sql(query: String): List<String> = DriverManager.getConnection(dbUrl).use { c ->
    c.createStatement().use { st ->
      st.executeQuery(query).use { rs ->
        val out = ArrayList<String>()
        val n = rs.metaData.columnCount
        while (rs.next()) out += (1..n).joinToString(" | ") { rs.getString(it) ?: "null" }
        out
      }
    }
  }

  @Test
  fun `lib-iml bootstrap and Hub interoperability`() {
    assumeTrue(!hubUrl.isNullOrBlank() && !dbUrl.isNullOrBlank(), "Set IML_PARITY_HUB_URL and NAKSHA_TEST_IML_DB_URL")
    require(URI(hubUrl!!).host in setOf("localhost", "127.0.0.1"))
    ImlTestFixture.validateLocalUrl(dbUrl!!)
    val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
    val meta = "imlb_meta_$suffix"
    val layerId = "boot-$suffix"
    val spaceId = "ev-boot-$suffix"
    val config = ImlConfig("iml-eval-boot-$suffix", dbUrl).apply {
      schema = "public"
      metadataSchema = meta
      writeEnabled = true
      create = true
      upgrade = false
    }
    val context = NakshaContext.currentContext().withAppId("iml-eval-boot")
    val previousSu = context.su
    var table: String? = null
    try {
      context.su = true
      val storage = Naksha.useStorage(config) as ImlStorage
      report("bootstrap ok: metadata schema $meta")
      val collection = NakshaCollection(layerId, "public")
      (storage.newWriteSession() as ImlWriteSession).use { writer ->
        val request = WriteRequest().apply {
          returnResults = true
          add(Write().createCollection(collection))
          add(Write().createFeature(collection, NakshaFeature("p1").withGeometry(SpPoint(8.5, 50.5, 1.5)).apply { properties.setRaw("name", "point") }))
          add(Write().createFeature(collection, NakshaFeature("l1").withGeometry(SpLineString(LineStringCoord(PointCoord(8.25, 50.25), PointCoord(8.75, 50.75)))).apply { properties.setRaw("name", "line") }))
        }
        val response = writer.executeWrite(request)
        report("write response: ${response::class.simpleName} ${(response as? SuccessResponse)?.length}")
        writer.commit()
      }
      table = sql("SELECT h_id FROM \"$meta\".space_meta WHERE id = '$layerId'").single()
      report("space_meta row: " + sql("SELECT id, schem, h_id, meta::text FROM \"$meta\".space_meta WHERE id = '$layerId'").single().take(300))
      report("control objects in $meta: " + sql("SELECT relname || ':' || relkind::text FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE nspname='$meta' ORDER BY 1"))
      report("table kind (r=plain, p=partitioned): " + sql("SELECT relkind::text FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE nspname='public' AND relname='$table'"))
      report("partitions: " + sql("SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid=i.inhrelid JOIN pg_class p ON p.oid=i.inhparent WHERE p.relname='$table'"))
      report("columns: " + sql("SELECT attname || ' ' || format_type(atttypid, atttypmod) || CASE WHEN attnotnull THEN ' NOT NULL' ELSE '' END FROM pg_attribute WHERE attrelid = 'public.\"$table\"'::regclass AND attnum > 0 AND NOT attisdropped ORDER BY attnum"))
      report("constraints: " + sql("SELECT conname || ' ' || pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'public.\"$table\"'::regclass"))
      report("indexes: " + sql("SELECT indexdef FROM pg_indexes WHERE schemaname='public' AND tablename='$table'"))
      report("sequences named like the table: " + sql("SELECT relname FROM pg_class WHERE relkind='S' AND relname LIKE '${table}%'"))
      report("rows: " + sql("SELECT id || ' v=' || version || ' nv=' || next_version || ' op=' || operation::text || ' i=' || i || ' author=' || coalesce(author,'null') || ' geo=' || coalesce(ST_AsText(geo),'null') || ' json=' || left(jsondata::text, 160) FROM public.\"$table\" ORDER BY id"))

      // Can the Hub serve this table? Register a space that points at it.
      val (createStatus, createBody) = hub("POST", "/spaces", """{"id":"$spaceId","title":"lib-iml bootstrap","versionsToKeep":100,"storage":{"id":"psql","params":{"tableName":"$table"}}}""")
      report("hub POST /spaces with storage.params.tableName=$table -> $createStatus ${createBody.take(400)}")
      if (createStatus == 200) {
        val assigned = (Platform.fromJSON(createBody) as Map<*, *>).let { ((it["storage"] as Map<*, *>)["params"] as Map<*, *>?)?.get("tableName") }
        report("hub assigned tableName=$assigned (requested $table, same=${assigned == table})")
        val (iterStatus, iterBody) = hub("GET", "/spaces/$spaceId/iterate?limit=10")
        report("hub GET /iterate -> $iterStatus ${iterBody.take(600)}")
        val (byIdStatus, byIdBody) = hub("GET", "/spaces/$spaceId/features/p1")
        report("hub GET /features/p1 -> $byIdStatus ${byIdBody.take(400)}")
        val (writeStatus, writeBody) = hub("PUT", "/spaces/$spaceId/features", """{"type":"FeatureCollection","features":[{"type":"Feature","id":"hub1","geometry":{"type":"Point","coordinates":[8.6,50.6]},"properties":{"name":"from hub"}}]}""", "application/geo+json")
        report("hub PUT feature into lib-iml table -> $writeStatus ${writeBody.take(400)}")
        report("hub space_meta row (xyz_config): " + sql("SELECT id, h_id, meta::text FROM xyz_config.space_meta WHERE id = '$spaceId'"))
        report("relations after hub create: " + sql("SELECT relname || ':' || relkind::text FROM pg_class WHERE relname LIKE '${assigned ?: table}%' ORDER BY 1"))
      }
    } finally {
      try { hub("DELETE", "/spaces/$spaceId") } catch (_: Exception) {}
      try { Naksha.removeStorage(config) } finally {
        context.su = previousSu
        DriverManager.getConnection(dbUrl).use { c ->
          c.createStatement().use { st ->
            if (table != null) st.execute("DROP TABLE IF EXISTS public.\"$table\" CASCADE")
            st.execute("DROP SCHEMA IF EXISTS \"$meta\" CASCADE")
          }
        }
        report("cleanup done: " + sql("SELECT count(*) FROM pg_namespace WHERE nspname = '$meta'").single() + " scratch schemas left")
      }
    }
  }
}
