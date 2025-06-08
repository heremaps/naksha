package naksha.geo

import naksha.base.Platform.PlatformCompanion.fromJson
import naksha.base.Platform.PlatformCompanion.toJSON
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.MultiLineString
import org.locationtech.jts.geom.MultiPoint
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.geojson.GeoJsonReader
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JtsTest {

    private val jtsJsonReader = GeoJsonReader()

    @Test
    fun testEnvelope() {
        // given
        val x1 = 1.0
        val y1 = 1.1
        val x2 = 2.0
        val y2 = 2.1

        // when
        val polygon = SpPolygon(BBox(x1, y1, x2, y2))

        // then
        val coords = assertNotNull(polygon.coordinates[0])

        assertEquals(1.0, assertNotNull(coords[0]).longitude)
        assertEquals(1.1, assertNotNull(coords[0]).latitude)

        assertEquals(2.0, assertNotNull(coords[1]).longitude)
        assertEquals(1.1, assertNotNull(coords[1]).latitude)

        assertEquals(2.0, assertNotNull(coords[2]).longitude)
        assertEquals(2.1, assertNotNull(coords[2]).latitude)

        assertEquals(1.0, assertNotNull(coords[3]).longitude)
        assertEquals(2.1, assertNotNull(coords[3]).latitude)

        assertEquals(1.0, assertNotNull(coords[4]).longitude)
        assertEquals(1.1, assertNotNull(coords[4]).latitude)
    }

    @Test
    fun testPointToJts() {
        // given
        val json = """
          {
            "type": "Point",
            "coordinates": [-20.26, 27.12, 0.55]
          }
        """.trimIndent()

        val spGeometry = assertNotNull(fromJson(json, SpGeometry.TYPE))
        val jtsFromJson = jtsJsonReader.read(json)

        // when
        val jtsFromProxy = GeoUtil.toJtsGeometry(spGeometry)
        val proxyFromJts = GeoUtil.toProxyGeometry(jtsFromJson)

        // then
        assertInstanceOf(Point::class.java, jtsFromProxy)
        assertEquals(jtsFromJson, jtsFromProxy)
        assertEquals(toJSON(proxyFromJts), toJSON(spGeometry))
    }

    @Test
    fun testMultiPointToJts() {
        // given
        val json = """
          {
            "type": "MultiPoint",
            "coordinates": [
                [-20.26, 27.12, 0.55],
                [-20.24, 27.13]
               ]
          }
        """.trimIndent()

        val spGeometry = assertNotNull(fromJson(json, SpGeometry.TYPE))
        val jtsFromJson = jtsJsonReader.read(json)

        // when
        val jtsFromProxy = GeoUtil.toJtsGeometry(spGeometry)
        val proxyFromJts = GeoUtil.toProxyGeometry(jtsFromJson)

        // then
        assertInstanceOf(MultiPoint::class.java, jtsFromProxy)
        assertEquals(jtsFromJson, jtsFromProxy)
        assertEquals(toJSON(proxyFromJts), toJSON(spGeometry))
    }

    @Test
    fun testLineStringToJts() {
        // given
        val json = """
          {
            "type": "LineString",
            "coordinates": [
                [-20.26, 27.12, 0.55],
                [-20.24, 27.13]
               ]
          }
        """.trimIndent()

        val spGeometry = assertNotNull(fromJson(json, SpGeometry.TYPE))
        val jtsFromJson = jtsJsonReader.read(json)

        // when
        val jtsFromProxy = GeoUtil.toJtsGeometry(spGeometry)
        val proxyFromJts = GeoUtil.toProxyGeometry(jtsFromJson)

        // then
        assertInstanceOf(LineString::class.java, jtsFromProxy)
        assertEquals(jtsFromJson, jtsFromProxy)
        assertEquals(toJSON(proxyFromJts), toJSON(spGeometry))
    }

    @Test
    fun testMultiLineStringToJts() {
        // given
        val json = """
          {
            "type": "MultiLineString",
            "coordinates": [
               [
                [-20.26, 27.12, 0.55],
                [-20.24, 27.13]
               ],
               [
                [-21.26, 28.12, 0.55],
                [-21.24, 28.13, 0.1]
               ]
             ]
          }
        """.trimIndent()

        val spGeometry = assertNotNull(fromJson(json, SpGeometry.TYPE))
        val jtsFromJson = jtsJsonReader.read(json)

        // when
        val jtsFromProxy = GeoUtil.toJtsGeometry(spGeometry)
        val proxyFromJts = GeoUtil.toProxyGeometry(jtsFromJson)

        // then
        assertInstanceOf(MultiLineString::class.java, jtsFromProxy)
        assertEquals(jtsFromJson, jtsFromProxy)
        assertEquals(toJSON(proxyFromJts), toJSON(spGeometry))
    }

    @Test
    fun testPolygonToJts() {
        // given
        val json = """
          {
            "type": "Polygon",
            "coordinates": [
              [
                [
                  28.5968982,
                  11.7949413
                ],
                [
                  38.4130292,
                  -5.1379065
                ],
                [
                  41.6838850,
                  11.9426922
                ],
                [
                  28.5968982,
                  11.7949413
                ]
              ]
            ]
          }
        """

        val spGeometry = assertNotNull(fromJson(json, SpGeometry.TYPE))
        val jtsFromJson = jtsJsonReader.read(json)

        // when
        val jtsFromProxy = GeoUtil.toJtsGeometry(spGeometry)
        val proxyFromJts = GeoUtil.toProxyGeometry(jtsFromJson)

        // then
        assertInstanceOf(Polygon::class.java, jtsFromProxy)
        assertEquals(jtsFromJson, jtsFromProxy)
        assertEquals(toJSON(proxyFromJts), toJSON(spGeometry))
    }

    @Test
    fun testPolygonWithHolesToJts() {
        // given
        val json = """
          {
            "type": "Polygon",
            "coordinates": [
              [
                [
                  28.5968982,
                  11.7949413
                ],
                [
                  38.4130292,
                  -5.1379065
                ],
                [
                  41.6838850,
                  11.9426922
                ],
                [
                  28.5968982,
                  11.7949413
                ]
              ],
              [
                [
                  34.4933852,
                   8.3018559
                ],
                [
                  38.4048530,
                   8.1571634
                ],
                [
                  35.2452972,
                   5.5684798
                ],
                [
                  34.4933852,
                   8.3018559
                ]
              ]
            ]
          }
        """

        val spGeometry = assertNotNull(fromJson(json, SpGeometry.TYPE))
        val jtsFromJson = jtsJsonReader.read(json)

        // when
        val jtsFromProxy = GeoUtil.toJtsGeometry(spGeometry)
        val proxyFromJts = GeoUtil.toProxyGeometry(jtsFromJson)

        // then
        assertInstanceOf(Polygon::class.java, jtsFromProxy)
        assertEquals(jtsFromJson, jtsFromProxy)
        assertEquals(toJSON(proxyFromJts), toJSON(spGeometry))
    }

    @Test
    fun testMultiPolygonToJts() {
        // given
        val json = """
          {
            "type": "MultiPolygon",
            "coordinates": [
               [
                  [
                    [
                      28.5968982,
                      11.7949413
                    ],
                    [
                      38.4130292,
                      -5.1379065
                    ],
                    [
                      41.6838850,
                      11.9426922
                    ],
                    [
                      28.5968982,
                      11.7949413
                    ]
                  ]
              ],
              [
                  [
                    [
                      34.4933852,
                       8.3018559
                    ],
                    [
                      38.4048530,
                       8.1571634
                    ],
                    [
                      35.2452972,
                       5.5684798
                    ],
                    [
                      34.4933852,
                       8.3018559
                    ]
                  ]
              ]
            ]
          }
        """

        val spGeometry = assertNotNull(fromJson(json, SpGeometry.TYPE))
        val jtsFromJson = jtsJsonReader.read(json)

        // when
        val jtsFromProxy = GeoUtil.toJtsGeometry(spGeometry)
        val proxyFromJts = GeoUtil.toProxyGeometry(jtsFromJson)

        // then
        assertInstanceOf(MultiPolygon::class.java, jtsFromProxy)
        assertEquals(jtsFromJson, jtsFromProxy)
        assertEquals(toJSON(proxyFromJts), toJSON(spGeometry))
    }

    @Test
    fun testUnknownType(){
        // given
        val json = """{"coordinates":[1.0, 2.0]}"""

        // when
        val spPoint = assertNotNull(fromJson(json, SpPoint.TYPE))
        val jtsFromProxy = GeoUtil.toJtsGeometry(spPoint)

        // then
        assertEquals(SpPoint.TYPE.jsonType, spPoint.type)
        assertEquals(Point.TYPENAME_POINT, jtsFromProxy.geometryType)
        assertEquals(1.0, jtsFromProxy.coordinates[0].x)
        assertEquals(2.0, jtsFromProxy.coordinates[0].y)
    }
}