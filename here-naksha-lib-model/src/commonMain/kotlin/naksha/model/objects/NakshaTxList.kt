@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [transactions][NakshaTx].
 * @since 3.0
 */
@JsExport
class NakshaTxList : ListProxy<NakshaTx>(NakshaTx.TYPE) {
    companion object NakshaTxList_C {
        /**
         * The [PlatformType] of [NakshaTxList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NakshaTxList::class).withPackageName(PACKAGE_NAME)
    }
}