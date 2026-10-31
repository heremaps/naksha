package naksha.iml

import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.sql.ResultSet
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.TupleNumber
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaFeature
import naksha.model.objects.XyzMembers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Exotic JSONB numbers and collection trees, without database access. @since 3.0 */
class ImlExoticEncodingTest {
  @Test
  fun `expanded floating maxima and long boundaries survive row parsing and tuple encoding`() {
    val single = BigDecimal(Float.MAX_VALUE.toString()).toPlainString()
    val double = BigDecimal(Double.MAX_VALUE.toString()).toPlainString()
    val members = MemberList(Member("single", MemberType.FLOAT32), Member("double", MemberType.FLOAT64), Member("integer", MemberType.INT64))
    for (sign in arrayOf("", "-")) {
      val properties = """{"single":$sign$single,"double":$sign$double,"integer":${if (sign.isEmpty()) Long.MAX_VALUE else Long.MIN_VALUE},"precise":9007199254740993,"nested":[null,true,{},[],{"number":$sign$double}]}"""
      val tuple = ImlTuple.read(row(properties), 1, 2, 3, members)
      val multiplier = if (sign.isEmpty()) 1 else -1
      assertEquals(multiplier * Float.MAX_VALUE, tuple.membersBook["single"])
      assertEquals(multiplier * Double.MAX_VALUE, tuple.membersBook["double"])
      assertEquals(if (sign.isEmpty()) Long.MAX_VALUE else Long.MIN_VALUE, tuple.membersBook["integer"])
      val decoded = tuple.decodeFeature(null).properties
      assertEquals(9007199254740993L, decoded.getRaw("precise"))
      assertEquals(multiplier * Double.MAX_VALUE, (decoded.getRaw("double") as Number).toDouble())
      assertEquals(multiplier * Double.MAX_VALUE, (decoded.getPath("nested", 4, "number") as Number).toDouble())
      assertEquals("{}", Platform.toJSON(decoded.getPath("nested", 2)))
      assertEquals("[]", Platform.toJSON(decoded.getPath("nested", 3)))
    }
  }

  @Test
  fun `number normalization preserves quoted escapes Unicode decimals and long values`() {
    val unchanged = """{"9223372036854775808":"quote\"\\\u96ea 9999999999999999999999","values":[-9223372036854775808,9223372036854775807,9007199254740993,1.7976931348623157e308,0.125,5e-324]}"""
    assertSame(unchanged, ImlTuple.jsonNumbers(unchanged))
    val expanded = """{"values":[9223372036854775808,-9223372036854775809,100000000000000000000],"text":"100000000000000000000"}"""
    assertEquals("""{"values":[9223372036854775808.0,-9223372036854775809.0,100000000000000000000.0],"text":"100000000000000000000"}""", ImlTuple.jsonNumbers(expanded))
    assertThrows(NakshaException::class.java) { ImlTuple.jsonNumbers("[1${"0".repeat(309)}]") }
  }

  @Test
  fun `scientific and decimal floating boundaries retain existing parser semantics`() {
    val members = MemberList(Member("single", MemberType.FLOAT32), Member("double", MemberType.FLOAT64))
    val singles = floatArrayOf(Float.MIN_VALUE, 0.1f, Float.MAX_VALUE)
    val doubles = doubleArrayOf(Double.MIN_VALUE, 1.0000000000000002, Double.MAX_VALUE)
    for (i in singles.indices) {
      val tuple = ImlTuple.read(row("""{"single":${singles[i]},"double":${doubles[i]}}"""), 1, 2, 3, members)
      assertEquals(singles[i], tuple.membersBook["single"])
      assertEquals(doubles[i], tuple.membersBook["double"])
    }
  }

  @Test
  fun `empty and mixed nested geometry collections encode inline without mutating typed input`() {
    val shapes = arrayOf(
      """{"type":"GeometryCollection","geometries":[],"extra":"\u96ea"}""",
      """{"type":"GeometryCollection","geometries":[{"type":"Point","coordinates":[12.1234567890123,34.7654321098765,6.75]},{"type":"LineString","coordinates":[[1,2,3],[4,5,6]]},{"type":"GeometryCollection","geometries":[]}]}"""
    )
    val custom = Member("shape", MemberType.SPATIAL, JsonPath("properties", "shapes", 0))
    for (shape in shapes) {
      val source = NakshaFeature.fromJson("""{"type":"Feature","id":"collection","geometry":$shape,"properties":{"shapes":[$shape],"empty":[]}}""")
      source.geometry?.asGeometryCollection()
      val before = Platform.toJSON(source)
      val tuple = ImlTuple.encode(source, TupleNumber(1, 2, 3, 4, 4), members = MemberList(custom))
      assertEquals(before, Platform.toJSON(source))
      assertEquals("GeometryCollection", tuple.getSpatial(XyzMembers.XyzGeometry)?.getRaw("type"))
      assertEquals("GeometryCollection", tuple.getSpatial(custom)?.getRaw("type"))
      val decoded = tuple.decodeFeature(null)
      assertEquals(Platform.toJSON(source.geometry), Platform.toJSON(decoded.geometry?.asGeometryCollection()))
      assertEquals(Platform.toJSON(source.properties.getPath("shapes", 0)), Platform.toJSON(decoded.properties.getPath("shapes", 0)))
      assertEquals("[]", Platform.toJSON(decoded.properties.getRaw("empty")))
      val fromRow = ImlTuple.read(row("{}", shape), 1, 2, 3).decodeFeature(null)
      assertEquals(Platform.toJSON(source.geometry), Platform.toJSON(fromRow.geometry?.asGeometryCollection()))
    }
  }

  @Test
  fun `normalization performance smoke comparison for ordinary and text heavy rows`() {
    val samples = arrayOf(
      """{"type":"Feature","id":"point","geometry":null,"properties":{"integer":9007199254740993,"fraction":0.125,"flag":true}}""",
      Platform.toJSON(NakshaFeature("text").apply { properties.setRaw("text", "quote\"\\\n\u96ea".repeat(12000)) })
    )
    for (json in samples) {
      repeat(200) { ImlTuple.jsonNumbers(json); NakshaFeature.fromJson(json) }
      val rounds = if (json.length < 1000) 10000 else 500
      val scanStart = System.nanoTime()
      repeat(rounds) { assertSame(json, ImlTuple.jsonNumbers(json)) }
      val scanNanos = System.nanoTime() - scanStart
      val parseStart = System.nanoTime()
      repeat(rounds) { NakshaFeature.fromJson(json) }
      val parseNanos = System.nanoTime() - parseStart
      println("IML JSON ${json.length} chars, $rounds rounds: scan ${scanNanos / rounds} ns, parse ${parseNanos / rounds} ns, scan/parse ${scanNanos.toDouble() / parseNanos}")
      assertTrue(scanNanos > 0 && parseNanos > 0)
    }
  }

  private fun row(properties: String, geometry: String? = null): ResultSet {
    val values = mapOf(
      "id" to "row", "i" to 4L, "version" to 1L, "next_version" to Long.MAX_VALUE,
      "operation" to "I", "author" to null, "geometry_json" to geometry,
      "jsondata" to """{"type":"Feature","id":"stale","properties":$properties}"""
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
}
