@file:Suppress("OPT_IN_USAGE")

package naksha.mom.v2

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [MOM references][MomReference].
 * @since 3.0
 */
@JsExport
class MomReferenceList : ListProxy<MomReference>(MomReference.TYPE) {
    companion object MomReferenceList_C {
        /**
         * The [PlatformType] of [MomReferenceList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MomReferenceList::class).withPackageName(PACKAGE_NAME)
    }
}
