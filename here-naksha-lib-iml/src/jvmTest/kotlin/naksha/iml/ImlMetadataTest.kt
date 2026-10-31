package naksha.iml

import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import naksha.base.Id
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.TupleNumber
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StoreMode
import naksha.model.objects.XyzMembers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Database-free discovery tests, including optional writer-owned metadata. @since 3.0 */
class ImlMetadataTest {
  private val config = ImlConfig("metadata-test", "jdbc:postgresql://localhost/test").apply {
    metadataSchema = "metadata\";--"
  }

  @Test
  fun `absent writer table discovers only default schema and virtual controls`() {
    val metadata = snapshot(layers = listOf(layer("roads", "public"), layer("ignored", "external", null)))
    assertEquals(setOf("public", Id.ADMIN_CATALOG_TEXT), metadata.catalogsById.keys)
    assertSame(metadata.catalog, metadata.getCatalogByNumber(Id.textToInt("public")))
    assertEquals(setOf("roads"), metadata.layersById.keys)
    assertEquals(1, metadata.allLayers.size)
    assertNull(metadata.getLayer("external", "ignored"))
    assertSame(metadata.collectionsCollection, metadata.collectionById("public", Id.COLLECTIONS_COL_TEXT))
    assertSame(metadata.catalogsCollection, metadata.collectionById(Id.ADMIN_CATALOG_TEXT, Id.CATALOGS_COL_TEXT))
    assertNotNull(metadata.collectionById(Id.ADMIN_CATALOG_TEXT, Id.COLLECTIONS_COL_TEXT))
    assertEquals(2, metadata.getCollections("public").size)
    assertEquals(emptyList<NakshaCollection>(), metadata.getCollections("missing"))
    val source = checkNotNull(metadata.getLayer("public", "roads"))
    assertEquals("\"public\".\"physical\"\";--\"", source.sqlTable)
    assertEquals(StoreMode.ON, source.collection.storeHistory)
    assertEquals(StoreMode.ON, source.collection.storeDeleted)
    assertEquals(XyzMembers.ALL.size, source.collection.useMembers().size)
    assertEquals(4L, source.tuple.tupleNumber.version)
    for (catalog in metadata.catalogsById.values) assertNotNull(metadata.synthetic[catalog.tupleNumber])
  }

  @Test
  fun `same collection numbers in different catalogs and empty catalogs are supported`() {
    val metadata = snapshot(listOf(catalog("writer"), catalog("empty")), listOf(layer("roads", "public"), layer("roads", "writer")))
    val source = checkNotNull(metadata.getLayer("public", "roads"))
    val owned = checkNotNull(metadata.getLayer("writer", "roads"))
    assertEquals(source.number, owned.number)
    assertSame(source, metadata.layersByNumber[source.number])
    assertSame(source, metadata.layersById["roads"])
    assertSame(owned, metadata.allLayers["writer" to "roads"])
    assertSame(owned, metadata.getLayerByNumber(Id.textToInt("writer"), owned.number))
    assertEquals(2, metadata.getCollections("writer").size)
    assertEquals(1, metadata.getCollections("empty").size)
    assertNull(metadata.getLayerByNumber(Id.textToInt("empty"), owned.number))
    assertNotNull(metadata.getCatalogById("empty"))
  }

  @Test
  fun `persisted full descriptors and packed versions survive discovery`() {
    val catalogTn = TupleNumber(11, Id.ADMIN_CATALOG_INT, Id.CATALOGS_COL_INT, Id.textToInt("public").toLong(), 37)
    val descriptor = NakshaCatalog("public").apply {
      properties.setRaw("settings", Platform.fromJSON("""{"nested":[1,2,3]}"""))
      properties.xyz.setRaw("updatedAt", 1234L)
    }
    val collectionTn = TupleNumber(11, Id.textToInt("public"), Id.COLLECTIONS_COL_INT, Id.textToInt("roads").toLong(), 41)
    val collection = NakshaCollection("roads", "public", storeHistory = StoreMode.OFF, storeDeleted = StoreMode.OFF, storeMeta = StoreMode.ON).apply {
      featureType = NakshaCollection.FEATURE_TYPE
      members = naksha.model.objects.MemberList().apply { addAll(XyzMembers.ALL) }
      properties.setRaw("settings", Platform.fromJSON("""{"keep":true}"""))
      setRaw("extra", Platform.fromJSON("""{"indexes":["custom"]}"""))
      properties.xyz.setRaw("updatedAt", 5678L)
    }
    val catalogJson = Platform.toJSON(ImlTuple.encode(descriptor, catalogTn).decodeFeature(null))
    val collectionJson = Platform.toJSON(ImlTuple.encode(collection, collectionTn).decodeFeature(null))
    val metadata = snapshot(listOf(mapOf("id" to "public", "feature" to catalogJson)), listOf(layer("roads", "public", feature = collectionJson)))
    assertEquals(catalogTn, metadata.catalog.tupleNumber)
    assertEquals(catalogTn, metadata.catalogTuple.tupleNumber)
    assertEquals(1234L, (metadata.catalog.properties.xyz.getRaw("updatedAt") as Number).toLong())
    assertEquals(Platform.toJSON(descriptor.properties.getRaw("settings")), Platform.toJSON(metadata.catalog.properties.getRaw("settings")))
    val discovered = checkNotNull(metadata.collectionById("public", "roads"))
    assertEquals(collectionTn, discovered.tupleNumber)
    assertEquals(StoreMode.OFF, discovered.storeHistory)
    assertEquals(StoreMode.OFF, discovered.storeDeleted)
    assertEquals(StoreMode.ON, discovered.storeMeta)
    assertEquals(Platform.toJSON(collection.members), Platform.toJSON(discovered.members))
    assertEquals(Platform.toJSON(collection.properties.getRaw("settings")), Platform.toJSON(discovered.properties.getRaw("settings")))
    assertEquals(Platform.toJSON(collection.getRaw("extra")), Platform.toJSON(discovered.getRaw("extra")))
    assertEquals(5678L, (discovered.properties.xyz.getRaw("updatedAt") as Number).toLong())
    assertNotNull(metadata.synthetic[collectionTn])
    descriptor.properties.setRaw("settings", null)
    assertNotNull(metadata.catalog.properties.getRaw("settings"))
  }

