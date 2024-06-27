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
    data object ID : PRef()
    data object APP_ID : PRef()
    data object AUTHOR : PRef()
    data object UID : PRef()
    data object UUID : PRef()
    data object GRID : PRef()
    data object TXN : PRef()
    data object TXN_NEXT : PRef()
    data object TAGS : PRef()
    class NON_INDEXED_PREF(vararg val path: String) : PRef()

    companion object {

        /**
         * Just for convenient usage in java
         */
        const val JSON_PROPERTIES = "properties"
        const val JSON_XYZ_NAMESPACE = "@ns:com:here:xyz"
        const val JSON_AUTHOR = "author"
        const val JSON_ID = "id"
        const val JSON_UUID = "uuid"
        const val JSON_APP_ID = "appId"
        const val JSON_GRID = "grid"
        const val JSON_TXN = "txn"
        const val JSON_TXN_NEXT = "txnNext"
        const val JSON_TAGS = "tags"

        private val ID_PROP_PATH = arrayOf(JSON_ID)
        private val APP_ID_PROP_PATH = arrayOf(JSON_PROPERTIES, JSON_XYZ_NAMESPACE, JSON_APP_ID)
        private val AUTHOR_PROP_PATH = arrayOf(JSON_PROPERTIES, JSON_XYZ_NAMESPACE, JSON_AUTHOR)
        private val UUID_PROP_PATH = arrayOf(JSON_PROPERTIES, JSON_XYZ_NAMESPACE, JSON_UUID)
        private val GRID_PROP_PATH = arrayOf(JSON_PROPERTIES, JSON_XYZ_NAMESPACE, JSON_GRID)
        private val TXN_PROP_PATH = arrayOf(JSON_PROPERTIES, JSON_XYZ_NAMESPACE, JSON_TXN)
        private val TXN_NEXT_PROP_PATH = arrayOf(JSON_PROPERTIES, JSON_XYZ_NAMESPACE, JSON_TXN_NEXT)
        private val TAGS_PROP_PATH = arrayOf(JSON_PROPERTIES, JSON_XYZ_NAMESPACE, JSON_TAGS)

        private val PATH_TO_PREF_MAPPING = mapOf(
            ID_PROP_PATH to ID,
            APP_ID_PROP_PATH to APP_ID,
            AUTHOR_PROP_PATH to AUTHOR,
            UUID_PROP_PATH to UUID,
            GRID_PROP_PATH to GRID,
            TXN_PROP_PATH to TXN,
            TXN_NEXT_PROP_PATH to TXN_NEXT,
            TAGS_PROP_PATH to TAGS,
        )

        @JvmStatic
        fun pRefPathMap() = PATH_TO_PREF_MAPPING

        @JvmStatic
        fun id() = ID

        @JvmStatic
        fun appId() = APP_ID

        @JvmStatic
        fun author() = AUTHOR

        @JvmStatic
        fun uid() = UID

        @JvmStatic
        fun grid() = GRID

        @JvmStatic
        fun txn() = TXN

        @JvmStatic
        fun txnNext() = TXN_NEXT

        @JvmStatic
        fun tags() = TAGS

        @JvmStatic
        fun nonIndexedPref(vararg path: String) = NON_INDEXED_PREF(*path)

        //TODO     log.atInfo().setMessage("NonIndexedPRef: {}").addArgument(path).log();
    }

}
