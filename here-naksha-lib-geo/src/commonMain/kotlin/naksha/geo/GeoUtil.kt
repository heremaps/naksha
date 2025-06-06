package naksha.geo

/**
 * Geometry utilities.
 * @since 3.0.0
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class GeoUtil private constructor() {
    companion object GeoUtilCompanion {
        /**
         * Decode a TWKB GeoJSON geometry from encoded bytes.
         * @param raw the TWKB.
         * @return the GeoJSON geometry.
         * @since 3.0.0
         */
        fun fromTWKB(raw: ByteArray?): SpGeometry?

        /**
         * Decode a EWKB GeoJSON geometry from encoded bytes.
         * @param raw the EWKB.
         * @return the GeoJSON geometry.
         * @since 3.0.0
         */
        fun fromEWKB(raw: ByteArray?): SpGeometry?

        /**
         * Decode a WKB GeoJSON geometry from encoded bytes.
         * @param raw the WKB.
         * @return the GeoJSON geometry.
         * @since 3.0.0
         */
        fun fromWKB(raw: ByteArray?): SpGeometry?

        /**
         * Encodes the given GeoJSON geometry into TWKB bytes.
         * @param geometry the geometry to encode.
         * @return the encoded GeoJSON geometry.
         * @since 3.0.0
         */
        fun toTWKB(geometry: SpGeometry?): ByteArray?

        /**
         * Encodes the given GeoJSON geometry into EWKB bytes.
         * @param geometry the geometry to encode.
         * @return the encoded GeoJSON geometry.
         * @since 3.0.0
         */
        fun toEWKB(geometry: SpGeometry?): ByteArray?

        /**
         * Encodes the given GeoJSON geometry into WKB bytes.
         * @param geometry the geometry to encode.
         * @return the encoded GeoJSON geometry.
         * @since 3.0.0
         */
        fun toWKB(geometry: SpGeometry?): ByteArray?
    }
}