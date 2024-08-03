package naksha.psql

import naksha.base.Platform
import naksha.base.PlatformMap
import naksha.geo.SpGeometry
import naksha.model.*
import naksha.model.GeoEncoding.GeoEncoding_C.EWKB
import naksha.model.GeoEncoding.GeoEncoding_C.EWKB_GZIP
import naksha.model.GeoEncoding.GeoEncoding_C.GEO_JSON
import naksha.model.GeoEncoding.GeoEncoding_C.GEO_JSON_GZIP
import naksha.model.GeoEncoding.GeoEncoding_C.TWKB
import naksha.model.GeoEncoding.GeoEncoding_C.TWKB_GZIP
import naksha.model.GeoEncoding.GeoEncoding_C.WKB
import naksha.model.GeoEncoding.GeoEncoding_C.WKB_GZIP
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE")
@JsExport
actual class PgPlatform {
    actual companion object PgPlatformCompanion {
        private fun plv8Forbidden(opName: String) {
            if (isPlv8()) throw UnsupportedOperationException("${opName}: Not supported in PLV8 storage")
        }

        private fun browserForbidden(opName: String) {
            if (!isPlv8()) throw UnsupportedOperationException("${opName}: Not supported in the browser")
        }

        /**
         * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes
         * (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
         * @param parts the literal parts to merge and quote.
         * @return The quoted literal.
         */
        internal actual fun quote_literal(vararg parts: String): String?
            = if (isPlv8()) js("""
parts && parts.length>0 ? (parts.length===1 ? plv8.quote_literal(parts[0]) : plv8.quote_literal(parts.join(''))) : ''
""").unsafeCast<String>() else null

        /**
         * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes
         * (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
         */
        internal actual fun quote_ident(vararg parts: String): String?
                = if (isPlv8()) js("""
parts && parts.length>0 ? (parts.length===1 ? plv8.quote_ident(parts[0]) : plv8.quote_literal(parts.join(''))) : ''
""").unsafeCast<String>() else null

        /**
         * Calculates the partition number between 0 and 255. This is the unsigned value of the first byte of the MD5 hash above the
         * given feature-id. When there are less than 256 partitions, the value must be divided by the number of partitions and the rest
         * addresses the partition, for example for 4 partitions we get `partitionNumber(id) % 4`, what will be a value between 0 and 3.
         *
         * In PVL8 this is implemented using the native code as `get_byte(digest(id,'md5'),0)`, which is as well what the partitioning
         * statement will do.
         * @param featureId the feature id.
         * @return the partition number of the feature, a value between 0 and 255.
         */
        @JsStatic
        actual fun partitionNumber(featureId: String): Int {
            if (isPlv8()) {
                return js("plv8.execute(\"SELECT get_byte(digest(\$1,'md5'),0) as i\",[featureId])[0].i").unsafeCast<Int>()
            }
            throw UnsupportedOperationException("PgUtil::partitionNumber is not implemented in the browser yet")
        }

        /**
         * Returns the instance.
         * @param host the PostgresQL server host.
         * @param port the PostgresQL server port.
         * @param database the database to connect to.
         * @param user the user to authenticate with.
         * @param password the password to authenticate with.
         * @param readOnly if all connections to the host must read-only (the host is a read-replica).
         * @return the instance that represents this host.
         * @throws UnsupportedOperationException if executed in `PLV8` extension.
         */
        @JsStatic
        actual fun getInstance(
            host: String,
            port: Int,
            database: String,
            user: String,
            password: String,
            readOnly: Boolean
        ): PgInstance {
            plv8Forbidden("PgUtil.getInstance")
            TODO("Not yet implemented")
        }

        /**
         * Returns the instance for the given JDBC URL.
         * @param url the JDBC URL, for example `jdbc:postgresql://foo.com/bar_db?user=postgres&password=password`
         * @throws UnsupportedOperationException if executed in `PLV8` extension.
         */
        @JsStatic
        @JsName("getInstanceFromJdbcUrl")
        actual fun getInstance(url: String): PgInstance {
            plv8Forbidden("PgUtil.getInstance")
            TODO("Not yet implemented")
        }

        /**
         * Creates a new cluster configuration.
         * @param master the master PostgresQL server.
         * @param replicas the read-replicas; if any.
         * @throws UnsupportedOperationException if executed in `PLV8` extension.
         */
        @JsStatic
        actual fun newCluster(master: PgInstance, vararg replicas: PgInstance): PgCluster {
            plv8Forbidden("PgUtil.newCluster")
            TODO("Not yet implemented")
        }

        @JsStatic
        actual fun newStorage(cluster: PgCluster, schemaName: String): PgStorage {
            plv8Forbidden("PgUtil.newStorage")
            TODO("Not yet implemented")
            // Should return the NodeJsStorage!
        }

        /**
         * Tests if this code is executed within a PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         * @return _true_ if this code is executed within PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         */
        @JsStatic
        actual fun isPlv8(): Boolean = js("typeof plv8==='object'").unsafeCast<Boolean>()

        /**
         * Returns the [PLV8 extension](https://plv8.github.io/) storage.
         * @return the [PLV8 extension](https://plv8.github.io/) storage; _null_ if this code is not executed within PostgresQL database.
         * @throws UnsupportedOperationException if called, when [isPlv8] returns _false_.
         */
        @JsStatic
        actual fun getPlv8(): PgStorage {
            if (!isPlv8()) throw UnsupportedOperationException("PgUtil.getPlv8: Only supported in PLV8 storage")
            TODO("Create a new virtual storage instance and add to plv8, if not done already")
            // Note: The session is opened using the new(Read|Write)Connection call.
            //       The storage does not have a cluster
            //       We need to implement a Plv8Storage and a NodeJsStorage
        }

        /**
         * Initializes a test-storage to execute tests. If the storage is already initialized, does nothing. Do guarantee that a new
         * storage is initialized, do:
         * ```kotlin
         * if (!PgUtil.initTestStorage(options, params)) {
         *   PgUtil.getTestStorage().close()
         *   check(PgUtil.initTestStorage(options, params))
         * }
         * // The test storage will be freshly initialized!
         * ```
         * @param defaultOptions the default options for new connections.
         * @param params optional parameters to be forwarded to the test engine.
         * @return _true_ if a new test-storage was created; _false_ if there is already an existing storage.
         * @throws UnsupportedOperationException if this platform does not support running tests.
         */
        @JsStatic
        actual fun initTestStorage(defaultOptions: SessionOptions, params: Map<String, *>?): Boolean {
            plv8Forbidden("PgUtil.getTestStorage")
            // TODO: Can we fix this for JavaScript/TypeScript?
            throw UnsupportedOperationException("Testing not supported in PLV8")
        }

        /**
         * Returns the existing test-storage to execute tests. If no test storage exists yet, creates a new test storage.
         * @return the test-storage.
         * @throws UnsupportedOperationException if this platform does not support running tests.
         */
        @JsStatic
        actual fun getTestStorage(): PgStorage {
            plv8Forbidden("PgUtil.getTestStorage")
            // TODO: Can we fix this for JavaScript/TypeScript?
            throw UnsupportedOperationException("Testing not supported in PLV8")
        }

        /**
         * Create a new test-storage to execute tests.
         * @return the test-storage.
         * @throws UnsupportedOperationException if this platform does not support running tests.
         */
        @JsStatic
        actual fun newTestStorage(): PgStorage {
            TODO("Not yet implemented")
        }

        //
        // We use the native C implementations from PostGIS:
        //
        // [ST_GeomFromGeoJSON(text)](https://postgis.net/docs/ST_GeomFromGeoJSON.html)
        // [ST_AsGeoJSON(geo, 7, 0)](https://postgis.net/docs/ST_AsGeoJSON.html)
        //
        // [ST_GeomFromEWKB(bytes)](https://postgis.net/docs/ST_GeomFromEWKB.html)
        // [ST_AsEWKB(geo, 'XDR')](https://postgis.net/docs/ST_AsEWKB.html) - little-endian ('NDR') or big-endian ('XDR').
        //
        // [ST_GeomFromWKB(bytes, 4326)](https://postgis.net/docs/ST_GeomFromWKB.html)
        // [ST_AsBinary(geo, 'XDR')](https://postgis.net/docs/ST_AsBinary.html) - little-endian ('NDR') or big-endian ('XDR').
        //
        // [ST_GeomFromTWKB(bytes)](https://postgis.net/docs/ST_GeomFromTWKB.html)
        // [ST_AsTWKB(geo, 7, 7, 0, false, false)](https://postgis.net/docs/ST_AsTWKB.html)
        //
        // WKT Helper:
        // [ST_AsText(geo)](https://postgis.net/docs/ST_AsText.html)
        // [ST_GeomFromText(text, 4326)](https://postgis.net/docs/ST_GeomFromText.html)
        //
        // Example for testing:
        /*
WITH test AS (SELECT
 ST_GeomFromText('LINESTRING(-71.160281 42.258729,-71.160837 42.259113,-71.161144 42.25932)',4269) AS geo,
 ST_AsGeoJSON(ST_GeomFromText('LINESTRING(-71.160281 42.258729,-71.160837 42.259113,-71.161144 42.25932)',4269), 7, 0) AS json
)
SELECT geo, json
FROM test;
        */

        @Suppress("UNUSED_PARAMETER", "NOTHING_TO_INLINE")
        private inline fun exec(code: String, arg: dynamic): dynamic = js("plv8.execute('SELECT '+code+' as s',[arg])[0].s")

        /**
         * Decode a GeoJSON geometry from encoded bytes.
         * @param raw the bytes to decode.
         * @param flags the codec flags.
         * @return the GeoJSON geometry.
         * @since 3.0.0
         */
        actual fun decodeGeometry(raw: ByteArray?, flags: Int): SpGeometry? {
            if (raw == null) return null
            val encoding = flags.geoEncoding()
            val json: String = when (encoding) {
                TWKB_GZIP -> exec("ST_AsGeoJSON(ST_GeomFromTWKB(gunzip($1::bytea),7,1))", raw) as String
                TWKB -> exec("ST_GeomFromTWKB($1::bytea)", raw) as String
                EWKB_GZIP -> exec("ST_AsGeoJSON(ST_GeomFromEWKB(gunzip($1::bytea)))", raw) as String
                EWKB -> exec("ST_GeomFromEWKB($1::bytea)", raw) as String
                WKB_GZIP -> exec("ST_AsGeoJSON(ST_GeomFromWKB(gunzip($1::bytea),4326))", raw) as String
                WKB -> exec("ST_GeomFromWKB($1::bytea, 4326)", raw) as String
                GEO_JSON_GZIP -> Platform.gzipInflate(raw).decodeToString()
                GEO_JSON -> raw.decodeToString()
                else -> throw NakshaException(ILLEGAL_ARGUMENT, "Unknown geometry encoding")
            }
            return (Platform.fromJSON(json) as PlatformMap).proxy(SpGeometry::class)
        }

        /**
         * Encodes the given GeoJSON geometry into bytes.
         * @param geometry the geometry to encode.
         * @param flags the codec flags.
         * @return the encoded GeoJSON geometry.
         * @since 3.0.0
         */
        actual fun encodeGeometry(geometry: SpGeometry?, flags: Int): ByteArray? {
            if (geometry == null) return null
            val json = Platform.toJSON(geometry)
            val encoding = flags.geoEncoding()
            return when(encoding) {
                TWKB_GZIP -> exec("gzip(ST_AsTWKB(ST_GeomFromGeoJSON(\$1), 7, 7, 0, false, false))", json) as ByteArray
                TWKB -> exec("ST_AsTWKB(ST_GeomFromGeoJSON(\$1), 7, 7, 0, false, false)", json) as ByteArray
                EWKB_GZIP -> exec("gzip(ST_AsEWKB(ST_GeomFromGeoJSON(\$1),'XDR'))", json) as ByteArray
                EWKB -> exec("ST_AsEWKB(ST_GeomFromGeoJSON(\$1),'XDR')", json) as ByteArray
                WKB_GZIP -> exec("gzip(ST_AsBinary(ST_GeomFromGeoJSON(\$1),'XDR'))", json) as ByteArray
                WKB -> exec("ST_AsBinary(ST_GeomFromGeoJSON(\$1),'XDR')", json) as ByteArray
                GEO_JSON_GZIP -> Platform.gzipDeflate(json.encodeToByteArray())
                GEO_JSON -> json.encodeToByteArray()
                else -> throw NakshaException(ILLEGAL_ARGUMENT, "Unknown geometry encoding")
            }
        }

    }
}