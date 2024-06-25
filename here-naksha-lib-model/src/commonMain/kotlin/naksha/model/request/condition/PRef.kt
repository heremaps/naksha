package naksha.model.request.condition

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * All property operations.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
sealed class PRef {
    data object ID: PRef()
    data object APP_ID: PRef()
    data object AUTHOR: PRef()
    data object UID: PRef()
    data object GRID: PRef()
    data object TXN: PRef()
    data object TXN_NEXT: PRef()
    data object TAGS: PRef()
    class NON_INDEXED_PREF(vararg val path: String): PRef()

    companion object{

        /**
         * Just for convenient usage in java
         */
        @JvmStatic fun id() = ID
        @JvmStatic fun appId() = APP_ID
        @JvmStatic fun author() = AUTHOR
        @JvmStatic fun uid() = UID
        @JvmStatic fun grid() = GRID
        @JvmStatic fun txn() = TXN
        @JvmStatic fun txnNext() = TXN_NEXT
        @JvmStatic fun tags() = TAGS
        @JvmStatic fun nonIndexedPref(vararg path: String) = NON_INDEXED_PREF(*path)
        //TODO     log.atInfo().setMessage("NonIndexedPRef: {}").addArgument(path).log();
    }

}