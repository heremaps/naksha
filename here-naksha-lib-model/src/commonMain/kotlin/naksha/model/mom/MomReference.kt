@file:Suppress("OPT_IN_USAGE")

package naksha.model.mom

import naksha.base.NullableProperty
import naksha.base.AnyObject
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import naksha.base.String_TYPE
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * MOM reference object holding minimum equivalent fields from MOM reference object.
 */
@JsExport
class MomReference() : AnyObject() {

    @JsName("of")
    constructor(id: String?, spaceId: String?, featureType: String?) : this() {
        this.id = id
        this.spaceId = spaceId
        this.featureType = featureType
    }

    companion object MomReferenceCompanion {
        /**
         * The [PlatformType] of [MomReference].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MomReference::class).withPackageName(PACKAGE_NAME)

        private val STRING_NULL = NullableProperty<MomReference, String>(String_TYPE)
    }

    /**
     * The feature id that is referred.
     */
    var id by STRING_NULL

    /**
     * The space that is referred.
     */
    var spaceId by STRING_NULL

    /**
     * The feature-type is referred (is contained in the [id], because MOM defines IDs as URNs).
     */
    var featureType by STRING_NULL
}