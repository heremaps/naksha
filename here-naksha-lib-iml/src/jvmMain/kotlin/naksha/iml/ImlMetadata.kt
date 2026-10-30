package naksha.iml

import naksha.base.Id
import naksha.base.TupleNumber
import naksha.base.illegalArg
import naksha.model.Tuple
import naksha.model.objects.MemberList
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
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
  val layersById = LinkedHashMap<String, Layer>()
  val layersByNumber = HashMap<Int, Layer>()
  val synthetic = HashMap<TupleNumber, Tuple>()

  class Layer(val id: String, val schema: String, val table: String, val collection: NakshaCollection, val tuple: Tuple) {
    val number = Id.textToInt(id)
    val sqlTable = "${quote(schema)}.${quote(table)}"
  }

  init {
    if (config.schema.isBlank() || config.metadataSchema.isBlank()) throw illegalArg("IML schema names must not be blank")
    val catalogNumber = Id.textToInt(config.schema)
    if (catalogNumber == Id.ADMIN_CATALOG_INT) throw illegalArg("IML layer schema conflicts with the administrative catalog")
    catalog = NakshaCatalog(config.schema).apply { databaseId = config.id }
    catalogTuple = ImlTuple.encode(catalog, TupleNumber(databaseNumber, Id.ADMIN_CATALOG_INT, Id.CATALOGS_COL_INT, catalogNumber.toLong(), 4))
    catalog.tupleNumber = catalogTuple.tupleNumber
    val adminTuple = ImlTuple.encode(adminCatalog, TupleNumber(databaseNumber, Id.ADMIN_CATALOG_INT, Id.CATALOGS_COL_INT, Id.ADMIN_CATALOG_INT.toLong(), 4))
    adminCatalog.tupleNumber = adminTuple.tupleNumber
    synthetic[catalogTuple.tupleNumber] = catalogTuple
    synthetic[adminTuple.tupleNumber] = adminTuple
    for (collection in arrayOf(catalogsCollection, collectionsCollection)) {
      collection.databaseId = config.id
      collection.featureType = NakshaCollection.FEATURE_TYPE
      collection.storeHistory = StoreMode.OFF
      collection.storeDeleted = StoreMode.OFF
      collection.storeMeta = StoreMode.OFF
      collection.members = MemberList().apply { addAll(XyzMembers.ALL) }
      val parentNumber = Id.textToInt(collection.catalogId!!)
      val tuple = ImlTuple.encode(collection, TupleNumber(databaseNumber, parentNumber, Id.COLLECTIONS_COL_INT, collection.collectionNumber.toLong(), 4))
      collection.tupleNumber = tuple.tupleNumber
      synthetic[tuple.tupleNumber] = tuple
    }
    connection.createStatement().use { statement ->
      statement.fetchSize = 1000
      statement.executeQuery("SELECT id, schem, h_id FROM ${quote(config.metadataSchema)}.space_meta ORDER BY schem, id").use { rows ->
        while (rows.next()) {
          val schema = rows.getString("schem")
          if (schema != config.schema) continue
          val id = rows.getString("id") ?: throw illegalArg("IML metadata contains a null layer ID")
          val table = rows.getString("h_id") ?: throw illegalArg("IML metadata contains a null physical table")
          val number = Id.textToInt(id)
          if (id.isBlank() || table.isBlank() || number == Id.COLLECTIONS_COL_INT) throw illegalArg("Invalid or reserved IML layer identity")
          if (layersById.containsKey(id) || layersByNumber.containsKey(number)) throw illegalArg("Duplicate or colliding IML layer identities")
          val collection = NakshaCollection(id, schema, storeHistory = StoreMode.OFF, storeDeleted = StoreMode.OFF, storeMeta = StoreMode.OFF)
          collection.featureType = NakshaCollection.FEATURE_TYPE
          collection.databaseId = config.id
          collection.members = MemberList().apply { addAll(XyzMembers.ALL) }
          val tuple = ImlTuple.encode(collection, TupleNumber(databaseNumber, catalogNumber, Id.COLLECTIONS_COL_INT, number.toLong(), 4))
          collection.tupleNumber = tuple.tupleNumber
          val layer = Layer(id, schema, table, collection, tuple)
          layersById[id] = layer
          layersByNumber[number] = layer
          synthetic[tuple.tupleNumber] = tuple
        }
      }
    }
  }

  companion object {
    fun quote(identifier: String): String {
      if (identifier.indexOf('\u0000') >= 0) throw illegalArg("Invalid SQL identifier")
      return "\"${identifier.replace("\"", "\"\"")}\""
    }
  }
}
