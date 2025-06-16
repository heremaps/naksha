@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.notification

import naksha.base.Int64
import naksha.base.Int64_TYPE
import naksha.base.MapProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.model.Version
import naksha.model.objects.PACKAGE_NAME
import naksha.model.request.FeatureTuple
import naksha.model.request.FeatureTupleList
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A map where the key is the transaction number (aka [Version]), and the value is a list of [result-rows][FeatureTuple], order by [uid][naksha.model.Metadata.uid].
 */
@JsExport
class TuplesByTxn : MapProxy<Int64, FeatureTupleList>(Int64_TYPE, FeatureTupleList.TYPE) {
    companion object TuplesByTxn_C {
        /**
         * The [PlatformType] of [TuplesByTxn].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(TuplesByTxn::class).withPackageName(PACKAGE_NAME)
    }
}
