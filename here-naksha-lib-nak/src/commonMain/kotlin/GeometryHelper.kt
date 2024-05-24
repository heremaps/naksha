package com.here.naksha.lib.base

import com.here.naksha.lib.base.Geometry.Companion.LINE_STRING_TYPE
import com.here.naksha.lib.base.Geometry.Companion.POINT_TYPE
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object GeometryHelper {

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

    // TODO implement other types
}