@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.AnyObject
import naksha.base.NullableProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.base.String_TYPE
import naksha.geo.BBox
import naksha.geo.SpGeometry
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A Naksha object is a feature with a meaning for Naksha/
 *
 * @since 3.0
 * @see NakshaObject
 * @see NakshaStorage
 * @see NakshaMap
 * @see NakshaCollection
 * @see NakshaDictionary
 * @see NakshaSubscriptionState
 * @see NakshaTx
 */
@JsExport
open class NakshaObject : NakshaFeature() {
    companion object NakshaObject_C {
        /**
         * The [PlatformType] of [NakshaObject].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NakshaObject::class).withPackageName(PACKAGE_NAME)

        private val TITLE_MEMBER = NullableProperty<NakshaFeature, String>(String_TYPE)
        private val DESCRIPTION_MEMBER = NullableProperty<NakshaFeature, String>(String_TYPE)
    }

    override fun withId(id: String): NakshaObject = super.withId(id) as NakshaObject
    override fun withBBox(bbox: BBox): NakshaObject = super.withBBox(bbox) as NakshaObject
    override fun withAutoBBox(): NakshaObject = super.withAutoBBox() as NakshaObject
    override fun withGeometry(geometry: SpGeometry?): NakshaObject = super.withGeometry(geometry) as NakshaObject
    override val properties: NakshaProperties
        get() = get_properties(NakshaProperties.TYPE)
    override fun withProperties(properties: AnyObject): NakshaObject = super.withProperties(properties) as NakshaObject

    /**
     * Human-readable title.
     */
    open var title by TITLE_MEMBER

    /**
     * Human-readable description.
     */
    open var description by DESCRIPTION_MEMBER
}