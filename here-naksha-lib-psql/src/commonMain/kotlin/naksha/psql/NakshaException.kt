@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import kotlin.DeprecationLevel.WARNING
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@Deprecated(
    "Too many exceptions",
    replaceWith = ReplaceWith("naksha.model.StorageException"),
    level = WARNING)
@JsExport
class NakshaException private constructor(
        val errNo: String,
        val errMsg: String,
        val id: String? = null,
        val feature: ByteArray? = null,
        val flags : Int? = null,
        val geo: Any? = null,
        val tags: ByteArray? = null,
        val xyz: ByteArray? = null
) : RuntimeException(errMsg) {
    companion object {
        @JvmStatic
        fun forId(errNo:String, errMsg:String, id :String?) : NakshaException =
                NakshaException(errNo, errMsg, id)

        @JvmStatic
        fun forBulk(errNo: String, errMsg: String): NakshaException = NakshaException(errNo, errMsg, null)
    }
}
