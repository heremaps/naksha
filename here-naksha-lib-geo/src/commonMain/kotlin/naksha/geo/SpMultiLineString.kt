package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class SpMultiLineString() : SpGeometry() {

    @JsName("of")
    constructor(coordinates: MultiLineStringCoord) : this() {
        setCoordinates(coordinates)
    }

    override fun getCoordinates(): MultiLineStringCoord = super.getCoordinates() as MultiLineStringCoord
    fun withCoordinates(coordinates: MultiLineStringCoord): SpMultiLineString {
        setCoordinates(coordinates)
        return this
    }
}