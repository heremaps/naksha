package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object GeometryHelper {

    const val POINT_TYPE = "Point"
    const val LINE_STRING_TYPE = "LineString"

    fun pointGeometry(long: Double, lat: Double, alt: Double?): Geometry {
        val nakPoint = NakPoint(long, lat, alt)
        val geometry = Geometry()
        geometry.setCoordinates(nakPoint)
        geometry.setType(POINT_TYPE)
        return geometry
    }

    fun lineStringGeometry(vararg points: NakPoint): Geometry {
        val lineString = NakLineString(*points)
        val geometry = Geometry()
        geometry.setCoordinates(lineString)
        geometry.setType(LINE_STRING_TYPE)
        return geometry
    }
}