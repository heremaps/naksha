package naksha.psql

/**
 * Enumeration of all PostgreSQL schemas (map IDs) that are created by the test suite.
 *
 * Every entry corresponds to one Naksha map (catalog) that is provisioned during testing.
 * The [id] property holds the exact schema name used in the database.
 *
 * - [SHARED] is the single shared map used by the majority of tests (those that pass `mapId = null`
 *   to [PgTestBase]).
 * - All other entries are dedicated per-test-class maps used by tests that pass `mapId = ""` to
 *   [PgTestBase], which resolves the map name to the camelCase representation of the class name.
 *
 * @since 3.0
 */
enum class TestMap(val id: String) {
    /** Shared map used by tests that do **not** request their own schema (`mapId = null`). */
    SHARED("naksha_psql_test"),

    /** Dedicated map for [CollectionTests]. */
    COLLECTION_TESTS("collection_tests"),

    /** Dedicated map for [DeleteFeaturePartitioned]. */
    DELETE_FEATURE_PARTITIONED("delete_feature_partitioned"),

    /** Dedicated map for [DeleteFeatureTest]. */
    DELETE_FEATURE_TEST("delete_feature_test"),

    /** Dedicated map for [ReadFeaturesByGeometryTest]. */
    READ_FEATURES_BY_GEOMETRY_TEST("read_features_by_geometry_test"),

    /** Dedicated map for [ReadFeaturesByMetadataTest]. */
    READ_FEATURES_BY_METADATA_TEST("read_features_by_metadata_test"),

    /** Dedicated map for [ReadFeaturesByRefTilesTest]. */
    READ_FEATURES_BY_REF_TILES_TEST("read_features_by_ref_tiles_test"),

    /** Dedicated map for [TupleNumberPersistenceTest]. */
    TUPLE_NUMBER_PERSISTENCE_TEST("tuple_number_persistence_test"),

    /** Dedicated map for [UpdateFeatureTest]. */
    UPDATE_FEATURE_TEST("update_feature_test"),
    ;

    override fun toString(): String = id

    companion object {
        /**
         * Returns the [TestMap] whose [id] matches [schemaName], or `null` if none matches.
         */
        fun byId(schemaName: String): TestMap? = entries.firstOrNull { it.id == schemaName }
    }
}
