@file:Suppress("OPT_IN_USAGE")

package naksha.iml

import java.security.MessageDigest
import java.sql.Connection
import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.PlatformMap
import naksha.base.MapProxy
import naksha.base.TupleNumber
import naksha.geo.SpGeometry
import naksha.geo.SpType
import naksha.base.illegalArg
import naksha.base.proxy
import naksha.base.unsupportedOp
import naksha.model.Tuple
import naksha.model.XyzNs
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import naksha.model.objects.Index
import naksha.model.objects.XyzIndices
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.objects.StoreMode
import naksha.model.objects.XyzMembers
import naksha.model.request.Write
import naksha.model.request.WriteOp

/** Executes one mutation inside the caller's request savepoint. SQL names always come from quoted discovery. @since 3.0 */
internal class ImlMutation(private val session: ImlWriteSession, private val connection: Connection,
  private val metadata: ImlMetadata, private val time: Long, private val version: Long) {
  private val storage = session.storage
  private val control = ImlMetadata.quote(storage.config.metadataSchema)
  var metadataChanged = false
    private set

  fun execute(write: Write): Tuple? = when {
    write.isMapModification() -> catalog(write)
    write.isCollectionModification() -> collection(write)
    else -> feature(write)
  }

  private fun verify(write: Write, current: TupleNumber?, live: Boolean, exists: String, missing: String) {
    if (write.op == WriteOp.CREATE && live) throw NakshaException(exists, "IML object '${write.id}' already exists")
    if (write.op == WriteOp.UPDATE && !live) throw NakshaException(missing, "IML object '${write.id}' not found")
    if (!write.atomic || write.op == WriteOp.CREATE || write.op == WriteOp.UPSERT) return
    val expected = write.version?.number ?: write.tupleNumber?.version ?: throw illegalArg("Atomic IML write requires an expected version")
    val identity = write.tupleNumber
    if (current == null || (current.version ushr 2) != (expected ushr 2) ||
      (identity != null && (identity.databaseNumber != current.databaseNumber || identity.catalogNumber != current.catalogNumber ||
        identity.collectionNumber != current.collectionNumber || identity.featureNumber != current.featureNumber))) {
      throw NakshaException(NakshaError.CONFLICT, "IML object '${write.id}' no longer has the expected identity/version")
    }
  }

  private fun catalog(write: Write): Tuple? {
    val id = write.id
    if (id == Id.ADMIN_CATALOG_TEXT || id == storage.config.metadataSchema || id.startsWith("pg_", true) || id == "information_schema") throw illegalArg("Reserved IML catalog schema")
    if (id.toByteArray(Charsets.UTF_8).size > 63) throw illegalArg("IML catalog schemas cannot exceed PostgreSQL's 63-byte identifier limit")
    val current = metadata.catalogsById[id]
    if (id == "public" && (current == null || write.op == WriteOp.CREATE || write.op == WriteOp.DELETE || write.op == WriteOp.PURGE)) throw illegalArg("Cannot create or drop the public IML catalog schema")
    verify(write, current?.tupleNumber, current != null, NakshaError.CATALOG_EXISTS, NakshaError.MAP_NOT_FOUND)
    val deleting = write.op == WriteOp.DELETE || write.op == WriteOp.PURGE
    if (deleting && current == null) return null
    val owned = connection.prepareStatement("SELECT 1 FROM $control.iml_catalogs WHERE id = ?").use {
      it.setString(1, id)
      it.executeQuery().use { rows -> rows.next() }
    }
    val body = if (deleting) NakshaCatalog(id) else copy(write).proxy(NakshaCatalog::class)
    body.databaseId = storage.config.id
    val operation = if (deleting) "D" else if (current == null) "I" else "U"
    val tuple = ImlTuple.encode(body, TupleNumber(storage.number, Id.ADMIN_CATALOG_INT, Id.CATALOGS_COL_INT, Id.textToInt(id).toLong(), ImlTuple.version(version, operation)))
    if (deleting) {
      if (!owned || id == storage.config.schema) throw illegalArg("Cannot drop an externally managed IML catalog")
      connection.prepareStatement("SELECT h_id FROM $control.space_meta WHERE schem = ?").use {
        it.setString(1, id)
        it.executeQuery().use { rows ->
          while (rows.next()) connection.createStatement().use { statement -> statement.execute("DROP TABLE ${ImlMetadata.quote(id)}.${ImlMetadata.quote(rows.getString(1))}") }
        }
      }
      connection.prepareStatement("DELETE FROM $control.space_meta WHERE schem = ?").use { it.setString(1, id); it.executeUpdate() }
      // RESTRICT intentionally refuses to remove unexpected external objects in the schema.
      connection.createStatement().use { it.execute("DROP SCHEMA ${ImlMetadata.quote(id)} RESTRICT") }
      connection.prepareStatement("DELETE FROM $control.iml_catalogs WHERE id = ?").use { it.setString(1, id); it.executeUpdate() }
    } else {
      if (current == null) connection.createStatement().use { it.execute("CREATE SCHEMA ${ImlMetadata.quote(id)}") }
      if (current != null && !owned && id != storage.config.schema) throw illegalArg("Cannot modify an externally managed IML catalog")
      connection.prepareStatement("INSERT INTO $control.iml_catalogs VALUES (?, ?::jsonb) ON CONFLICT (id) DO UPDATE SET feature = excluded.feature").use {
        it.setString(1, id); it.setString(2, Platform.toJSON(tuple.decodeFeature(null))); it.executeUpdate()
      }
    }
    metadataChanged = true
    return tuple
  }

  private fun collection(write: Write): Tuple? {
    val catalogId = checkNotNull(write.catalogId)
    var catalog = metadata.catalogsById[catalogId]
    if (catalog == null && write.autoCreateMap && write.op != WriteOp.DELETE && write.op != WriteOp.PURGE) {
      catalog(Write().createMap(NakshaCatalog(catalogId)))
      catalog = NakshaCatalog(catalogId)
    }
    if (catalog == null) throw NakshaException(NakshaError.MAP_NOT_FOUND, "IML catalog not found")
    if (write.id == Id.COLLECTIONS_COL_TEXT || catalogId == Id.ADMIN_CATALOG_TEXT) throw illegalArg("Reserved IML collection")
    val current = metadata.getLayer(catalogId, write.id)
    verify(write, current?.collection?.tupleNumber, current != null, NakshaError.COLLECTION_EXISTS, NakshaError.COLLECTION_NOT_FOUND)
    val deleting = write.op == WriteOp.DELETE || write.op == WriteOp.PURGE
    if (deleting && current == null) return null
    val body = if (deleting) NakshaCollection(write.id, catalogId) else copy(write).proxy(NakshaCollection::class)
    if (!deleting) {
      if (body.partitions != 1 || body.storageClass != null || body.customIndexMapping != null) throw unsupportedOp("IML supports unpartitioned collections without storage classes or custom index mappings")
      body.catalogId = catalogId
      body.databaseId = storage.config.id
      val members = body.useMembers()
      validateIndices(body)
      for (standard in XyzMembers.ALL) {
        val declared = members.get(standard.name) ?: continue
        if (!standard.isSameAs(declared, true)) throw unsupportedOp("IML requires standard XYZ member paths and types")
      }
      if (current != null && !write.force) {
        for (member in current.collection.useMembers()) if (member != null && members.get(member.name) == null) throw illegalArg("Removing IML members requires force")
      }
      if (current != null) connection.createStatement().use {
        if (body.storeHistory == StoreMode.OFF) it.executeUpdate("DELETE FROM ${current.sqlTable} WHERE next_version != 9223372036854775807")
        if (body.storeDeleted == StoreMode.OFF) it.executeUpdate("DELETE FROM ${current.sqlTable} WHERE operation = 'D'")
      }
    }
    val action = if (deleting) "D" else if (current == null) "I" else "U"
    val tuple = ImlTuple.encode(body, TupleNumber(storage.number, Id.textToInt(catalogId), Id.COLLECTIONS_COL_INT, Id.textToInt(write.id).toLong(), ImlTuple.version(version, action)))
    if (deleting) {
      connection.createStatement().use { it.execute("DROP TABLE ${checkNotNull(current).sqlTable}") }
      connection.prepareStatement("DELETE FROM $control.space_meta WHERE id = ? AND schem = ?").use { it.setString(1, write.id); it.setString(2, catalogId); it.executeUpdate() }
    } else {
      val table = current?.table ?: hashedTable(catalogId, write.id)
      if (current == null) connection.createStatement().use {
        it.execute("CREATE TABLE ${ImlMetadata.quote(catalogId)}.${ImlMetadata.quote(table)} (" +
          "id text NOT NULL, version bigint NOT NULL, next_version bigint NOT NULL DEFAULT 9223372036854775807, " +
          "operation char(1) NOT NULL, author text, jsondata jsonb, geo geometry(GeometryZ,4326), " +
          "i bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, UNIQUE(id,next_version))")
      }
      createIndices(body, "${ImlMetadata.quote(catalogId)}.${ImlMetadata.quote(table)}", table)
      val json = Platform.toJSON(tuple.decodeFeature(null))
      if (current == null) connection.prepareStatement("INSERT INTO $control.space_meta (id, schem, h_id, meta) VALUES (?, ?, ?, jsonb_build_object('nakshaCollection', ?::jsonb))").use {
        it.setString(1, write.id); it.setString(2, catalogId); it.setString(3, table); it.setString(4, json); it.executeUpdate()
      } else connection.prepareStatement("UPDATE $control.space_meta SET meta = COALESCE(meta, '{}'::jsonb) || jsonb_build_object('nakshaCollection', ?::jsonb) WHERE id = ? AND schem = ?").use {
        it.setString(1, json); it.setString(2, write.id); it.setString(3, catalogId); it.executeUpdate()
      }
    }
    metadataChanged = true
    return tuple
  }

  private fun feature(write: Write): Tuple? {
    val catalogId = checkNotNull(write.catalogId)
    if (metadata.catalogsById[catalogId] == null) throw NakshaException(NakshaError.MAP_NOT_FOUND, "IML catalog not found")
    val layer = metadata.getLayer(catalogId, checkNotNull(write.collectionId)) ?: throw NakshaException(NakshaError.COLLECTION_NOT_FOUND, "IML collection not found")
    val table = layer.sqlTable
    val members = layer.collection.useMembers()
    validateIndices(layer.collection)
    val columns = "id, i, version, next_version, operation, author, jsondata, ST_AsGeoJSON(geo, 15, 0) AS geometry_json"
    var current = connection.prepareStatement("SELECT $columns FROM $table WHERE id = ? AND next_version = ? FOR UPDATE").use {
      it.setString(1, write.id); it.setLong(2, Long.MAX_VALUE)
      it.executeQuery().use { rows ->
        val tuple = if (rows.next()) ImlTuple.read(rows, storage.number, Id.textToInt(catalogId), layer.number, members) else null
        if (rows.next()) throw illegalArg("Multiple IML HEAD rows for one ID")
        tuple
      }
    }
    if (current == null && write.op == WriteOp.PURGE) {
      current = connection.prepareStatement("SELECT $columns FROM $table WHERE id = ? ORDER BY version DESC, i DESC LIMIT 1 FOR UPDATE").use {
        it.setString(1, write.id)
        it.executeQuery().use { rows -> if (rows.next()) ImlTuple.read(rows, storage.number, Id.textToInt(catalogId), layer.number, members) else null }
      }
    }
    val live = current != null && current.tupleNumber.action != Action.DELETE
    verify(write, current?.tupleNumber, live, NakshaError.FEATURE_EXISTS, NakshaError.FEATURE_NOT_FOUND)
    val deleting = write.op == WriteOp.DELETE || write.op == WriteOp.PURGE
    if (deleting && current == null) return null
    if (write.op == WriteOp.DELETE && !live) return null
    val coalesce = current != null && (current.tupleNumber.version ushr 2) == version
    val operation = if (deleting) "D" else if (coalesce && current?.tupleNumber?.action == Action.CREATE) "I" else if (live) "U" else "I"
    if (write.op == WriteOp.PURGE) {
      connection.prepareStatement("DELETE FROM $table WHERE id = ?").use { it.setString(1, write.id); it.executeUpdate() }
      return ImlTuple.encode(NakshaFeature(write.id), TupleNumber(storage.number, Id.textToInt(catalogId), layer.number, checkNotNull(current).tupleNumber.featureNumber, ImlTuple.version(version, "D")), members = members)
    }
    val body = if (deleting) NakshaFeature(write.id) else copy(write)
    body.id = write.id
    val xyz = body.properties.xyz
    xyz.setRaw(XyzNs.AUTHOR, session.options.author ?: session.options.appId)
    xyz.setRaw(XyzNs.CREATED_AT, current?.decodeFeature(null)?.properties?.xyz?.getRaw(XyzNs.CREATED_AT) ?: time)
    xyz.setRaw(XyzNs.UPDATED_AT, time)
    if (!deleting) {
      for (member in members) {
        if (member == null || member.isVirtual()) continue
        val registered = session.processors[member.name]
        if (member.name in listOf(StandardMembers.TN, StandardMembers.ID, StandardMembers.NEXT_VERSION, StandardMembers.GLOBAL_BOOK_FN)) {
          if (registered != null) throw unsupportedOp("IML identity members cannot be processed")
          continue
        }
        var value = memberValue(member, body.getPath(member.path))
        if (registered != null) for (processor in registered) value = processor.processMember(session, layer.collection, body, member, value)
        member.write(body, memberValue(member, value))
      }
      if (body.id != write.id) throw illegalArg("IML processors cannot change feature identity")
    }
    // Retain creation/update metadata on tombstones so history and recreation do not lose timestamps.
    val json = Platform.toJSON(body)
    val rawGeometry = body.getRaw(NakshaFeature.GEOMETRY)
    val geometry = if (deleting || rawGeometry == null || rawGeometry === Platform.UNDEFINED) null else Platform.toJSON(rawGeometry)
    if (!coalesce && current != null) {
      if ((current.tupleNumber.version ushr 2) >= version) throw NakshaException(NakshaError.CONFLICT, "IML source version advanced outside this writer transaction")
      val sql = if (layer.collection.storeHistory == StoreMode.ON) "UPDATE $table SET next_version = ? WHERE id = ? AND next_version = ?" else "DELETE FROM $table WHERE id = ? AND next_version = ?"
      connection.prepareStatement(sql).use {
        var index = 1
        if (layer.collection.storeHistory == StoreMode.ON) it.setLong(index++, version)
        it.setString(index++, write.id); it.setLong(index, Long.MAX_VALUE); it.executeUpdate()
      }
    }
    if (deleting && layer.collection.storeDeleted != StoreMode.ON) {
      if (coalesce) connection.prepareStatement("DELETE FROM $table WHERE id = ? AND next_version = ?").use { it.setString(1, write.id); it.setLong(2, Long.MAX_VALUE); it.executeUpdate() }
      return ImlTuple.encode(body, TupleNumber(storage.number, Id.textToInt(catalogId), layer.number, checkNotNull(current).tupleNumber.featureNumber, ImlTuple.version(version, "D")), members = members)
    }
    val number = if (coalesce) null else session.allocations.next(connection, layer)
    val sql = if (coalesce) "UPDATE $table SET operation = ?, author = ?, jsondata = ?::jsonb, geo = ST_Force3D(ST_SetSRID(ST_GeomFromGeoJSON(?),4326)) WHERE id = ? AND version = ? RETURNING $columns"
      else "INSERT INTO $table (operation, author, jsondata, geo, id, version, next_version${if (number != null) ", i" else ""}) VALUES (?, ?, ?::jsonb, ST_Force3D(ST_SetSRID(ST_GeomFromGeoJSON(?),4326)), ?, ?, 9223372036854775807${if (number != null) ", ?" else ""}) RETURNING $columns"
    return connection.prepareStatement(sql).use {
      it.setString(1, operation); it.setString(2, XyzMembers.XyzAuthor.readString(body)); it.setString(3, json); it.setString(4, geometry); it.setString(5, write.id); it.setLong(6, version)
      if (number != null) it.setLong(7, number)
      it.executeQuery().use { rows -> check(rows.next()); ImlTuple.read(rows, storage.number, Id.textToInt(catalogId), layer.number, members) }
    }
  }

  private fun copy(write: Write): NakshaFeature {
    val feature = write.feature ?: throw illegalArg("IML write requires a feature body")
    return (Platform.copy(feature.platformObject(), true) as PlatformMap).proxy(NakshaFeature::class)
  }

  private fun memberValue(member: Member, value: Any?): Any? {
    if (value == null || value === Platform.UNDEFINED) return null
    val type = member.dataType
    if (type == MemberType.BOOLEAN) return value as? Boolean ?: throw illegalArg("Invalid IML boolean member '${member.name}'")
    if (type in listOf(MemberType.INT8, MemberType.INT16, MemberType.INT32, MemberType.INT64)) {
      if (value !is Number) throw illegalArg("Invalid IML integer member '${member.name}'")
      val number = value.toLong()
      if ((value is Float || value is Double) && (!value.toDouble().isFinite() || value.toDouble() < Long.MIN_VALUE.toDouble() || value.toDouble() >= 9223372036854775808.0 || value.toDouble() != number.toDouble())) throw illegalArg("Invalid IML integer member '${member.name}'")
      return when (type) {
        MemberType.INT8 -> if (number in Byte.MIN_VALUE..Byte.MAX_VALUE) number.toByte() else throw illegalArg("IML int8 overflow")
        MemberType.INT16 -> if (number in Short.MIN_VALUE..Short.MAX_VALUE) number.toShort() else throw illegalArg("IML int16 overflow")
        MemberType.INT32 -> if (number in Int.MIN_VALUE..Int.MAX_VALUE) number.toInt() else throw illegalArg("IML int32 overflow")
        else -> number
      }
    }
    if (type == MemberType.FLOAT32 || type == MemberType.FLOAT64) {
      if (value !is Number || !value.toDouble().isFinite()) throw illegalArg("Invalid IML floating point member '${member.name}'")
      return if (type == MemberType.FLOAT32) value.toFloat().also { if (!it.isFinite()) throw illegalArg("IML float32 overflow") } else value.toDouble()
    }
    if (type == MemberType.SPATIAL) {
      val map = when (value) {
        is MapProxy<*, *> -> value.platformObject() as PlatformMap
        is PlatformMap -> value
        else -> throw illegalArg("Invalid IML spatial member '${member.name}'")
      }
      val geometry = map.proxy(SpGeometry::class)
      val geometryType = SpType.ofDefined(geometry.getRaw("type") as? String) ?: throw illegalArg("Invalid IML spatial member '${member.name}'")
      val converted = geometry.proxy(geometryType.klass)
      try {
        if (geometryType == SpType.GeometryCollection) {
          if (converted.getRaw("geometries") == null) throw illegalArg("Missing IML geometry collection contents")
        } else converted.getCoordinates()
      } catch (error: Exception) { throw illegalArg("Invalid IML spatial member '${member.name}': ${error.message}") }
      return converted
    }
    val converted = try { type.convert(value, false) } catch (error: Exception) { throw illegalArg("Invalid IML member '${member.name}': ${error.message}") }
    if (converted == null || !type.isInstance(converted)) throw illegalArg("Invalid IML member '${member.name}'")
    return converted
  }

  private fun validateIndices(collection: NakshaCollection) {
    val indices = collection.indices ?: return
    val names = HashSet<String>()
    for (index in indices) {
      if (index == null || !names.add(index.name)) throw illegalArg("Null or duplicate IML index")
      var standard: Index? = null
      for (candidate in XyzIndices.ALL) if (candidate.name == index.name) { standard = candidate; break }
      var sameMembers = standard != null && index.on.size == standard.on.size
      if (sameMembers) for (i in index.on.indices) if (index.on[i] != standard!!.on[i]) { sameMembers = false; break }
      if (!sameMembers || index.isUnique() || index.isInternal() || index.isConditional() || index.include != null || index.hasRaw("type")) {
        throw unsupportedOp("IML supports only unmodified standard XYZ index definitions")
      }
      for (name in index.on) {
        if (name != StandardMembers.FN && name != StandardMembers.VERSION && collection.useMembers().get(checkNotNull(name)) == null) throw illegalArg("IML index references an undeclared member")
      }
    }
  }

  private fun createIndices(collection: NakshaCollection, sqlTable: String, table: String) {
    val members = collection.useMembers()
    val predicates = ImlPredicates({ throw illegalArg("IML index expressions cannot have parameters") }, members)
    // Only exact obsolete adapter-owned names on adapter-created tables may be removed.
    if (table == hashedTable(checkNotNull(collection.catalogId), collection.id)) {
      val owned = HashSet<String>()
      for (index in XyzIndices.ALL) owned.add("${table}_xyz_${index.name}")
      val requested = collection.indices
      if (requested != null) for (index in requested) owned.remove("${table}_xyz_${checkNotNull(index).name}")
      connection.prepareStatement("SELECT indexname FROM pg_indexes WHERE schemaname = ? AND tablename = ?").use {
        it.setString(1, collection.catalogId); it.setString(2, table)
        it.executeQuery().use { rows ->
          while (rows.next()) {
            val name = rows.getString(1)
            if (name in owned) connection.createStatement().use { statement ->
              statement.execute("DROP INDEX ${ImlMetadata.quote(checkNotNull(collection.catalogId))}.${ImlMetadata.quote(name)}")
            }
          }
        }
      }
    }
    connection.createStatement().use { statement ->
      for ((suffix, columns) in arrayOf("version" to "version, id", "head" to "next_version, operation", "author" to "author, i, version")) {
        statement.execute("CREATE INDEX IF NOT EXISTS ${ImlMetadata.quote("${table}_$suffix")} ON $sqlTable ($columns)")
      }
      val indices = collection.indices ?: return
      for (index in indices) {
        val definition = checkNotNull(index)
        val expressions = ArrayList<String>()
        var method = "btree"
        for (name in definition.on) {
          if (name == StandardMembers.FN) { expressions.add("i"); continue }
          if (name == StandardMembers.VERSION) { expressions.add("version"); continue }
          val member = members.get(checkNotNull(name)) ?: throw illegalArg("Undeclared IML index member")
          if (name == XyzMembers.XyzGeometry.name) { method = "gist"; expressions.add("geo"); continue }
          val expression = when (member.dataType) {
            MemberType.SPATIAL -> {
              method = "gist"
              val path = ArrayList<String>()
              for (part in member.path) path.add("E'${part.toString().replace("\\", "\\\\").replace("'", "''")}'")
              val accessor = "(jsondata #> ARRAY[${path.joinToString(", ")}]::text[])"
              "(CASE WHEN jsonb_typeof($accessor) = 'object' THEN ST_SetSRID(ST_GeomFromGeoJSON(($accessor)::text),4326) END)"
            }
            MemberType.TAG_LIST -> { method = "gin"; "jsondata->'properties'->'@ns:com:here:xyz'->'tags'" }
            MemberType.INT8, MemberType.INT16, MemberType.INT32, MemberType.INT64,
            MemberType.FLOAT32, MemberType.FLOAT64, MemberType.STRING -> predicates.column(name)
            else -> throw unsupportedOp("Unsupported IML index member type")
          }
          expressions.add("($expression)")
        }
        statement.execute("CREATE INDEX IF NOT EXISTS ${ImlMetadata.quote("${table}_xyz_${definition.name}")} ON $sqlTable USING $method (${expressions.joinToString(",")})")
      }
    }
  }

  private fun hashedTable(catalog: String, collection: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest("$catalog\u0000$collection".toByteArray(Charsets.UTF_8))
    val result = StringBuilder("iml_")
    for (byte in bytes) result.append((byte.toInt() and 255).toString(16).padStart(2, '0'))
    return result.toString()
  }
}
