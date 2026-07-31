package com.here.naksha.app.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaAppInjection;
import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.app.common.TestUtil;
import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.naksha.SpaceProperties;
import com.here.naksha.lib.core.util.CollectionIndexPolicy;
import com.here.naksha.lib.handlers.DefaultStorageHandler;
import com.here.naksha.lib.handlers.DefaultStorageHandlerProperties;
import java.net.http.HttpResponse;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import naksha.base.Platform;
import naksha.geo.SpPoint;
import naksha.model.NakshaContext;
import naksha.model.XyzFeatureCollection;
import naksha.model.objects.Index;
import naksha.model.objects.IndexList;
import naksha.model.objects.JsonPath;
import naksha.model.objects.Member;
import naksha.model.objects.MemberType;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.ops.Equals;
import naksha.model.request.ops.Gte;
import naksha.model.request.ops.Intersects;
import naksha.model.request.ops.Lt;
import naksha.model.request.ops.Op;
import naksha.model.request.ops.StartsWith;
import naksha.model.request.ops.TagEquals;
import naksha.model.request.ops.TagListContainsAllOf;
import naksha.model.request.ops.TagListContainsAnyOf;
import naksha.model.request.ops.TagMapHasKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in diagnostic for native custom members and indices as they travel through the Hub REST API,
 * {@link DefaultStorageHandler}, and lib-psql. This test intentionally retains the created schema
 * objects and rows for manual inspection.
 */
@EnabledIfEnvironmentVariable(named = "NAKSHA_RUN_CUSTOM_MEMBER_PROBE", matches = "(?i)true")
@EnabledIfEnvironmentVariable(named = "NAKSHA_APP_SERVICE_TEST_CONTEXT", matches = "LOCAL_STANDALONE")
@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_DATA_DB_URL", matches = "jdbc:postgresql://.+")
@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_ADMIN_DB_URL", matches = "jdbc:postgresql://.+")
@EnabledIfEnvironmentVariable(named = "NAKSHA_CUSTOM_MEMBER_PROBE_RUN_ID", matches = "[a-z][a-z0-9_]{2,24}")
class CustomMemberIndexProbeIT extends ApiTest {

  private static final Logger log = LoggerFactory.getLogger(CustomMemberIndexProbeIT.class);
  private static final NakshaTestWebClient CLIENT = new NakshaTestWebClient(300);
  private static final String SCHEMA = "naksha_data_schema";

  private static final String PROBE_BOOL = "probe_bool";
  private static final String PROBE_I8 = "probe_i8";
  private static final String PROBE_I16 = "probe_i16";
  private static final String PROBE_I32 = "probe_i32";
  private static final String PROBE_I64 = "probe_i64";
  private static final String PROBE_F32 = "probe_f32";
  private static final String PROBE_F64 = "probe_f64";
  private static final String PROBE_LABEL = "probe_label";
  private static final String PROBE_BYTES = "probe_bytes";
  private static final String PROBE_TN = "probe_tn";
  private static final String PROBE_SPATIAL = "probe_spatial";
  private static final String PROBE_TAG_MAP = "probe_tag_map";
  private static final String PROBE_TAG_MAP_ARRAY = "probe_tag_map_array";
  private static final String PROBE_TAG_LIST = "probe_tag_list";

  CustomMemberIndexProbeIT() {
    super(CLIENT);
  }

