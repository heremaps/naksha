package naksha.geo

import naksha.base.JvmMap
import naksha.base.Platform
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
        val polygon = PolygonGeometry(BoundingBoxProxy(x1, y1, x2, y2))

        // then
        val coords = polygon.getCoordinates()[0]!!

        assertEquals(1.0, coords[0]!!.getLongitude())
        assertEquals(1.1, coords[0]!!.getLatitude())

        assertEquals(2.0, coords[1]!!.getLongitude())
        assertEquals(1.1, coords[1]!!.getLatitude())

        assertEquals(2.0, coords[2]!!.getLongitude())
        assertEquals(2.1, coords[2]!!.getLatitude())

        assertEquals(1.0, coords[3]!!.getLongitude())
        assertEquals(2.1, coords[3]!!.getLatitude())

        assertEquals(1.0, coords[4]!!.getLongitude())
        assertEquals(1.1, coords[4]!!.getLatitude())
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

        val proxyGeometry = (Platform.fromJSON(json) as JvmMap).proxy(PointGeometry::class)

        // when
        val jtsFromProxy = ProxyGeoUtil.toJtsGeometry(proxyGeometry)
        val jtsFromJson = jtsJsonReader.read(json)

        // then
        assertInstanceOf(Point::class.java, jtsFromProxy)
        assertEquals(jtsFromJson, jtsFromProxy)
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

        val proxyGeometry = (Platform.fromJSON(json) as JvmMap).proxy(MultiPointGeometry::class)

        // when
        val jtsFromProxy = ProxyGeoUtil.toJtsGeometry(proxyGeometry)
        val jtsFromJson = jtsJsonReader.read(json)

        // then
        assertInstanceOf(MultiPoint::class.java, jtsFromProxy)
        assertEquals(jtsFromJson, jtsFromProxy)
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

        val proxyGeometry = (Platform.fromJSON(json) as JvmMap).proxy(LineStringGeometry::class)

        // when
        val jtsFromProxy = ProxyGeoUtil.toJtsGeometry(proxyGeometry)
        val jtsFromJson = jtsJsonReader.read(json)

        // then
        assertInstanceOf(LineString::class.java, jtsFromProxy)
        assertEquals(jtsFromJson, jtsFromProxy)
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
                [-21.24, 28.13, 0]
               ]
             ]
          }
        """.trimIndent()

        val proxyGeometry = (Platform.fromJSON(json) as JvmMap).proxy(MultiLineStringGeometry::class)

        // when
        val jtsFromProxy = ProxyGeoUtil.toJtsGeometry(proxyGeometry)
        val jtsFromJson = jtsJsonReader.read(json)

        // then
        assertInstanceOf(MultiLineString::class.java, jtsFromProxy)
        assertEquals(jtsFromJson, jtsFromProxy)
    }

    @Test
    fun testPolygonToJts() {
        // given
        val json = """
          {
            "coordinates": [
              [
                [
                  28.596898251874165,
                  11.794941370567287
                ],
                [
                  38.413029200628415,
                  -5.137906502534122
                ],
                [
                  41.68388501908956,
                  11.9426922326914
                ],
                [
                  28.596898251874165,
                  11.794941370567287
                ]
              ]
            ],
            "type": "Polygon"
          }
        """.trimIndent()

        val proxyGeometry = (Platform.fromJSON(json) as JvmMap).proxy(PolygonGeometry::class)

        // when
        val jtsFromProxy = ProxyGeoUtil.toJtsGeometry(proxyGeometry)
        val jtsFromJson = jtsJsonReader.read(json)

        // then
        assertInstanceOf(Polygon::class.java, jtsFromProxy)
        assertEquals(jtsFromJson, jtsFromProxy)
    }

    @Test
    fun testPolygonWithHolesToJts() {
        // given
        val json = """
          {
            "coordinates": [
              [
                [
                  28.596898251874165,
                  11.794941370567287
                ],
                [
                  38.413029200628415,
                  -5.137906502534122
                ],
                [
                  41.68388501908956,
                  11.9426922326914
                ],
                [
                  28.596898251874165,
                  11.794941370567287
                ]
              ],
              [
                [
                  34.49338528240901,
                  8.301855939379635
                ],
                [
                  38.40485302348304,
                  8.157163482762314
                ],
                [
                  35.24529722445408,
                  5.568479831724019
                ],
                [
                  34.49338528240901,
                  8.301855939379635
                ]
              ]
            ],
            "type": "Polygon"
          }
        """.trimIndent()

        val proxyGeometry = (Platform.fromJSON(json) as JvmMap).proxy(PolygonGeometry::class)

        // when
        val jtsFromProxy = ProxyGeoUtil.toJtsGeometry(proxyGeometry)
        val jtsFromJson = jtsJsonReader.read(json)

        // then
        assertInstanceOf(Polygon::class.java, jtsFromProxy)
        assertEquals(jtsFromJson, jtsFromProxy)
    }

    @Test
    fun testMultiPolygonToJts() {
        // given
        val json = """
          {
            "coordinates": [
               [
                  [
                    [
                      28.596898251874165,
                      11.794941370567287
                    ],
                    [
                      38.413029200628415,
                      -5.137906502534122
                    ],
                    [
                      41.68388501908956,
                      11.9426922326914
                    ],
                    [
                      28.596898251874165,
                      11.794941370567287
                    ]
                  ]
              ],
              [
                  [
                    [
                      34.49338528240901,
                      8.301855939379635
                    ],
                    [
                      38.40485302348304,
                      8.157163482762314
                    ],
                    [
                      35.24529722445408,
                      5.568479831724019
                    ],
                    [
                      34.49338528240901,
                      8.301855939379635
                    ]
                  ]
              ]
            ],
            "type": "MultiPolygon"
          }
        """.trimIndent()

        val proxyGeometry = (Platform.fromJSON(json) as JvmMap).proxy(MultiPolygonGeometry::class)

        // when
        val jtsFromProxy = ProxyGeoUtil.toJtsGeometry(proxyGeometry)
        val jtsFromJson = jtsJsonReader.read(json)

        // then
        assertInstanceOf(MultiPolygon::class.java, jtsFromProxy)
        assertEquals(jtsFromJson, jtsFromProxy)
    }
}