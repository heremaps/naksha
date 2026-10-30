package naksha.iml

import java.lang.reflect.Proxy
import java.sql.ResultSet
import naksha.base.Action
import naksha.base.Guid
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.geo.SpPoint
import naksha.jbon.JB2_MAGIC
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.FeatureTuple
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Pure JVM tests for IML row and tuple mapping without database access.
 * @since 3.0
 */
class ImlTupleTest {
  @Test
  fun `manual versions pack operations and translate HEAD without overflow`() {
    assertEquals(28L, ImlTuple.version(7, "I"))
    assertEquals(29L, ImlTuple.version(7, "U"))
    assertEquals(30L, ImlTuple.version(7, "D"))
    assertEquals(31L, ImlTuple.version(7, "unknown"))
    assertEquals(Version.HEAD.number, ImlTuple.version(Long.MAX_VALUE, "I"))
    val maximum = Version.MAX_MANUAL.number ushr 2
    assertEquals(Version.MAX_MANUAL.number, ImlTuple.version(maximum, ""))
    assertThrows(NakshaException::class.java) { ImlTuple.version(-1, "I") }
    assertThrows(NakshaException::class.java) { ImlTuple.version(maximum + 1, "I") }
  }

  @Test
  fun `read uses relational identity author and three dimensional geometry`() {
    val tuple = ImlTuple.read(row("relational-author", """{"type":"Point","coordinates":[12.125,34.25,56.75]}"""), 11, 12, 13)
    val tn = TupleNumber(11, 12, 13, 99, 29)
    assertEquals(tn, tuple.tupleNumber)
    assertEquals("00123", tuple.id)
    assertEquals(99L, tuple.getLong(StandardMembers.FeatureNumber))
    assertEquals(29L, tuple.getLong(StandardMembers.FeatureVersion))
    assertEquals(Action.UPDATE.intValue, tuple.getInt(StandardMembers.Action))
    assertEquals(Version.HEAD.number, tuple.nextVersion)
    assertNull(tuple.globalBookTn)
    assertArrayEquals(JB2_MAGIC, tuple.featureBytes.copyOfRange(0, JB2_MAGIC.size))
    val feature = tuple.decodeFeature(null)
    assertEquals("00123", feature.id)
    assertEquals("relational-author", tuple.getString(XyzMembers.XyzAuthor))
    assertEquals("relational-author", feature.properties.xyz.getRaw("author"))
    assertEquals(Guid("00123", tn).toString(), feature.properties.xyz.getRaw("uuid"))
    assertEquals(tn.action.toString(), feature.properties.xyz.getRaw("action"))
    assertEquals(29L, feature.properties.xyz.version?.number)
    val point = checkNotNull(tuple.getSpatial(XyzMembers.XyzGeometry)).asPoint()
    assertEquals(12.125, point.longitude)
    assertEquals(34.25, point.latitude)
    assertEquals(56.75, point.z)
    val decodedPoint = feature.geometry as SpPoint
    assertEquals(point.z, decodedPoint.z)
    assertEquals(point.longitude, decodedPoint.longitude)
    assertPreserved(feature)
    assertEquals(1000L, tuple.getLong(XyzMembers.XyzCreatedAt))
    assertEquals(2000L, tuple.getLong(XyzMembers.XyzUpdatedAt))
    assertEquals(1500L, tuple.getLong(XyzMembers.XyzAuthorTimestamp))
    assertEquals(listOf("b", "a"), tuple.getTagList(XyzMembers.XyzTags)?.toList())
  }

  @Test
  fun `null relational author and geometry replace nonnull JSON values`() {
    val tuple = ImlTuple.read(row(null, null), 1, 2, 3)
    val feature = tuple.decodeFeature(null)
    assertNull(feature.geometry)
    assertNull(feature.properties.xyz.getRaw("author"))
    assertNull(tuple.getSpatial(XyzMembers.XyzGeometry))
    assertNull(tuple.getString(XyzMembers.XyzAuthor))
    assertPreserved(feature)
  }

  @Test
  fun `historical next version is packed but no successor identity is fabricated`() {
    val tuple = ImlTuple.read(row(null, null, nextVersion = 8, operation = "D"), 1, 2, 3)
    assertEquals(30L, tuple.tupleNumber.version)
    assertEquals(35L, tuple.nextVersion)
    val xyz = tuple.decodeFeature(null).properties.xyz
    assertEquals(35L, (xyz.getRaw("nextVersion") as Number).toLong())
    assertEquals(Action.DELETE.toString(), xyz.getRaw("action"))
    assertNull(xyz.getRaw("nuuid"))
    assertNull(xyz.getRaw("muuid"))
  }

