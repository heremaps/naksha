package naksha.iml

import naksha.base.Id
import naksha.base.TupleNumber
import naksha.base.illegalArg
import naksha.base.proxy
import naksha.model.Tuple
import naksha.model.objects.MemberList
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.objects.XyzMembers
import java.sql.Connection

/**
 * Immutable storage-local discovery snapshot. Physical identifiers come only from metadata and are always quoted.
 * @since 3.0
 */
internal class ImlMetadata(connection: Connection, config: ImlConfig, databaseNumber: Long) {
  val catalog: NakshaCatalog
  val catalogTuple: Tuple
  val adminCatalog = NakshaCatalog(Id.ADMIN_CATALOG_TEXT).apply { databaseId = config.id }
  val catalogsCollection = NakshaCollection(Id.CATALOGS_COL_TEXT, Id.ADMIN_CATALOG_TEXT)
  val collectionsCollection = NakshaCollection(Id.COLLECTIONS_COL_TEXT, config.schema)
  val catalogsById = LinkedHashMap<String, NakshaCatalog>()
  val catalogsByNumber = HashMap<Int, NakshaCatalog>()
  // Legacy read paths use only the configured schema; writer paths use catalog-scoped indexes.
  val layersById = LinkedHashMap<String, Layer>()
  val layersByNumber = HashMap<Int, Layer>()
  val allLayers = LinkedHashMap<Pair<String, String>, Layer>()
  private val allLayersByNumber = HashMap<Pair<Int, Int>, Layer>()
  private val collectionsByCatalog = LinkedHashMap<String, LinkedHashMap<String, NakshaCollection>>()
  val synthetic = HashMap<TupleNumber, Tuple>()

  class Layer(val id: String, val schema: String, val table: String, val collection: NakshaCollection, val tuple: Tuple) {
    val number = Id.textToInt(id)
    val sqlTable = "${quote(schema)}.${quote(table)}"
  }

