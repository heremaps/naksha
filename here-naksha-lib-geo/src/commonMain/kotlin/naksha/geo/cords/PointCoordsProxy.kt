@file:Suppress("OPT_IN_USAGE")

<<<<<<<< HEAD:here-naksha-lib-geo/src/commonMain/kotlin/naksha/geo/PointCoordsProxy.kt
package naksha.geo
|||||||| bf4352af3:here-naksha-lib-model/src/commonMain/kotlin/naksha/model/PointProxy.kt
package naksha.model
========
package naksha.geo.cords
>>>>>>>> origin/v3:here-naksha-lib-geo/src/commonMain/kotlin/naksha/geo/cords/PointCoordsProxy.kt

<<<<<<<< HEAD:here-naksha-lib-geo/src/commonMain/kotlin/naksha/geo/PointCoordsProxy.kt
|||||||| bf4352af3:here-naksha-lib-model/src/commonMain/kotlin/naksha/model/PointProxy.kt
import naksha.base.P_List
========
import naksha.base.AbstractListProxy
>>>>>>>> origin/v3:here-naksha-lib-geo/src/commonMain/kotlin/naksha/geo/cords/PointCoordsProxy.kt
import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
<<<<<<<< HEAD:here-naksha-lib-geo/src/commonMain/kotlin/naksha/geo/PointCoordsProxy.kt
class PointCoordsProxy() : CoordinatesProxy<Double>(Double::class) {

    @JsName("of")
    constructor(vararg coords: Double?) : this() {
        addAll(coords)
    }
|||||||| bf4352af3:here-naksha-lib-model/src/commonMain/kotlin/naksha/model/PointProxy.kt
class PointProxy: P_List<Double>(Double::class) {
========
class PointCoordsProxy() : AbstractListProxy<Double>(Double::class) {

    @JsName("of")
    constructor(vararg coords: Double?) : this() {
        addAll(coords)
    }
>>>>>>>> origin/v3:here-naksha-lib-geo/src/commonMain/kotlin/naksha/geo/cords/PointCoordsProxy.kt

    fun getLongitude(): Double? = get(0)
    fun setLongitude(value: Double?): Double? = set(0, value)
    fun getLatitude(): Double? = get(1)
    fun setLatitude(value: Double?): Double? = set(1, value)
    fun getAltitude(): Double? = get(2)
    fun setAltitude(value: Double?): Double? = set(2, value)
}