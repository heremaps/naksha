@file:Suppress("OPT_IN_USAGE")

package naksha.mom.v2

import naksha.base.AnyObject
import naksha.base.NullableProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The properties of a MOM feature.
 * @since 3.0
 * @see MomFeature
 */
@JsExport
open class MomProperties : AnyObject() {

    companion object MomProperties_C {
        /**
         * The [PlatformType] of [MomProperties].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MomProperties::class).withPackageName(PACKAGE_NAME)

        /**
         * The key of the Mom-Delta namespace property (`@ns:com:here:delta`).
         * @since 3.0
         */
        const val DELTA_KEY = "@ns:com:here:mom:delta"

        /**
         * The key of the Mom-Meta namespace property (`@ns:com:here:meta`).
         * @since 3.0
         */
        const val META_KEY = "@ns:com:here:mom:meta"

        /**
         * Properties used by the deprecated Activity-Log service, just here to allow downward
         * compatibility.
         */
        const val XYZ_ACTIVITY_LOG_NS = "@ns:com:here:xyz:log"

        private val DELTA_MEMBER = NullableProperty<MomProperties, MomDeltaNs>(MomDeltaNs.TYPE, name = DELTA_KEY, autoRemove = true)
        private val META_MEMBER = NullableProperty<MomProperties, MomMetaNs>(MomMetaNs.TYPE, name = META_KEY, autoRemove = true)
        private val REFERENCES_MEMBER = NullableProperty<MomProperties, MomReferenceList>(MomReferenceList.TYPE, autoRemove = true)
    }

    /**
     * The MOM delta namespace.
     * @since 1.0
     */
    var delta by DELTA_MEMBER

    fun useDelta(): MomDeltaNs {
        var instance = this.delta
        if (instance == null) {
            instance = MomDeltaNs()
            this.delta = instance
        }
        return instance
    }

    /**
     * The MOM delta namespace.
     * @since 1.0
     */
    var meta by META_MEMBER

    fun useMeta(): MomMetaNs {
        var instance = this.meta
        if (instance == null) {
            instance = MomMetaNs()
            this.meta = instance
        }
        return instance
    }

    /**
     * References to MOM objects.
     * @since 1.0
     */
    var references by REFERENCES_MEMBER

    fun useReferences(): MomReferenceList {
        var instance = this.references
        if (instance == null) {
            instance = MomReferenceList()
            this.references = instance
        }
        return instance
    }

}