  init {
    if (config.schema.isBlank() || config.metadataSchema.isBlank()) throw illegalArg("IML schema names must not be blank")
    quote(config.metadataSchema)

    fun encode(feature: NakshaFeature, identity: TupleNumber, persisted: TupleNumber?): Tuple {
      val tn = if (persisted != null && TupleNumber.copy(persisted, storageNumber = identity.databaseNumber, version = identity.version) == identity) identity.copy(version = persisted.version) else identity
      val tuple = ImlTuple.encode(feature, tn)
      synthetic[tuple.tupleNumber] = tuple
      return tuple
    }

    fun addCatalog(descriptor: NakshaCatalog, administrative: Boolean = false) {
      val id = descriptor.id
      val number = Id.textToInt(id)
      if (!administrative && (id.isBlank() || number == Id.ADMIN_CATALOG_INT || id == config.metadataSchema ||
          id.startsWith("pg_", true) || id == "information_schema" || id.toByteArray(Charsets.UTF_8).size > 63)) {
        throw illegalArg("Invalid or reserved IML catalog schema")
      }
      quote(id)
      if (catalogsById.containsKey(id) || catalogsByNumber.containsKey(number)) throw illegalArg("Duplicate or colliding IML catalog identities")
      descriptor.databaseId = config.id
      val tuple = encode(descriptor, TupleNumber(databaseNumber, Id.ADMIN_CATALOG_INT, Id.CATALOGS_COL_INT, number.toLong(), 4), descriptor.tupleNumber)
      descriptor.tupleNumber = tuple.tupleNumber
      catalogsById[id] = descriptor
      catalogsByNumber[number] = descriptor
      collectionsByCatalog[id] = LinkedHashMap()
    }

    val persistedCatalogs = LinkedHashMap<String, NakshaCatalog>()
    val sqlCatalogs = quote(config.metadataSchema) + "." + quote("iml_catalogs")
    val hasCatalogs = connection.prepareStatement("SELECT to_regclass(?)").use { statement ->
      statement.setString(1, sqlCatalogs)
      statement.executeQuery().use { rows -> rows.next() && rows.getString(1) != null }
    }
    if (hasCatalogs) connection.createStatement().use { statement ->
      statement.executeQuery("SELECT id, feature FROM $sqlCatalogs ORDER BY id").use { rows ->
        while (rows.next()) {
          val id = rows.getString("id") ?: throw illegalArg("IML metadata contains a null catalog ID")
          val json = rows.getString("feature") ?: throw illegalArg("IML catalog metadata has no feature body")
          val descriptor = NakshaFeature.fromJson(json).proxy(NakshaCatalog::class)
          descriptor.id = id
          if (persistedCatalogs.put(id, descriptor) != null) throw illegalArg("Duplicate IML catalog identities")
        }
      }
    }
    catalog = persistedCatalogs.remove(config.schema) ?: NakshaCatalog(config.schema)
    addCatalog(catalog)
    catalogTuple = synthetic.getValue(checkNotNull(catalog.tupleNumber))
    addCatalog(adminCatalog, true)
    for (descriptor in persistedCatalogs.values) addCatalog(descriptor)

    for (descriptor in catalogsById.values) {
      val virtual = when (descriptor.id) {
        config.schema -> collectionsCollection
        else -> NakshaCollection(Id.COLLECTIONS_COL_TEXT, descriptor.id)
      }
      val collections = collectionsByCatalog.getValue(descriptor.id)
      val controls = if (descriptor.id == Id.ADMIN_CATALOG_TEXT) arrayOf(virtual, catalogsCollection) else arrayOf(virtual)
      for (collection in controls) {
        collection.databaseId = config.id
        collection.featureType = NakshaCollection.FEATURE_TYPE
        collection.storeHistory = StoreMode.OFF
        collection.storeDeleted = StoreMode.OFF
        collection.storeMeta = StoreMode.OFF
        collection.members = MemberList().apply { addAll(XyzMembers.ALL) }
        val tuple = encode(collection, TupleNumber(databaseNumber, descriptor.catalogNumber, Id.COLLECTIONS_COL_INT, collection.collectionNumber.toLong(), 4), null)
        collection.tupleNumber = tuple.tupleNumber
        collections[collection.id] = collection
      }
    }
    connection.createStatement().use { statement ->
      statement.fetchSize = 1000
      statement.executeQuery("SELECT id, schem, h_id, meta -> 'nakshaCollection' AS collection_feature FROM ${quote(config.metadataSchema)}.${quote("space_meta")} ORDER BY schem, id").use { rows ->
        while (rows.next()) {
          val schema = rows.getString("schem")
          val parent = catalogsById[schema] ?: continue
          if (schema == Id.ADMIN_CATALOG_TEXT) throw illegalArg("IML layers cannot use the administrative catalog")
          val id = rows.getString("id") ?: throw illegalArg("IML metadata contains a null layer ID")
          val table = rows.getString("h_id") ?: throw illegalArg("IML metadata contains a null physical table")
          val number = Id.textToInt(id)
          if (id.isBlank() || table.isBlank() || number == Id.COLLECTIONS_COL_INT) throw illegalArg("Invalid or reserved IML layer identity")
          val key = parent.id to id
          val numberKey = parent.catalogNumber to number
          if (allLayers.containsKey(key) || allLayersByNumber.containsKey(numberKey)) throw illegalArg("Duplicate or colliding IML layer identities")
          val json = rows.getString("collection_feature")
          val collection = if (json == null || json == "null") {
            NakshaCollection(id, parent.id, storeHistory = StoreMode.ON, storeDeleted = StoreMode.ON, storeMeta = StoreMode.OFF).apply {
              members = MemberList().apply { addAll(XyzMembers.ALL) }
            }
          } else NakshaFeature.fromJson(json).proxy(NakshaCollection::class)
          collection.id = id
          collection.catalogId = parent.id
          collection.featureType = NakshaCollection.FEATURE_TYPE
          collection.databaseId = config.id
          val tuple = encode(collection, TupleNumber(databaseNumber, parent.catalogNumber, Id.COLLECTIONS_COL_INT, number.toLong(), 4), collection.tupleNumber)
          collection.tupleNumber = tuple.tupleNumber
          val layer = Layer(id, parent.id, table, collection, tuple)
          allLayers[key] = layer
          allLayersByNumber[numberKey] = layer
          collectionsByCatalog.getValue(parent.id)[id] = collection
          if (schema == config.schema) {
            layersById[id] = layer
            layersByNumber[number] = layer
          }
        }
      }
    }
  }

  /** Returns a physical layer in the specified catalog. @since 3.0 */
  fun getLayer(catalogId: String, collectionId: String): Layer? = allLayers[catalogId to collectionId]

  /** Returns a physical layer by its catalog-scoped numeric identity. @since 3.0 */
  fun getLayerByNumber(catalogNumber: Int, collectionNumber: Int): Layer? = allLayersByNumber[catalogNumber to collectionNumber]

  /** Returns a catalog descriptor from this snapshot. @since 3.0 */
  fun getCatalogById(catalogId: String): NakshaCatalog? = catalogsById[catalogId]

  /** Returns a catalog descriptor by its numeric identity. @since 3.0 */
  fun getCatalogByNumber(catalogNumber: Int): NakshaCatalog? = catalogsByNumber[catalogNumber]

  /** Returns a physical or virtual collection descriptor from this snapshot. @since 3.0 */
  fun collectionById(catalogId: String, id: String): NakshaCollection? = collectionsByCatalog[catalogId]?.get(id)

  /** Lists all physical and virtual collection descriptors in the catalog. @since 3.0 */
  fun getCollections(catalogId: String): List<NakshaCollection> = collectionsByCatalog[catalogId]?.values?.toList() ?: emptyList()

  companion object {
    fun quote(identifier: String): String {
      if (identifier.indexOf('\u0000') >= 0) throw illegalArg("Invalid SQL identifier")
      return "\"${identifier.replace("\"", "\"\"")}\""
    }
  }
}