  @Test
  void runRetainedCustomMemberCheckpoint(@NakshaAppInjection NakshaApp app) throws Exception {
    String runId = requiredEnvironment("NAKSHA_CUSTOM_MEMBER_PROBE_RUN_ID");
    String jdbcUrl = requiredEnvironment("NAKSHA_TEST_DATA_DB_URL");
    String collectionId = "custom_member_probe_" + runId;
    String matchingFeatureId = "custom_member_match_" + runId;
    String standardGeoFeatureId = "custom_member_standard_geo_" + runId;

    NakshaCollection collection = allTypesCollection(collectionId);
    ProbeResources resources = createResources("custom_member_" + runId, collection);

    HttpResponse<String> createResponse = CLIENT.post(
        "hub/spaces/" + resources.spaceId + "/features",
        featureCollectionJson(matchingFeatureId, standardGeoFeatureId),
        UUID.randomUUID().toString());
    assertEquals(200, createResponse.statusCode(), createResponse.body());

    verifyHttpReadback(resources.spaceId, matchingFeatureId);
    DefaultStorageHandler realHandler = realHandler(app, resources);
    verifyWorkingMemberQueries(realHandler, matchingFeatureId);
    characterizeKnownQueryDefects(realHandler, matchingFeatureId, standardGeoFeatureId);

    ProbeResources booleanFailure = createResources(
        "custom_member_bool_failure_" + runId,
        invalidBooleanIndexCollection("custom_member_bool_failure_" + runId));
    ProbeResources missingMemberFailure = createResources(
        "custom_member_missing_failure_" + runId,
        missingMemberIndexCollection("custom_member_missing_failure_" + runId));
    assertRejectedAutoCreate(booleanFailure);
    assertRejectedAutoCreate(missingMemberFailure);

    try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
      verifyPhysicalColumns(connection, collectionId);
      verifyPhysicalIndices(connection, collectionId);
      verifyRawMaterializedValues(connection, collectionId);
      verifyExactIndexPlans(connection, collectionId);
      assertTableAbsent(connection, booleanFailure.collection.getId());
      assertTableAbsent(connection, missingMemberFailure.collection.getId());
      printInspectionSummary(connection, runId, resources, booleanFailure, missingMemberFailure);
    }
  }

  private static ProbeResources createResources(String prefix, NakshaCollection collection) throws Exception {
    String handlerId = prefix + "_handler";
    String spaceId = prefix + "_space";
    String handlerJson = """
        {
          "id": "%s",
          "type": "EventHandler",
          "title": "Custom member diagnostic handler",
          "description": "Opt-in native custom member diagnostic",
          "className": "com.here.naksha.lib.handlers.DefaultStorageHandler",
          "active": true,
          "properties": {
            "storageId": "%s",
            "autoCreateCollection": true,
            "autoDeleteCollection": false,
            "collection": %s
          }
        }
        """.formatted(handlerId, databaseId, Platform.toJSON(collection));
    assertAdminCreated("hub/handlers", handlerJson);

    String spaceJson = """
        {
          "id": "%s",
          "type": "Space",
          "title": "Custom member diagnostic space",
          "description": "Opt-in native custom member diagnostic",
          "eventHandlerIds": ["%s"],
          "properties": {}
        }
        """.formatted(spaceId, handlerId);
    assertAdminCreated("hub/spaces", spaceJson);
    return new ProbeResources(handlerId, spaceId, collection);
  }

  private static void assertAdminCreated(String path, String json) throws Exception {
    HttpResponse<String> response = CLIENT.post(path, json, UUID.randomUUID().toString());
    assertEquals(200, response.statusCode(), response.body());
  }

  private static NakshaCollection allTypesCollection(String collectionId) {
    NakshaCollection collection = new NakshaCollection(collectionId).withXyzMembers();
    collection.addMember(member(PROBE_BOOL, MemberType.BOOLEAN, "properties", "bool_value"));
    collection.addMember(member(PROBE_I8, MemberType.INT8, "properties", "i8_value"));
    collection.addMember(new Member(PROBE_I16, MemberType.INT16, new JsonPath("properties", "samples", 1)));
    collection.addMember(member(PROBE_I32, MemberType.INT32, "properties", "i32_value"));
    collection.addMember(member(PROBE_I64, MemberType.INT64, "properties", "i64_value"));
    collection.addMember(member(PROBE_F32, MemberType.FLOAT32, "properties", "f32_value"));
    collection.addMember(member(PROBE_F64, MemberType.FLOAT64, "properties", "f64_value"));
    collection.addMember(member(PROBE_LABEL, MemberType.STRING, "properties", "details", "label"));
    collection.addMember(member(PROBE_BYTES, MemberType.BYTE_ARRAY, "properties", "missing_bytes"));
    collection.addMember(member(PROBE_TN, MemberType.TUPLE_NUMBER, "properties", "missing_tn"));
    collection.addMember(member(PROBE_SPATIAL, MemberType.SPATIAL, "properties", "custom_geometry"));
    collection.addMember(member(PROBE_TAG_MAP, MemberType.TAG_MAP, "properties", "custom_tag_map"));
    collection.addMember(member(PROBE_TAG_MAP_ARRAY, MemberType.TAG_MAP_FROM_ARRAY, "properties", "custom_tag_array"));
    collection.addMember(member(PROBE_TAG_LIST, MemberType.TAG_LIST, "properties", "custom_tag_list"));
    collection.addMember(member("probe_missing", MemberType.STRING, "properties", "not_present"));
    collection.addMember(member("probe_explicit_null", MemberType.STRING, "properties", "explicit_null"));
    collection.addMember(member("probe_wrong_int", MemberType.INT32, "properties", "wrong_int"));
    collection.addMember(member("probe_out_of_range_i8", MemberType.INT8, "properties", "too_large_i8"));

    IndexList indices = CollectionIndexPolicy.hubSlimIndices();
    for (String name : List.of(
        PROBE_I8,
        PROBE_I16,
        PROBE_I32,
        PROBE_I64,
        PROBE_F32,
        PROBE_F64,
        PROBE_LABEL,
        PROBE_BYTES,
        PROBE_TN,
        PROBE_SPATIAL,
        PROBE_TAG_MAP,
        PROBE_TAG_MAP_ARRAY,
        PROBE_TAG_LIST)) {
      indices.add(new Index(indexName(name), name));
    }
    collection.setIndices(indices);
    return collection;
  }

  private static NakshaCollection invalidBooleanIndexCollection(String collectionId) {
    NakshaCollection collection = new NakshaCollection(collectionId).withXyzMembers();
    collection.addMember(member(PROBE_BOOL, MemberType.BOOLEAN, "properties", "bool_value"));
    IndexList indices = CollectionIndexPolicy.hubSlimIndices();
    indices.add(new Index("idx_probe_bool", PROBE_BOOL));
    collection.setIndices(indices);
    return collection;
  }

  private static NakshaCollection missingMemberIndexCollection(String collectionId) {
    NakshaCollection collection = new NakshaCollection(collectionId).withXyzMembers();
    IndexList indices = CollectionIndexPolicy.hubSlimIndices();
    indices.add(new Index("idx_missing_member", "member_does_not_exist"));
    collection.setIndices(indices);
    return collection;
  }

  private static Member member(String name, MemberType type, Object... path) {
    return new Member(name, type, new JsonPath(path));
  }

  private static String featureCollectionJson(String matchingId, String standardGeoId) {
    return """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "id": "%s",
              "geometry": {"type": "Point", "coordinates": [0.0, 0.0]},
              "properties": {
                "bool_value": true,
                "i8_value": 7,
                "samples": [11, 1234],
                "i32_value": 123456,
                "i64_value": 42,
                "f32_value": 1.25,
                "f64_value": 2.5,
                "details": {"label": "alpha_probe"},
                "custom_geometry": {"type": "Point", "coordinates": [20.0, 20.0]},
                "custom_tag_map": {"region": "eu", "rank": 3},
                "custom_tag_array": ["region=eu", "rank:=3"],
                "custom_tag_list": ["red", "blue"],
                "explicit_null": null,
                "wrong_int": "not-a-number",
                "too_large_i8": 1000
              }
            },
            {
              "type": "Feature",
              "id": "%s",
              "geometry": {"type": "Point", "coordinates": [20.0, 20.0]},
              "properties": {
                "bool_value": false,
                "i8_value": 2,
                "samples": [5, 6],
                "i32_value": 7,
                "i64_value": 7,
                "f32_value": 8.5,
                "f64_value": 9.5,
                "details": {"label": "beta_probe"},
                "custom_geometry": {"type": "Point", "coordinates": [-20.0, -20.0]},
                "custom_tag_map": {"region": "us"},
                "custom_tag_array": ["region=us"],
                "custom_tag_list": ["green"]
              }
            }
          ]
        }
        """.formatted(matchingId, standardGeoId);
  }

  private static void verifyHttpReadback(String spaceId, String featureId) throws Exception {
    HttpResponse<String> response = CLIENT.get(
        "hub/spaces/" + spaceId + "/features?id=" + featureId,
        UUID.randomUUID().toString());
    assertEquals(200, response.statusCode(), response.body());
    XyzFeatureCollection features = TestUtil.parseJson(response.body(), XyzFeatureCollection.class);
    assertEquals(1, features.getFeatures().size());
    NakshaFeature feature = features.getFeatures().get(0);
    assertEquals(featureId, feature.getId());
    assertEquals("alpha_probe", feature.getProperties().getPath("details", "label"));
    assertEquals(1234L, ((Number) feature.getProperties().getPath("samples", 1)).longValue());
    assertNotNull(feature.getProperties().getPath("custom_geometry"));
    assertNotNull(feature.getProperties().getPath("custom_tag_map"));
    assertNull(feature.getProperties().getPath("custom_tag_array"));
    assertNotNull(feature.getProperties().getPath("custom_tag_list"));
  }

  private static DefaultStorageHandler realHandler(NakshaApp app, ProbeResources resources) {
    DefaultStorageHandlerProperties properties = new DefaultStorageHandlerProperties();
    properties.setStorageId(databaseId);
    properties.setAutoCreateCollection(true);
    properties.setAutoDeleteCollection(false);
    properties.setCollection(resources.collection);
    EventHandlerConfig config = new EventHandlerConfig();
    config.setId(resources.handlerId);
    config.setClassName(DefaultStorageHandler.class.getName());
    config.setActive(true);
    config.setProperties(properties);
    Space space = new Space();
    space.setId(resources.spaceId);
    space.addHandler(resources.handlerId);
    space.setProperties(new SpaceProperties());
    NakshaContext.newInstance("custom-member-probe").withSu(true).attachToCurrentThread();
    return new DefaultStorageHandler(config, app.getHub(), space);
  }

  private static void verifyWorkingMemberQueries(DefaultStorageHandler handler, String matchingFeatureId) {
    assertSuccessfulIds(handler, new Equals(PROBE_LABEL, "alpha_probe"), matchingFeatureId);
    assertSuccessfulIds(handler, new StartsWith(PROBE_LABEL, "alpha_"), matchingFeatureId);
    assertSuccessfulIds(handler, new Gte(PROBE_I64, 40L), matchingFeatureId);
    assertSuccessfulIds(handler, new Lt(PROBE_F64, 3.0), matchingFeatureId);
    assertSuccessfulIds(handler, new TagListContainsAllOf(PROBE_TAG_LIST, "red", "blue"), matchingFeatureId);
    assertSuccessfulIds(handler, new TagListContainsAnyOf(PROBE_TAG_LIST, "blue", "yellow"), matchingFeatureId);
  }

  private static void characterizeKnownQueryDefects(
      DefaultStorageHandler handler,
      String customSpatialFeatureId,
      String standardSpatialFeatureId) {
    Response tagKey = executeMemberRead(handler, new TagMapHasKey(PROBE_TAG_MAP, "region"));
    assertInstanceOf(ErrorResponse.class, tagKey, "Raw JSONB '?' must currently be observed as a JDBC failure");

    Response tagValue = executeMemberRead(handler, new TagEquals(PROBE_TAG_MAP, "rank", 3));
    assertInstanceOf(ErrorResponse.class, tagValue, "PgType.ofValue is currently unfinished");

    Response spatial = executeMemberRead(handler, new Intersects(PROBE_SPATIAL, new SpPoint(20.0, 20.0)));
    SuccessResponse spatialSuccess = assertInstanceOf(SuccessResponse.class, spatial);
    Set<String> ids = featureIds(spatialSuccess);
    assertFalse(ids.contains(customSpatialFeatureId), "The current builder must expose that it ignored the custom member");
    assertEquals(Set.of(standardSpatialFeatureId), ids, "The current builder unexpectedly stopped using standard geo");
  }

  private static void assertSuccessfulIds(DefaultStorageHandler handler, Op operation, String... expectedIds) {
    Response response = executeMemberRead(handler, operation);
    SuccessResponse success = assertInstanceOf(SuccessResponse.class, response, response.toString());
    assertEquals(Set.of(expectedIds), featureIds(success), "Unexpected result for " + operation);
  }

  private static Set<String> featureIds(SuccessResponse response) {
    return response.getFeatures().stream()
        .filter(java.util.Objects::nonNull)
        .map(NakshaFeature::getId)
        .collect(Collectors.toSet());
  }

  private static Response executeMemberRead(DefaultStorageHandler handler, Op operation) {
    ReadFeatures read = new ReadFeatures();
    read.setQueryMembers(operation);
    IEvent event = mock(IEvent.class);
    when(event.getRequest()).thenReturn(read);
    return handler.processEvent(event);
  }

  private static void assertRejectedAutoCreate(ProbeResources resources) throws Exception {
    String body = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","id":"negative_probe","geometry":null,"properties":{}}
        ]}
        """;
    HttpResponse<String> response = CLIENT.post(
        "hub/spaces/" + resources.spaceId + "/features",
        body,
        UUID.randomUUID().toString());
    assertTrue(response.statusCode() >= 400, "Expected collection creation rejection, got: " + response.body());
  }

  private static void verifyPhysicalColumns(Connection connection, String collectionId) throws SQLException {
    Map<String, String> expected = new LinkedHashMap<>();
    expected.put(PROBE_BOOL, "boolean");
    expected.put(PROBE_I8, "smallint");
    expected.put(PROBE_I16, "smallint");
    expected.put(PROBE_I32, "integer");
    expected.put(PROBE_I64, "bigint");
    expected.put(PROBE_F32, "real");
    expected.put(PROBE_F64, "double precision");
    expected.put(PROBE_LABEL, "text");
    expected.put(PROBE_BYTES, "text");
    expected.put(PROBE_TN, "text");
    expected.put(PROBE_SPATIAL, "bytea");
    expected.put(PROBE_TAG_MAP, "jsonb");
    expected.put(PROBE_TAG_MAP_ARRAY, "jsonb");
    expected.put(PROBE_TAG_LIST, "ARRAY");
    Map<String, String> actual = new HashMap<>();
    try (PreparedStatement statement = connection.prepareStatement("""
        SELECT column_name, data_type
        FROM information_schema.columns
        WHERE table_schema = ? AND table_name = ?
        """)) {
      statement.setString(1, SCHEMA);
      statement.setString(2, collectionId);
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) actual.put(rows.getString(1), rows.getString(2));
      }
    }
    expected.forEach((column, type) -> assertEquals(type, actual.get(column), "Unexpected type for " + column));
  }

  private static void verifyPhysicalIndices(Connection connection, String collectionId) throws SQLException {
    Map<String, String> indexDefinitions = new HashMap<>();
    Set<String> historyTables = new HashSet<>();
    try (PreparedStatement statement = connection.prepareStatement("""
        SELECT tablename, indexname, indexdef
        FROM pg_indexes
        WHERE schemaname = ? AND (tablename = ? OR tablename LIKE ?)
        """)) {
      statement.setString(1, SCHEMA);
      statement.setString(2, collectionId);
      statement.setString(3, collectionId + "$hst$%");
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          String table = rows.getString(1);
          String name = rows.getString(2);
          indexDefinitions.put(name, rows.getString(3));
          if (!table.equals(collectionId)) historyTables.add(table);
        }
      }
    }
    assertFalse(historyTables.isEmpty(), "Expected retained history partitions");
    List<String> requestedIndices = new ArrayList<>(List.of("tags", "geo"));
    requestedIndices.addAll(List.of(
        PROBE_I8,
        PROBE_I16,
        PROBE_I32,
        PROBE_I64,
        PROBE_F32,
        PROBE_F64,
        PROBE_LABEL,
        PROBE_BYTES,
        PROBE_TN,
        PROBE_SPATIAL,
        PROBE_TAG_MAP,
        PROBE_TAG_MAP_ARRAY,
        PROBE_TAG_LIST).stream().map(CustomMemberIndexProbeIT::indexName).toList());
    for (String index : requestedIndices) {
      assertTrue(indexDefinitions.containsKey(collectionId + "$ci_" + index), "Missing HEAD index " + index);
      for (String historyTable : historyTables) {
        assertTrue(indexDefinitions.containsKey(historyTable + "$ci_" + index), "Missing HISTORY index " + index);
      }
    }
    assertFalse(indexDefinitions.containsKey(collectionId + "$ci_next_version"));
    for (String historyTable : historyTables) {
      assertTrue(indexDefinitions.containsKey(historyTable + "$ci_next_version"));
    }
    assertDefinitionContains(indexDefinitions, collectionId, PROBE_LABEL, "USING btree", "text_pattern_ops");
    assertDefinitionContains(indexDefinitions, collectionId, PROBE_SPATIAL, "USING gist", "naksha_2d");
    assertDefinitionContains(indexDefinitions, collectionId, PROBE_TAG_MAP, "USING gin");
    assertDefinitionContains(indexDefinitions, collectionId, PROBE_TAG_LIST, "USING gin");
    assertIndexOpClass(connection, collectionId, PROBE_LABEL, "text_pattern_ops");
    assertIndexOpClass(connection, collectionId, PROBE_TAG_MAP, "jsonb_ops");
    assertIndexOpClass(connection, collectionId, PROBE_TAG_LIST, "array_ops");
  }

  private static void assertDefinitionContains(
      Map<String, String> definitions,
      String collectionId,
      String member,
      String... fragments) {
    String definition = definitions.get(collectionId + "$ci_" + indexName(member));
    assertNotNull(definition);
    for (String fragment : fragments) {
      assertTrue(definition.contains(fragment), "Expected '" + fragment + "' in: " + definition);
    }
  }

  private static void assertIndexOpClass(
      Connection connection,
      String collectionId,
      String member,
      String expectedOpClass) throws SQLException {
    String physicalIndexName = collectionId + "$ci_" + indexName(member);
    try (PreparedStatement statement = connection.prepareStatement("""
        SELECT opc.opcname
        FROM pg_class idx
        JOIN pg_namespace ns ON ns.oid = idx.relnamespace
        JOIN pg_index ind ON ind.indexrelid = idx.oid
        JOIN LATERAL unnest(ind.indclass) WITH ORDINALITY AS classes(opclass_oid, position) ON true
        JOIN pg_opclass opc ON opc.oid = classes.opclass_oid
        WHERE ns.nspname = ? AND idx.relname = ? AND classes.position = 1
        """)) {
      statement.setString(1, SCHEMA);
      statement.setString(2, physicalIndexName);
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next(), "No opclass found for " + physicalIndexName);
        assertEquals(expectedOpClass, result.getString(1), "Unexpected opclass for " + physicalIndexName);
      }
    }
  }

  private static void verifyRawMaterializedValues(Connection connection, String collectionId) throws SQLException {
    String sql = "SELECT * FROM " + qualified(collectionId) + " WHERE " + PROBE_LABEL + " = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, "alpha_probe");
      try (ResultSet row = statement.executeQuery()) {
        assertTrue(row.next());
        assertTrue(row.getBoolean(PROBE_BOOL));
        assertEquals(7, row.getShort(PROBE_I8));
        assertEquals(1234, row.getShort(PROBE_I16));
        assertEquals(123456, row.getInt(PROBE_I32));
        assertEquals(42L, row.getLong(PROBE_I64));
        assertEquals(1.25f, row.getFloat(PROBE_F32));
        assertEquals(2.5d, row.getDouble(PROBE_F64));
        assertEquals("alpha_probe", row.getString(PROBE_LABEL));
        assertNull(row.getObject(PROBE_BYTES));
        assertNull(row.getObject(PROBE_TN));
        assertNotNull(row.getBytes(PROBE_SPATIAL));
        assertTrue(row.getString(PROBE_TAG_MAP).contains("region"));
        assertNull(row.getObject(PROBE_TAG_MAP_ARRAY));
        Array tagList = row.getArray(PROBE_TAG_LIST);
        assertNotNull(tagList);
        assertArrayEquals(new Object[] {"red", "blue"}, (Object[]) tagList.getArray());
        assertNull(row.getObject("probe_missing"));
        assertNull(row.getObject("probe_explicit_null"));
        assertNull(row.getObject("probe_wrong_int"));
        assertNull(row.getObject("probe_out_of_range_i8"));
        assertFalse(row.next());
      }
    }
  }

  private static void verifyExactIndexPlans(Connection connection, String collectionId) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("SET enable_seqscan = off");
      statement.execute("ANALYZE " + qualified(collectionId));
    }
    assertPlanUses(connection, collectionId, PROBE_LABEL + " = 'alpha_probe'", indexName(PROBE_LABEL));
    assertPlanUses(connection, collectionId, "starts_with(" + PROBE_LABEL + ", 'alpha_')", indexName(PROBE_LABEL));
    assertPlanUses(connection, collectionId, PROBE_I64 + " >= 40", indexName(PROBE_I64));
    assertPlanUses(connection, collectionId, PROBE_F64 + " < 3.0", indexName(PROBE_F64));
    assertPlanUses(
        connection,
        collectionId,
        PROBE_TAG_LIST + " @> ARRAY['red']::text[]",
        indexName(PROBE_TAG_LIST));
    assertPlanUses(
        connection,
        collectionId,
        "ST_Intersects(naksha_2d(" + PROBE_SPATIAL + "), ST_SetSRID(ST_Point(20, 20), 4326))",
        indexName(PROBE_SPATIAL));
  }

  private static void assertPlanUses(Connection connection, String collectionId, String predicate, String index)
      throws SQLException {
    String expectedIndex = collectionId + "$ci_" + index;
    String sql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM "
        + qualified(collectionId)
        + " WHERE "
        + predicate;
    try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
      assertTrue(result.next());
      String plan = result.getString(1);
      assertTrue(plan.contains(expectedIndex), "Expected index " + expectedIndex + " in plan: " + plan);
    }
  }

  private static void assertTableAbsent(Connection connection, String tableName) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
        SELECT EXISTS (
          SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?
        )
        """)) {
      statement.setString(1, SCHEMA);
      statement.setString(2, tableName);
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next());
        assertFalse(result.getBoolean(1), "Failed creation left table " + tableName);
      }
    }
  }

  private static void printInspectionSummary(
      Connection connection,
      String runId,
      ProbeResources success,
      ProbeResources booleanFailure,
      ProbeResources missingFailure) throws SQLException {
    String database;
    try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT current_database()")) {
      assertTrue(result.next());
      database = result.getString(1);
    }
    String summary = """

        ===== RETAINED CUSTOM MEMBER PROBE =====
        runId: %s
        database: %s
        schema: %s
        storageId: %s
        spaceId: %s
        handlerId: %s
        collectionId: %s
        expected rejected collections: %s, %s
        Data is intentionally retained. Inspect it before another app-service test resets the schema.
        ========================================
        """.formatted(
        runId,
        database,
        SCHEMA,
        databaseId,
        success.spaceId,
        success.handlerId,
        success.collection.getId(),
        booleanFailure.collection.getId(),
        missingFailure.collection.getId());
    System.out.println(summary);
    log.info(summary);
  }

  private static String indexName(String memberName) {
    return "idx_" + memberName;
  }

  private static String qualified(String table) {
    return quoteIdentifier(SCHEMA) + "." + quoteIdentifier(table);
  }

  private static String quoteIdentifier(String identifier) {
    return '"' + identifier.replace("\"", "\"\"") + '"';
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    assertNotNull(value, "Missing " + name);
    assertFalse(value.isBlank(), "Blank " + name);
    return value;
  }

  private record ProbeResources(String handlerId, String spaceId, NakshaCollection collection) {}
}
