@file:Suppress("OPT_IN_USAGE")

package naksha.model.mom

import naksha.base.AnyObject
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The MOM meta namespace.
 * @since 3.0.0
 */
@JsExport
class MomMetaNs : AnyObject() {

    companion object MomMetaNsCompanion {
        /**
         * The [PlatformType] of [MomMetaNs].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MomMetaNs::class).withPackageName(PACKAGE_NAME)
    }
}