  @Test
  fun `encode leaves input unchanged and attach preserves inline XYZ metadata on cached tuples`() {
    val original = NakshaFeature.fromJson(SOURCE)
    val before = Platform.toJSON(original)
    val tn = TupleNumber(1, 2, 3, 99, 29)
    val tuple = ImlTuple.encode(original, tn)
    assertEquals(before, Platform.toJSON(original))
    assertNull(tuple.previousTupleNumber)
    val target = FeatureTuple(tn, tuple)
    ImlTuple.attach(target, tuple)
    assertSame(tuple, target.tuple)
    assertNotNull(target.feature)
    assertPreserved(target.feature!!)
    assertSame(target.feature, target.feature)

    val replacement = ImlTuple.encode(NakshaFeature("replacement").apply {
      properties.xyz.setRaw("custom", "replacement-value")
    }, tn)
    ImlTuple.attach(target, replacement)
    assertEquals("replacement", target.id)
    assertEquals("replacement-value", target.feature!!.properties.xyz.getRaw("custom"))
  }

  @Test
  fun `synthetic catalog and collection feature types survive encoding`() {
    val features = listOf(
      NakshaCatalog("public"),
      NakshaCollection("layer").apply { featureType = NakshaCollection.FEATURE_TYPE }
    )
    for (feature in features) {
      val tuple = ImlTuple.encode(feature, TupleNumber(1, 2, 3, 4, 4))
      assertEquals(feature.featureType, tuple.decodeFeature(null).featureType)
      assertEquals(feature.featureType, tuple.getString(XyzMembers.XyzFeatureType))
      assertEquals(feature.id, tuple.id)
    }
  }

  private fun assertPreserved(feature: NakshaFeature) {
    assertEquals("root-value", feature.getRaw("extra"))
    assertEquals("kept", feature.properties.getRaw("name"))
    assertEquals("other-value", feature.properties.getPath("@ns:other", "key"))
    assertEquals("nested", feature.properties.xyz.getPath("custom", "value"))
    val xyz = feature.properties.xyz
    assertEquals(1000L, (xyz.getRaw("createdAt") as Number).toLong())
    assertEquals(2000L, (xyz.getRaw("updatedAt") as Number).toLong())
    assertEquals(1500L, (xyz.getRaw("authorTs") as Number).toLong())
    assertEquals(listOf("b", "a"), xyz.tags.toList())
    assertFalse(xyz.getRaw("uuid") == "source-uuid")
    assertNull(xyz.getRaw("nuuid"))
    assertNull(xyz.getRaw("muuid"))
    assertNull(xyz.getRaw("globalBookFn"))
  }

  private fun row(author: String?, geometry: String?, nextVersion: Long = Long.MAX_VALUE, operation: String = "U"): ResultSet {
    val values = mapOf(
      "id" to "00123", "i" to 99L, "version" to 7L, "next_version" to nextVersion,
      "operation" to operation, "author" to author, "jsondata" to SOURCE, "geometry_json" to geometry
    )
    return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ResultSet::class.java)) { _, method, args ->
      val column = args?.get(0) as? String
      if (column !in values) throw AssertionError("Unexpected column $column")
      when (method.name) {
        "getString" -> values[column] as String?
        "getLong" -> values[column] as Long
        else -> throw AssertionError("Unexpected JDBC method ${method.name}")
      }
    } as ResultSet
  }

  companion object {
    private const val SOURCE = """{
      "type":"Feature","id":"json-id","extra":"root-value","geometry":{"type":"Point","coordinates":[1,2,3]},
      "properties":{"name":"kept","@ns:other":{"key":"other-value"},"@ns:com:here:xyz":{
        "uuid":"source-uuid","nuuid":"source-next","muuid":"source-merge",
        "author":"json-author","version":123,"nextVersion":456,"action":"wrong","globalBookFn":123,
        "createdAt":1000,"updatedAt":2000,"authorTs":1500,"tags":["b","a"],
        "custom":{"value":"nested"}
      }}
    }"""
  }
}
