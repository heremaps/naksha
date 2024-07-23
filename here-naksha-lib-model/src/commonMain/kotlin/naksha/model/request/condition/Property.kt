@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * The query fields.
 */
@JsExport
class Property(var name: String, var path: Array<String>? = null) {

    /**
     * Sets the name.
     * @param name the name to set.
     * @return this.
     */
    fun withName(name: String): Property {
        this.name = name
        return this
    }

    /**
     * Set the path.
     * @param path the path to set.
     * @return this.
     */
    fun withPath(path: Array<String>?): Property {
        this.path = path
        return this
    }

    /**
     * Adds the given path-segment to the end of the current path.
     * @param pathSegment the path-segment to add.
     * @return this.
     */
    fun add(pathSegment: String): Property {
        val path = this.path
        this.path = if (path != null) path + pathSegment else arrayOf(pathSegment)
        return this
    }

    companion object PropRefCompanion {
        /**
         * Returns the virtual `uuid` property, being a string. Effectively this requires a [naksha.model.Guid] in stringified form, and will eventually be resolved into a query for [Row.id][naksha.model.Row.id], [Metadata.txn][naksha.model.Metadata.txn] and [Metadata.uid][naksha.model.Metadata.uid].
         */
        @JvmStatic
        @JsStatic
        fun uuid(): Property = Property("uuid")

        /**
         * Returns the virtual `puuid` property, being a string. Effectively this requires a [naksha.model.Guid] in stringified form, and will eventually be resolved into a query for [Row.id][naksha.model.Row.id], [Metadata.ptxn][naksha.model.Metadata.ptxn] and [Metadata.puid][naksha.model.Metadata.puid]
         */
        @JvmStatic
        @JsStatic
        fun puuid(): Property = Property("puuid")

        /**
         * Returns a property reference to the feature-id.
         */
        @JvmStatic
        @JsStatic
        fun id(): Property = Property("id")

        /**
         * Returns a property reference to the transaction number.
         */
        @JvmStatic
        @JsStatic
        fun txn(): Property = Property("txn")

        /**
         * Returns a property reference to the transaction local unique identifier.
         */
        @JvmStatic
        @JsStatic
        fun uid(): Property = Property("uid")

        /**
         * Returns a property reference to the author of the feature.
         */
        @JvmStatic
        @JsStatic
        fun author(): Property = Property( "author")

        /**
         * Returns a property reference to the author timestamp of the feature.
         */
        @JvmStatic
        @JsStatic
        fun authorTs() = Property("author_ts")

        /**
         * Returns a property reference to the app-id of the feature.
         */
        @JvmStatic
        @JsStatic
        fun appId() = Property("app_id")

        /**
         * Returns a property reference to the root of the feature itself, requires a path.
         */
        @JvmStatic
        @JsStatic
        fun feature(vararg path: String) = Property("feature", arrayOf(*path))

        /**
         * Returns a property reference to the properties of the feature, requires a path.
         */
        @JvmStatic
        @JsStatic
        fun properties(vararg path: String) = Property("properties", arrayOf(*path))
    }
}