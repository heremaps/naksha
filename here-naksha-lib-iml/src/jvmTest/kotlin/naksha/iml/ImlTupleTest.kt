package naksha.iml

import java.lang.reflect.Proxy
import java.sql.ResultSet
import naksha.base.Action
import naksha.base.Guid
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.MapProxy
import naksha.base.proxy
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.geo.SpPoint
import naksha.jbon.JB2_MAGIC
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.FeatureTuple
import naksha.model.NakshaContext
import naksha.model.request.OrderBy
import naksha.model.request.ReadFeatures
import naksha.model.request.ops.And
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Gt
import naksha.model.request.ops.Intersects
import naksha.model.request.ops.IsNull
import naksha.model.request.ops.IsTrue
import naksha.model.request.ops.StartsWith
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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

  @Test
  fun `custom declared paths are materialized alongside canonical XYZ members`() {
    val name = Member("label", MemberType.STRING, JsonPath("properties", "name"))
    val nested = Member("nested", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "custom", "value"))
    val members = MemberList(name, nested, Member("id", MemberType.STRING, JsonPath("extra")))
    val tuple = ImlTuple.read(row("relational-author", null), 1, 2, 3, members)
    assertEquals("kept", tuple.getString(name))
    assertEquals("nested", tuple.getString(nested))
    assertEquals("00123", tuple.id)
    assertEquals("relational-author", tuple.getString(XyzMembers.XyzAuthor))
    assertNull(tuple.getSpatial(XyzMembers.XyzGeometry))
    assertArrayEquals(JB2_MAGIC, tuple.featureBytes.copyOfRange(0, JB2_MAGIC.size))
    assertNull(tuple.membersBook[StandardMembers.FEATURE_BYTES])
    assertPreserved(tuple.decodeFeature(null))
  }

  @Test
  fun `encoding normalizes declared primitive types and detects custom spatial maps without changing JSON`() {
    val source = NakshaFeature.fromJson("""{"type":"Feature","id":"custom","properties":{
      "number":12,"decimal":1.25,"flag":true,"wrong":"12","array":["first","second"],
      "shape":{"type":"Point","coordinates":[1,2,3],"extra":"kept"},"map":{"key":"value"}
    }}""")
    val members = MemberList()
    val cases = listOf(
      MemberType.INT8 to 12.toByte(), MemberType.INT16 to 12.toShort(),
      MemberType.INT32 to 12, MemberType.INT64 to 12L,
      MemberType.FLOAT32 to 12.0f, MemberType.FLOAT64 to 12.0
    )
    for ((index, entry) in cases.withIndex()) members.add(Member("number$index", entry.first, JsonPath("properties", "number")))
    val flag = Member("flag", MemberType.BOOLEAN)
    val wrong = Member("wrong", MemberType.INT64)
    val shape = Member("shape", MemberType.SPATIAL)
    val second = Member("second", MemberType.STRING, JsonPath("properties", "array", 1))
    val map = Member("map", MemberType.TAG_MAP)
    members.addAll(listOf(flag, wrong, shape, second, map))
    val before = Platform.toJSON(source)
    val tuple = ImlTuple.encode(source, TupleNumber(1, 2, 3, 4, 4), members = members)
    for ((index, entry) in cases.withIndex()) {
      val actual = tuple.membersBook["number$index"]
      assertEquals(entry.second, actual)
      assertEquals(entry.second::class, actual!!::class)
    }
    assertEquals(true, flag.readBoolean(tuple))
    assertNull(wrong.readLong(tuple))
    assertEquals("second", tuple.getString(second))
    assertEquals("value", tuple.getTags(map)?.getRaw("key"))
    val point = checkNotNull(tuple.getSpatial(shape)).asPoint()
    assertEquals(1.0, point.longitude)
    assertEquals(3.0, point.z)
    assertEquals(before, Platform.toJSON(source))
    val decoded = tuple.decodeFeature(null)
    val decodedPoint = (decoded.properties.getPath("shape") as MapProxy<*, *>).proxy(SpPoint::class)
    assertEquals(1.0, decodedPoint.longitude)
    assertEquals(2.0, decodedPoint.latitude)
    assertEquals(3.0, decodedPoint.z)
    decoded.properties.removeRaw("shape")
    decoded.properties.removeRaw("@ns:com:here:xyz")
    val expected = NakshaFeature.fromJson(before)
    expected.properties.removeRaw("shape")
    assertEquals(Platform.toJSON(expected.properties), Platform.toJSON(decoded.properties))
  }

  @Test
  fun `canonical spatial members parse raw maps without synthesizing missing geometry`() {
    val source = NakshaFeature.fromJson("""{"type":"Feature","id":"spatial",
      "referencePoint":{"type":"Point","coordinates":[4,5,6]},"properties":{}}
    """)
    val before = Platform.toJSON(source)
    val tuple = ImlTuple.encode(source, TupleNumber(1, 2, 3, 4, 4))
    assertNull(tuple.getSpatial(XyzMembers.XyzGeometry))
    val point = checkNotNull(tuple.getSpatial(XyzMembers.XyzReferencePoint)).asPoint()
    assertEquals(4.0, point.longitude)
    assertEquals(6.0, point.z)
    assertEquals(before, Platform.toJSON(source))
    val decoded = tuple.decodeFeature(null)
    assertFalse(decoded.hasRaw("geometry"))
    val decodedPoint = decoded.referencePoint as SpPoint
    assertEquals(4.0, decodedPoint.longitude)
    assertEquals(5.0, decodedPoint.latitude)
    assertEquals(6.0, decodedPoint.z)

    source.setRaw("geometry", null)
    source.setRaw("referencePoint", null)
    val empty = ImlTuple.encode(source, TupleNumber(1, 2, 3, 4, 4))
    assertNull(empty.getSpatial(XyzMembers.XyzGeometry))
    assertNull(empty.getSpatial(XyzMembers.XyzReferencePoint))
    assertNull(empty.decodeFeature(null).getRaw("geometry"))
  }

  @Test
  fun `integer and precise canonical points decode directly with altitude and unchanged source`() {
    for (coordinates in listOf("[1,2,3]", "[12.1234567,34.7654321,56.75]")) {
      val source = NakshaFeature.fromJson("""{"type":"Feature","id":"point","geometry":{"type":"Point","coordinates":$coordinates},"properties":{}}""")
      val before = Platform.toJSON(source)
      val tuple = ImlTuple.encode(source, TupleNumber(1, 2, 3, 4, 4))
      val stored = tuple.getSpatial(XyzMembers.XyzGeometry) as SpPoint
      val decoded = tuple.decodeFeature(null).geometry as SpPoint
      assertEquals(stored.longitude, decoded.longitude, 0.0000001)
      assertEquals(stored.latitude, decoded.latitude, 0.0000001)
      assertEquals(stored.z!!, decoded.z!!, 0.01)
      assertEquals(before, Platform.toJSON(source))
    }
  }

  @Test
  fun `custom scalar queries bind only values across repeated expressions and custom ordering`() {
    NakshaContext.currentContext().appId = "iml-tuple-test"
    val name = Member("label", MemberType.STRING, JsonPath("properties", "a'\\key", 0))
    val count = Member("count", MemberType.INT64)
    val active = Member("active", MemberType.BOOLEAN)
    val members = MemberList(name, count, active)
    val request = ReadFeatures().apply {
      queryMembers = And(Equals(name, "x' OR TRUE --"), StartsWith(name, "a!%_"), Gt(count, 7), IsTrue(active), Equals(active, true))
      orderBy = OrderBy(count)
    }
    val query = ImlQuery(request, 10, members)
    assertEquals(listOf(Long.MAX_VALUE, "x' OR TRUE --", "a!!!%!_%", 7, true), query.parameters)
    assertEquals(query.parameters.size, query.predicate.count { it == '?' })
    assertFalse(query.predicate.contains("x' OR TRUE --"))
    assertTrue(query.predicate.contains("ARRAY[E'properties', E'a''\\\\key', E'0']"))
    assertTrue(query.predicate.contains("= 'number'"))
    assertTrue(query.predicate.contains("= 'boolean'"))
    assertTrue(query.orderBy.contains("::numeric END) ASC, i ASC"))
    assertTrue(ImlQuery(ReadFeatures().apply { queryMembers = IsTrue(count) }, 10, members).predicate.endsWith("NULL::boolean IS TRUE"))
    assertThrows(NakshaException::class.java) { ImlQuery(ReadFeatures().apply { queryMembers = Equals("id", true) }, 10, members) }
    assertThrows(NakshaException::class.java) { ImlQuery(ReadFeatures().apply { queryMembers = Equals("active", true) }, 10) }
    assertThrows(NakshaException::class.java) { ImlQuery(ReadFeatures().apply { queryMembers = IsNull("missing") }, 10, members) }
  }

  @Test
  fun `custom spatial predicates use guarded metadata JSON paths without changing projection`() {
    NakshaContext.currentContext().appId = "iml-tuple-test"
    val shape = Member("shape", MemberType.SPATIAL)
    val value = SpPoint(naksha.geo.PointCoord(1.0, 2.0))
    val query = ImlQuery(ReadFeatures().apply { queryMembers = Intersects(shape, value) }, 10, MemberList(shape))
    assertEquals(listOf(Long.MAX_VALUE, Platform.toJSON(value)), query.parameters)
    assertEquals(query.parameters.size, query.predicate.count { it == '?' })
    assertTrue(query.predicate.contains("CASE WHEN jsonb_typeof((jsondata #> ARRAY[E'properties', E'shape']::text[])) = 'object'"))
    assertTrue(query.predicate.contains("ST_Intersects((CASE"))
    assertThrows(NakshaException::class.java) {
      ImlQuery(ReadFeatures().apply { queryMembers = IsNull(shape) }, 10, MemberList(shape))
    }
    assertThrows(NakshaException::class.java) {
      ImlQuery(ReadFeatures().apply { queryMembers = Intersects("missing", value) }, 10, MemberList(shape))
    }
  }

  @Test
  fun `invalid member metadata is rejected and canonical duplicates cannot override tuple fields`() {
    val source = NakshaFeature("custom")
    val tn = TupleNumber(1, 2, 3, 4, 4)
    val invalid = listOf(
      MemberList(Member("same"), Member("same")),
      MemberList(Member("id", MemberType.INT64)),
      MemberList(Member("bad").apply { path = JsonPath("properties", "bad\u0000key") }),
      MemberList(Member("bad").apply { path = JsonPath("properties", "x".repeat(128)) }),
      MemberList(Member("bad").apply { path = JsonPath("properties", true) }),
      MemberList(Member("bad").apply { name = "bad; SQL" })
    )
    for (members in invalid) {
      assertThrows(NakshaException::class.java) { ImlTuple.encode(source, tn, members = members) }
      NakshaContext.currentContext().appId = "iml-tuple-test"
      assertThrows(NakshaException::class.java) { ImlQuery(ReadFeatures(), 10, members) }
    }
    val featureBytes = Member(StandardMembers.FEATURE_BYTES, MemberType.BYTE_ARRAY, JsonPath())
    val tuple = ImlTuple.encode(source, tn, members = MemberList(featureBytes))
    assertArrayEquals(JB2_MAGIC, tuple.featureBytes.copyOfRange(0, JB2_MAGIC.size))
    assertNull(tuple.membersBook[featureBytes.name])
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