  @Test
  fun `persisted revisions survive rebasing onto a different storage identity`() {
    val foreign = TupleNumber(99, Id.ADMIN_CATALOG_INT, Id.CATALOGS_COL_INT, Id.textToInt("writer").toLong(), 37)
    val json = Platform.toJSON(ImlTuple.encode(NakshaCatalog("writer"), foreign).decodeFeature(null))
    val metadata = snapshot(listOf(mapOf("id" to "writer", "feature" to json)))
    val tn = checkNotNull(metadata.catalogsById["writer"]?.tupleNumber)
    assertEquals(11L, tn.databaseNumber)
    assertEquals(37L, tn.version)
  }

  @Test
  fun `catalog and catalog scoped layer collisions and reserved identities are rejected`() {
    assertEquals(Id.textToInt("1"), Id.textToInt("2147483649"))
    assertThrows(NakshaException::class.java) { snapshot(listOf(catalog("1"), catalog("2147483649"))) }
    assertThrows(NakshaException::class.java) { snapshot(layers = listOf(layer("1", "public"), layer("2147483649", "public"))) }
    assertThrows(NakshaException::class.java) { snapshot(layers = listOf(layer("roads", "public"), layer("roads", "public"))) }
    for (reserved in listOf(Id.ADMIN_CATALOG_TEXT, config.metadataSchema, "pg_catalog", "information_schema", " ")) {
      assertThrows(NakshaException::class.java) { snapshot(listOf(catalog(reserved))) }
    }
    assertThrows(NakshaException::class.java) { snapshot(layers = listOf(layer(Id.COLLECTIONS_COL_TEXT, "public"))) }
    assertThrows(NakshaException::class.java) { snapshot(layers = listOf(layer("roads", "public", null))) }
    assertThrows(NakshaException::class.java) { snapshot(layers = listOf(layer("roads", "public", "bad\u0000table"))) }
  }

  private fun catalog(id: String) = mapOf("id" to id, "feature" to Platform.toJSON(NakshaCatalog(id)))

  private fun layer(id: String, schema: String, table: String? = "physical\";--", feature: String? = null) =
    mapOf("id" to id, "schem" to schema, "h_id" to table, "collection_feature" to feature)

  private fun snapshot(catalogs: List<Map<String, String?>>? = null, layers: List<Map<String, String?>> = emptyList()): ImlMetadata {
    val sqlCatalogs = "${ImlMetadata.quote(config.metadataSchema)}.\"iml_catalogs\""
    val connection = jdbc(Connection::class.java) { method, args ->
      when (method) {
        "prepareStatement" -> {
          assertEquals("SELECT to_regclass(?)", args[0])
          jdbc(PreparedStatement::class.java) { call, parameters ->
            when (call) {
              "setString" -> { assertEquals(1, parameters[0]); assertEquals(sqlCatalogs, parameters[1]); null }
              "executeQuery" -> rows(listOf(mapOf("1" to if (catalogs == null) null else sqlCatalogs)))
              "close" -> null
              else -> error("Unexpected JDBC call: $call")
            }
          }
        }
        "createStatement" -> jdbc(Statement::class.java) { call, parameters ->
          when (call) {
            "setFetchSize", "close" -> null
            "executeQuery" -> {
              val sql = parameters[0] as String
              when {
                sql == "SELECT id, feature FROM $sqlCatalogs ORDER BY id" -> rows(checkNotNull(catalogs))
                sql.startsWith("SELECT id, schem, h_id, meta -> 'nakshaCollection'") -> rows(layers)
                else -> error("Unexpected SQL: $sql")
              }
            }
            else -> error("Unexpected JDBC call: $call")
          }
        }
        else -> error("Unexpected JDBC call: $method")
      }
    }
    return ImlMetadata(connection, config, 11)
  }

  private fun rows(records: List<Map<String, String?>>): ResultSet {
    var index = -1
    return jdbc(ResultSet::class.java) { method, args ->
      when (method) {
        "next" -> ++index < records.size
        "getString" -> records[index][args[0].toString()]
        "close" -> null
        else -> error("Unexpected JDBC call: $method")
      }
    }
  }

  private fun <T> jdbc(type: Class<T>, handler: (String, Array<out Any?>) -> Any?): T =
    type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> handler(method.name, args ?: emptyArray()) })
}
