@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * How the binary `feature` payload of a [Tuple] is serialized.
 *
 * Stored per-collection (see [naksha.model.objects.NakshaCollection.dataEncoding]) and propagated
 * onto each decoded [Metadata.dataEncoding] so [Naksha.decodeFeature] / [Naksha.encodeFeature]
 * know how to (un)pack the bytes.
 *
 * @since 3.0
 */
@JsExport
class DataEncoding : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = DataEncoding::class

    override fun initClass() {}

    /**
     * The numeric tag for this encoding (0..3). Persisted by some SQL helpers (e.g.
     * `naksha_feature(bytea, encoding int4)`) that need to dispatch on it at query time.
     */
    var intValue: Int = 0
        private set

    /**
     * Whether the payload is GZIP-compressed before/after the inner encoder.
     */
    var gzip: Boolean = false
        private set

    @Suppress("MemberVisibilityCanBePrivate")
    companion object DataEncoding_C {
        internal const val JBON_VALUE = 0
        internal const val JBON_GZIP_VALUE = 1
        internal const val JSON_VALUE = 2
        internal const val JSON_GZIP_VALUE = 3
        internal const val JBON2_VALUE = 4
        internal const val JBON2_GZIP_VALUE = 5

        /**
         * [JBON](https://github.com/heremaps/naksha/blob/v3/docs/JBON.md), uncompressed.
         */
        @JsStatic
        @JvmField
        val JBON = defIgnoreCase(DataEncoding::class, "JBON") { self ->
            self.intValue = JBON_VALUE
            self.gzip = false
        }

        /**
         * [JBON](https://github.com/heremaps/naksha/blob/v3/docs/JBON.md), GZIP-compressed.
         */
        @JsStatic
        @JvmField
        val JBON_GZIP = defIgnoreCase(DataEncoding::class, "JBON_GZIP") { self ->
            self.intValue = JBON_GZIP_VALUE
            self.gzip = true
        }

        /**
         * [JSON](https://datatracker.ietf.org/doc/html/rfc8259), uncompressed.
         */
        @JsStatic
        @JvmField
        val JSON = defIgnoreCase(DataEncoding::class, "JSON") { self ->
            self.intValue = JSON_VALUE
            self.gzip = false
        }

        /**
         * [JSON](https://datatracker.ietf.org/doc/html/rfc8259), GZIP-compressed.
         */
        @JsStatic
        @JvmField
        val JSON_GZIP = defIgnoreCase(DataEncoding::class, "JSON_GZIP") { self ->
            self.intValue = JSON_GZIP_VALUE
            self.gzip = true
        }

        /**
         * [JBON2](https://github.com/heremaps/naksha/blob/v3/docs/latest/JBON2.md), uncompressed.
         */
        @JsStatic
        @JvmField
        val JBON2 = defIgnoreCase(DataEncoding::class, "JBON2") { self ->
            self.intValue = JBON2_VALUE
            self.gzip = false
        }

        /**
         * [JBON2](https://github.com/heremaps/naksha/blob/v3/docs/latest/JBON2.md), GZIP-compressed.
         */
        @JsStatic
        @JvmField
        val JBON2_GZIP = defIgnoreCase(DataEncoding::class, "JBON2_GZIP") { self ->
            self.intValue = JBON2_GZIP_VALUE
            self.gzip = true
        }

        private val FROM_VALUE = mapOf(
            JBON_VALUE to JBON,
            JBON_GZIP_VALUE to JBON_GZIP,
            JSON_VALUE to JSON,
            JSON_GZIP_VALUE to JSON_GZIP,
            JBON2_VALUE to JBON2,
            JBON2_GZIP_VALUE to JBON2_GZIP,
        )

        /**
         * Default encoding for new collections and admin reads when nothing else is configured.
         */
        @JsStatic
        @JvmField
        val DEFAULT = JBON_GZIP

        /**
         * Look up a [DataEncoding] by its [intValue]. Returns [DEFAULT] for any unknown value.
         */
        @JsStatic
        @JvmStatic
        fun fromValue(value: Int): DataEncoding = FROM_VALUE[value] ?: DEFAULT
    }
}
