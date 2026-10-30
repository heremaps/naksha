package naksha.iml

import java.sql.ResultSet
import naksha.base.Action
import naksha.base.Guid
import naksha.base.Platform
import naksha.base.PlatformMap
import naksha.base.proxy
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.base.illegalArg
import naksha.jbon.BookType
import naksha.jbon.HeapBook
import naksha.jbon.JbEncoder2
import naksha.model.Tuple
import naksha.model.XyzNs
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.FeatureTuple

/**
 * Maps read-only IML rows to self-contained Naksha tuples without a write session or global book.
 * @since 3.0
 */
internal object ImlTuple {
  /**
   * Packs a source manual version and operation, translating IML's HEAD sentinel before packing.
   * @since 3.0
   */
  fun version(source: Long, operation: String): Long {
    if (source == Long.MAX_VALUE) return Version.HEAD.number
    val action = when (operation) {
      "I" -> Action.CREATE
      "U" -> Action.UPDATE
      "D" -> Action.DELETE
      else -> Action.VERSION
    }
    return Version.manual(source, action).number
  }

  /**
   * Reads the current JDBC row, using relational ID, author and geometry even when author or geometry is null. The geometry_json column must contain ST_AsGeoJSON(geo, 15, 0) text.
   * @since 3.0
   */
  fun read(row: ResultSet, databaseNumber: Long, catalogNumber: Int, collectionNumber: Int): Tuple {
    val json = row.getString("jsondata")
    val feature = if (json != null) NakshaFeature.fromJson(json) else {
      if (row.getString("operation") != "D") throw illegalArg("IML live row has no JSON feature body")
      NakshaFeature(row.getString("id"))
    }
    feature.id = row.getString("id")
    feature.properties.xyz.setRaw(XyzNs.AUTHOR, row.getString("author"))
    val geometry = row.getString("geometry_json")
    feature.setRaw(NakshaFeature.GEOMETRY, if (geometry == null) null else Platform.fromJSON(geometry))
    val tn = TupleNumber(
      databaseNumber, catalogNumber, collectionNumber, row.getLong("i"),
      version(row.getLong("version"), row.getString("operation"))
    )
    return encode(feature, tn, version(row.getLong("next_version"), ""))
  }

  /**
   * Encodes a feature inline, preserving its properties and feature type without mutating the input. Both tuple and next versions are already packed Naksha versions.
   * @since 3.0
   */
  fun encode(feature: NakshaFeature, tn: TupleNumber, nextVersion: Long = Version.HEAD.number): Tuple {
    val id = XyzMembers.XyzId.readString(feature) ?: throw illegalArg("Missing 'id' in IML feature")
    // Geometry normalization in typed member readers mutates coordinates, so a shallow XYZ-only copy is insufficient.
    val copy = (Platform.copy(feature.platformObject(), true) as PlatformMap).proxy(NakshaFeature::class)
    val xyz = copy.properties.xyz
    xyz.removeRaw(XyzNs.UUID)
    xyz.removeRaw(XyzNs.NUUID)
    xyz.removeRaw(XyzNs.MUUID)
    xyz.removeRaw("globalBookFn")
    xyz.setRaw(XyzNs.UUID, Guid(id, tn).toString())
    xyz.setRaw(XyzNs.ACTION, tn.action.toString())
    xyz.setRaw("version", tn.version)
    xyz.setRaw("nextVersion", nextVersion)

    val members = HeapBook(BookType.MEMBER_BOOK)
    for (member in XyzMembers.ALL) {
      if (member.isVirtual()) continue
      val value = when (member.name) {
        StandardMembers.TN -> tn
        StandardMembers.ID -> id
        StandardMembers.NEXT_VERSION -> nextVersion
        StandardMembers.GLOBAL_BOOK_FN -> null
        XyzMembers.XyzGeometry.name -> copy.geometry
        XyzMembers.XyzReferencePoint.name -> copy.referencePoint
        else -> member.read(copy)
      }
      members.put(member.name, value)
    }
    // IML's i identifies a row, not a feature across revisions, so never synthesize a successor UUID.
    return Tuple(JbEncoder2().buildTupleFromMap(copy), members)
  }

  /**
   * Attaches a loaded or cached tuple without FeatureTuple's automatic XYZ namespace replacement. Call again whenever the wrapped tuple changes.
   * @since 3.0
   */
  fun attach(target: FeatureTuple, tuple: Tuple) {
    target.tuple = tuple
    target.feature = tuple.decodeFeature(null)
  }
}
