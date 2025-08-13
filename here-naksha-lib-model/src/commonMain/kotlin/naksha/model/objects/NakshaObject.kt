@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.NullableProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.base.String_TYPE
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A Naksha object is a feature with a meaning for Naksha, so an internal Naksha object using for example in Naksha-Hub or Naksha-CLI.
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

    override val properties: NakshaProperties
        get() = getProperties(NakshaProperties.TYPE)

    /**
     * Human-readable title.
     */
    open var title by TITLE_MEMBER

    /**
     * Human-readable description.
     */
    open var description by DESCRIPTION_MEMBER
}