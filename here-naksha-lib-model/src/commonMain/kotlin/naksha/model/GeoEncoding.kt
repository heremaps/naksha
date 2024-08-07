@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport

/**
 * Helper for geometry encoding in [Flags].
 */
@JsExport
class GeoEncoding : FlagsBits() {
    companion object GeoEncoding_C {
        /**
         * Geometry encoded in [TWKB](https://github.com/TWKB/Specification/blob/master/twkb.md) with precision 7 (so it fits into 32-bit integer encoding).
         *
         * See as well [Dan Bastons Post about optimizing PostgresQL geometries](http://www.danbaston.com/posts/2018/02/15/optimizing-postgis-geometries.html)
         */
        const val TWKB = 0 shl GEO_SHIFT

        /**
         * Geometry encoded in [TWKB](https://github.com/TWKB/Specification/blob/master/twkb.md) with precision 7  (so it fits into 32-bit integer encoding), compressed using [GZIP](https://en.wikipedia.org/wiki/Gzip).
         *
         * See as well [Dan Bastons Post about optimizing PostgresQL geometries](http://www.danbaston.com/posts/2018/02/15/optimizing-postgis-geometries.html)
         */
        const val TWKB_GZIP = 1 shl GEO_SHIFT

        /**
         * Geometry encoded in [WKB](https://en.wikipedia.org/wiki/Well-known_text_representation_of_geometry).
         */
        const val WKB = 2 shl GEO_SHIFT

        /**
         * Geometry encoded in [WKB](https://en.wikipedia.org/wiki/Well-known_text_representation_of_geometry), compressed using
         * [GZIP](https://en.wikipedia.org/wiki/Gzip).
         */
        const val WKB_GZIP = 3 shl GEO_SHIFT

        /**
         * Geometry encoded in [EWKB](https://en.wikipedia.org/wiki/Well-known_text_representation_of_geometry).
         */
        const val EWKB = 4 shl GEO_SHIFT

        /**
         * Geometry encoded in [EWKB](https://en.wikipedia.org/wiki/Well-known_text_representation_of_geometry), compressed using
         * [GZIP](https://en.wikipedia.org/wiki/Gzip).
         */
        const val EWKB_GZIP = 5 shl GEO_SHIFT

        /**
         * Geometry stored in [GeoJSON](https://datatracker.ietf.org/doc/html/rfc7946).
         */
        const val GEO_JSON = 6 shl GEO_SHIFT

        /**
         * Geometry stored in [GeoJSON](https://datatracker.ietf.org/doc/html/rfc7946), compressed using
         * [GZIP](https://en.wikipedia.org/wiki/Gzip).
         */
        const val GEO_JSON_GZIP = 7 shl GEO_SHIFT
    }
}
