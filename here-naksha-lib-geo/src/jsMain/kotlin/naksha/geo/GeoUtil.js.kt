@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.Base
import naksha.base.PlatformMap

/**
 * Geometry utilities.
 * @since 3.0.0
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@JsExport
actual class GeoUtil private actual constructor() {
    actual companion object GeoUtil_C {

        // ----------------------------------< JS only >------------------------------------------

        private fun isPlv8(): Boolean = js("typeof plv8==='object'").unsafeCast<Boolean>()
        private fun plv8Forbidden(opName: String) {
            if (isPlv8()) throw UnsupportedOperationException("${opName}: Not supported in PLV8 storage")
        }
        private fun browserForbidden(opName: String) {
            if (!isPlv8()) throw UnsupportedOperationException("${opName}: Not supported in the browser")
        }
        @Suppress("UNUSED_PARAMETER", "NOTHING_TO_INLINE")
        private inline fun exec(code: String, arg: dynamic): dynamic = js("plv8.execute('SELECT '+code+' as s',[arg])[0].s")

        // ----------------------------------< ACTUAL >-------------------------------------------

        /**
         * Decode a TWKB GeoJSON geometry from encoded bytes.
         * @param raw the TWKB.
         * @return the GeoJSON geometry.
         * @since 3.0.0
         */
        @JsStatic
        actual fun fromTWKB(raw: ByteArray?): SpGeometry? {
            if (raw == null) return null
            browserForbidden("fromTWKB")
            val json = exec("ST_GeomFromTWKB($1::bytea)", raw) as String
            return (Base.fromJSON(json) as PlatformMap).proxy(SpGeometry::class)
        }

        /**
         * Decode a EWKB GeoJSON geometry from encoded bytes.
         * @param raw the EWKB.
         * @return the GeoJSON geometry.
         * @since 3.0.0
         */
        @JsStatic
        actual fun fromEWKB(raw: ByteArray?): SpGeometry? {
            if (raw == null) return null
            browserForbidden("fromEWKB")
            val json = exec("ST_GeomFromEWKB($1::bytea)", raw) as String
            return (Base.fromJSON(json) as PlatformMap).proxy(SpGeometry::class)
        }

        /**
         * Decode a WKB GeoJSON geometry from encoded bytes.
         * @param raw the WKB.
         * @return the GeoJSON geometry.
         * @since 3.0.0
         */
        @JsStatic
        actual fun fromWKB(raw: ByteArray?): SpGeometry? {
            if (raw == null) return null
            browserForbidden("fromWKB")
            val json = exec("ST_GeomFromWKB($1::bytea, 4326)", raw) as String
            return (Base.fromJSON(json) as PlatformMap).proxy(SpGeometry::class)
        }

        /**
         * Encodes the given GeoJSON geometry into TWKB bytes.
         * @param geometry the geometry to encode.
         * @return the encoded GeoJSON geometry.
         * @since 3.0.0
         */
        @JsStatic
        actual fun toTWKB(geometry: SpGeometry?): ByteArray? {
            if (geometry == null) return null
            browserForbidden("toTWKB")
            val json = Base.toJSON(geometry)
            return exec("ST_AsTWKB(ST_GeomFromGeoJSON(\$1), 7, 7, 0, false, false)", json) as ByteArray
        }

        /**
         * Encodes the given GeoJSON geometry into EWKB bytes.
         * @param geometry the geometry to encode.
         * @return the encoded GeoJSON geometry.
         * @since 3.0.0
         */
        @JsStatic
        actual fun toEWKB(geometry: SpGeometry?): ByteArray? {
            if (geometry == null) return null
            browserForbidden("toEWKB")
            val json = Base.toJSON(geometry)
            return exec("ST_AsEWKB(ST_GeomFromGeoJSON(\$1),'XDR')", json) as ByteArray
        }

        /**
         * Encodes the given GeoJSON geometry into WKB bytes.
         * @param geometry the geometry to encode.
         * @return the encoded GeoJSON geometry.
         * @since 3.0.0
         */
        @JsStatic
        actual fun toWKB(geometry: SpGeometry?): ByteArray? {
            if (geometry == null) return null
            browserForbidden("toWKB")
            val json = Base.toJSON(geometry)
            return exec("ST_AsBinary(ST_GeomFromGeoJSON(\$1),'XDR')", json) as ByteArray
        }
    }
}