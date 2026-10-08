package com.here.naksha.app.storage;

import static com.here.naksha.lib.core.models.naksha.CustomIndexMappings.getCustomIndexMapping;
import static com.here.naksha.lib.core.models.naksha.CustomIndexMappings.setCustomIndexMapping;
import static org.junit.jupiter.api.Assertions.*;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaAppInjection;
import com.here.naksha.app.service.NakshaApp;
import com.here.naksha.lib.core.models.naksha.CustomIndexMapping;
import com.here.naksha.lib.core.models.naksha.CustomIndexMappingList;
import com.here.naksha.lib.core.util.CollectionIndexPolicy;
import com.here.naksha.lib.core.util.CustomIndexMappingCompiler;
import com.here.naksha.lib.core.util.PropertyMemberQueryTranslator;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import naksha.base.NakshaError;
import naksha.base.Platform;
import naksha.geo.PointCoord;
import naksha.geo.SpPoint;
import naksha.model.IReadSession;
import naksha.model.IWriteSession;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.TagList;
import naksha.model.TagMap;
import naksha.model.objects.*;
import naksha.model.request.*;
import naksha.model.request.ops.*;
import naksha.model.request.query.*;
import naksha.model.util.RequestHelper;
import naksha.psql.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Native storage checks using the existing app-service test database and lifecycle. */
class CustomIndexMappingStorageTest extends ApiTest {
  private PsqlStorage storage;
  private final NakshaCatalog catalog = new NakshaCatalog("custom_index_native");
  private final List<MemberType> types = List.of(MemberType.STRING, MemberType.STRING, MemberType.INT16, MemberType.INT32,
      MemberType.INT64, MemberType.FLOAT32, MemberType.FLOAT64, MemberType.BYTE_ARRAY,
      MemberType.SPATIAL, MemberType.TAG_LIST, MemberType.TAG_MAP);

  @BeforeEach void prepareCatalog(@NakshaAppInjection NakshaApp app) {
    NakshaContext.newInstance("custom-index-native-tests", "test", null, true).attachToCurrentThread();
    storage = assertInstanceOf(PsqlStorage.class, app.getHub().getStorageById(databaseId));
    executeWrite(new WriteRequest().add(new Write().upsertMap(catalog, false)));
  }

  private SuccessResponse executeWrite(WriteRequest request) {
    try (IWriteSession writer = storage.newWriteSession(null)) {
      Response response = writer.execute(request);
      if (response instanceof ErrorResponse) fail(((ErrorResponse) response).getError().toString());
      writer.commit();
      return assertInstanceOf(SuccessResponse.class, response);
    }
  }

  private SuccessResponse executeRead(ReadRequest request) {
    try (IReadSession reader = storage.newReadSession(null)) {
      Response response = reader.execute(request);
      if (response instanceof ErrorResponse) fail(((ErrorResponse) response).getError().toString());
      return assertInstanceOf(SuccessResponse.class, response);
    }
  }

  private NakshaCollection mapped(String id, CustomIndexMapping... mappings) {
    NakshaCollection definition = new NakshaCollection(id, catalog.getId());
    setCustomIndexMapping(definition, new CustomIndexMappingList(mappings));
    return definition;
  }

  private NakshaCollection definition(String id) {
    CustomIndexMapping[] mappings = new CustomIndexMapping[types.size()];
    for (int i = 0; i < types.size(); i++) mappings[i] = new CustomIndexMapping(new JsonPath("properties", "v" + i), types.get(i));
    NakshaCollection definition = mapped(id, mappings);
    definition.setIndices(new IndexList());
    return definition;
  }

  private SuccessResponse createMapped(NakshaCollection config) {
    return executeWrite(RequestHelper.createWriteCollectionsRequest(
        CollectionIndexPolicy.toNativeCollection(config, config.getId(), catalog.getId())));
  }

  private NakshaCollection stored(String id) {
    return Platform.javaProxy(singleFeature(executeRead(new ReadCollections().withMapId(catalog.getId()).addCollectionId(id))), NakshaCollection.class);
  }

  private NakshaFeature singleFeature(SuccessResponse response) {
    assertEquals(1, response.getFeatures().size());
    return Objects.requireNonNull(response.getFeatures().get(0));
  }

  private Member member(NakshaCollection schema, int index) {
    Member member = CustomIndexMappingCompiler.memberForPath(schema, new JsonPath("properties", "v" + index));
    assertNotNull(member);
    return member;
  }

  private ReadFeatures read(String id) {
    return new ReadFeatures().withCatalogId(catalog.getId()).withCollectionId(id);
  }

  private Set<String> ids(SuccessResponse response) {
    return response.getFeatures().stream().map(NakshaFeature::getId).collect(Collectors.toSet());
  }

  private Set<String> ids(String id, Op op) {
    ReadFeatures request = read(id);
    request.setQueryMembers(op);
    return ids(executeRead(request));
  }

  private Map<String, NakshaFeature> byId(SuccessResponse response) {
    return response.getFeatures().stream().collect(Collectors.toMap(NakshaFeature::getId, feature -> feature));
  }

  private NakshaFeature feature(String id, String property, Object value) {
    NakshaFeature feature = new NakshaFeature(id);
    feature.getProperties().put(property, value);
    return feature;
  }

  private TagMap tags(String key, Object value) {
    TagMap tags = new TagMap();
    tags.put(key, value);
    return tags;
  }

  @Test void booleanMembersWithoutIndicesRoundtripAndTranslateLikeLegacyFiltering() {
    NakshaCollection config = mapped("mapped_boolean", new CustomIndexMapping(new JsonPath("properties", "active"), MemberType.BOOLEAN));
    config.setIndices(new IndexList());
    createMapped(config);
    NakshaCollection schema = stored(config.getId());
    Member member = CustomIndexMappingCompiler.memberForPath(schema, new JsonPath("properties", "active"));
    assertNotNull(member);
    assertEquals(MemberType.BOOLEAN, member.getDataType());
    assertTrue(schema.getIndices().isEmpty());
    WriteRequest writes = new WriteRequest();
    writes.add(new Write().createFeature(schema, feature("active", "active", true)));
    writes.add(new Write().createFeature(schema, feature("inactive", "active", false)));
    writes.add(new Write().createFeature(schema, feature("null", "active", null)));
    writes.add(new Write().createFeature(schema, new NakshaFeature("missing")));
    executeWrite(writes);
    Map<String, NakshaFeature> readback = byId(executeRead(read(schema.getId())));
    assertEquals(true, readback.get("active").getProperties().get("active"));
    assertEquals(false, readback.get("inactive").getProperties().get("active"));
    assertNull(readback.get("null").getProperties().get("active"));
    assertNull(readback.get("missing").getProperties().get("active"));
    for (AnyOp op : List.of(AnyOp.IS_TRUE, AnyOp.IS_FALSE)) {
      String expected = op.equals(AnyOp.IS_TRUE) ? "active" : "inactive";
      ReadFeatures legacy = read(schema.getId()).withPropertyQuery(new PQuery(new Property("properties", "active"), op));
      ReadFeatures translated = PropertyMemberQueryTranslator.adapt(legacy, schema, config);
      assertNotNull(translated);
      if (op.equals(AnyOp.IS_TRUE)) assertInstanceOf(IsTrue.class, translated.getQueryMembers());
      else assertInstanceOf(IsFalse.class, translated.getQueryMembers());
      assertEquals(Set.of(expected), ids(executeRead(legacy)));
      assertEquals(Set.of(expected), ids(executeRead(translated)));
    }
  }

  @Test void literalStringKeysRoundtripAndCanBeQueried() {
    List<JsonPath> paths = List.of(new JsonPath("properties", ""), new JsonPath("properties", "*"));
    NakshaCollection config = mapped("literal_keys", paths.stream().map(path -> new CustomIndexMapping(path, MemberType.STRING)).toArray(CustomIndexMapping[]::new));
    createMapped(config);
    NakshaCollection schema = stored(config.getId());
    NakshaFeature feature = feature("one", "", "empty-key");
    feature.getProperties().put("*", "literal-star");
    executeWrite(new WriteRequest().add(new Write().createFeature(schema, feature)));
    NakshaFeature readback = singleFeature(executeRead(read(config.getId())));
    for (JsonPath path : paths) {
      String key = (String) path.get(path.size() - 1);
      assertEquals(feature.getProperties().get(key), readback.getProperties().get(key));
      Member member = CustomIndexMappingCompiler.memberForPath(schema, path);
      assertNotNull(member);
      assertEquals(Set.of("one"), ids(config.getId(), new Equals(member, feature.getProperties().get(key))));
    }
  }

  @Test void createsAllSchemasAndExercisesTenTypesSupportedByUnpatchedStorage() {
    NakshaCollection source = definition("custom_all_types");
    createMapped(source);
    NakshaCollection schema = stored(source.getId());
    assertEquals(XyzMembers.ALL.size() + types.size(), schema.getMembers().size());
    assertEquals(types.size(), schema.getIndices().size());
    assertFalse(schema.hasRaw("customIndexMapping"));
    assertNull(source.getMembers());
    assertEquals(types.size(), getCustomIndexMapping(source).size());
    SpPoint point = new SpPoint(new PointCoord(13.0, 52.0));
    List<Object> values = List.of("a_%literal", (byte) 12, (short) 32000, 2000000000, 9007199254740991L, 0.5f, 1.25,
        new byte[]{0, 1, -1}, point, new TagList("red", "blue"), tags("color", "red"));
    NakshaFeature feature = new NakshaFeature("one");
    feature.setGeometry(point);
    feature.getProperties().getXyz().put("tags", new TagList("xyz-preserved"));
    for (int i = 0; i < values.size(); i++) if (i != 1) feature.getProperties().put("v" + i, values.get(i));
    executeWrite(new WriteRequest().add(new Write().createFeature(schema, feature)));
    NakshaFeature read = singleFeature(executeRead(read(schema.getId())));
    for (int i = 0; i <= 6; i++) {
      if (i == 1) continue; // v1 is not written.
      assertEquals(Set.of("one"), ids(schema.getId(), new Equals(member(schema, i), values.get(i))), "type " + types.get(i));
      if (i == 0) assertEquals(values.get(i), read.getProperties().get("v" + i));
      else assertEquals(((Number) values.get(i)).doubleValue(), ((Number) read.getProperties().get("v" + i)).doubleValue());
    }
    assertEquals(Set.of("one"), ids(schema.getId(), new StartsWith(member(schema, 0), "a_%")));
    assertEquals(Set.of("one"), ids(schema.getId(), new Equals(member(schema, 7), values.get(7))));
    assertEquals(Set.of("one"), ids(schema.getId(), new Intersects(member(schema, 8), point)));
    assertEquals(Set.of("one"), ids(schema.getId(), new TagListContains(member(schema, 9), "red")));
    assertEquals(Set.of("one"), ids(schema.getId(), new TagEquals(member(schema, 10), "color", "red")));
    assertNotNull(read.getGeometry());
    assertTrue(read.getProperties().getXyz().getTags().contains("xyz-preserved"));
    assertArrayEquals((byte[]) values.get(7), (byte[]) read.getProperties().get("v7"));
    assertNotNull(read.getProperties().get("v8"));
    assertNotNull(read.getProperties().get("v9"));
    assertNotNull(read.getProperties().get("v10"));
    System.out.println("CUSTOM_BINARY_JSON " + Platform.toJSON(read.getProperties().get("v7")));
    try (PgConnection connection = storage.adminConnection();
         PgCursor rows = connection.execute("SELECT indexname FROM pg_indexes WHERE schemaname = $1 AND tablename = $2",
             new Object[]{catalog.getId(), schema.getId()})) {
      Set<String> names = new HashSet<>();
      while (rows.next()) names.add(rows.column("indexname").toString());
      for (Index index : schema.getIndices()) {
        assertTrue(names.contains(schema.getId() + "$ci_" + index.getName()), "missing index " + index.getName() + ": " + names);
      }
    }
  }

  @Test void childOnlyConversionAgreesWithReadbackAndPropertyFiltering() {
    // Documents the native behaviour: a value that does not fit the member type is not stored.
    Member rating = new Member("rating", MemberType.INT32, new JsonPath("properties", "address", "rating"));
    NakshaCollection definition = new NakshaCollection("child_only_behavior", catalog.getId());
    definition.setMembers(new MemberList(rating));
    definition.setIndices(new IndexList(new Index("rating_idx", rating.getName())));
    executeWrite(new WriteRequest().add(new Write().createCollection(definition)));
    NakshaCollection schema = stored(definition.getId());
    TagMap address = tags("city", "Berlin");
    address.put("rating", 4.5);
    executeWrite(new WriteRequest().add(new Write().createFeature(schema, feature("one", "address", address))));
    assertEquals(Set.of("one"), ids(schema.getId(), new IsNull(rating)));
    NakshaFeature readback = singleFeature(executeRead(read(schema.getId())));
    TagMap decoded = Platform.javaProxy(readback.getProperties().get("address"), TagMap.class);
    assertEquals("Berlin", decoded.get("city"));
    assertNull(decoded.get("rating"));
    ReadFeatures legacy = read(schema.getId()).withPropertyQuery(new PQuery(new Property("properties", "address", "rating"), DoubleOp.EQ, 4.5));
    assertTrue(executeRead(legacy).getFeatures().isEmpty());
    NakshaCollection config = mapped(schema.getId(), new CustomIndexMapping(rating.getPath(), rating.getDataType()));
    ReadFeatures translated = PropertyMemberQueryTranslator.adapt(legacy, schema, config);
    assertNotNull(translated);
    assertTrue(executeRead(translated).getFeatures().isEmpty());
  }

  @Test void translatedQueriesAgreeWithLegacyResultsAndKeepFallback() {
    NakshaCollection definition = mapped("custom_translate", new CustomIndexMapping(new JsonPath("properties", "score"), MemberType.INT32));
    definition.setIndices(new IndexList());
    createMapped(definition);
    NakshaCollection schema = stored(definition.getId());
    WriteRequest writes = new WriteRequest();
    List<Object> values = Arrays.asList(50, 51, -5, null, "bad", 2147483648L);
    for (int i = 0; i < values.size(); i++) writes.add(new Write().createFeature(schema, feature("f" + i, "score", values.get(i))));
    writes.add(new Write().createFeature(schema, new NakshaFeature("missing")));
    executeWrite(writes);
    Map<String, NakshaFeature> readback = byId(executeRead(read(schema.getId())));
    for (String id : List.of("f3", "f4", "f5", "missing")) assertNull(readback.get(id).getProperties().get("score"), id);
    for (AnyOp op : List.of(DoubleOp.EQ, DoubleOp.GT, DoubleOp.GTE, DoubleOp.LT, DoubleOp.LTE)) {
      for (double bound : List.of(50.0, 50.5, -1e100, 1e100)) {
        ReadFeatures legacy = read(schema.getId()).withPropertyQuery(new PQuery(new Property("properties", "score"), op, bound));
        ReadFeatures translated = PropertyMemberQueryTranslator.adapt(legacy, schema, definition);
        assertNotNull(translated);
        assertEquals(ids(executeRead(legacy)), ids(executeRead(translated)), op + " " + bound);
      }
    }
    ReadFeatures unsupported = new ReadFeatures().withPropertyQuery(new PQuery(new Property("properties", "score"), AnyOp.IS_NULL));
    assertNull(PropertyMemberQueryTranslator.adapt(unsupported, schema, definition));
  }

  private List<Object> values(int i) {
    return List.of("value_" + i, (byte) (i % 128), (short) i, i, (long) i, i + 0.5f, i + 0.25,
        new byte[]{(byte) (i >> 8), (byte) i}, new SpPoint(new PointCoord(i / 1000.0, 10.0)),
        new TagList("tag_" + i), tags("key", "value_" + i));
  }

  @Test void selectiveRequestsUseTenGeneratedIndexesOnUnpatchedStorage() {
    NakshaCollection definition = definition("custom_index_plans");
    createMapped(definition);
    NakshaCollection schema = stored(definition.getId());
    for (int batch = 0; batch < 10; batch++) {
      WriteRequest request = new WriteRequest();
      for (int i = batch * 300; i < (batch + 1) * 300; i++) {
        NakshaFeature feature = new NakshaFeature("plan_" + i);
        List<Object> values = values(i);
        for (int p = 0; p < values.size(); p++) if (p != 1) feature.getProperties().put("v" + p, values.get(p));
        request.add(new Write().createFeature(schema, feature));
      }
      executeWrite(request);
    }
    try (PgConnection connection = storage.adminConnection()) {
      connection.execute("ANALYZE \"" + catalog.getId() + "\".\"" + schema.getId() + "\"", null).close();
    }
    List<Object> values = values(1234);
    try (PgSession session = (PgSession) storage.newReadSession(new SessionOptions())) {
      for (int i = 0; i < types.size(); i++) {
        if (i == 1) continue; // v1 is not written.
        Member member = member(schema, i);
        Op op;
        if (i == 8) op = new Intersects(member, (SpPoint) values.get(i));
        else if (i == 9) op = new TagListContains(member, "tag_1234");
        else if (i == 10) op = new TagEquals(member, "key", "value_1234");
        else op = new Equals(member, values.get(i));
        ReadFeatures request = read(schema.getId());
        request.setQueryMembers(op);
        PgQuery query = new PgQueryBuilder(session, request).build();
        try (PgConnection connection = storage.adminConnection()) {
          connection.execute("SET search_path=\"" + catalog.getId() + "\",\"naksha~admin\",public", null).close();
          String plan = session.explain(connection, false, query.getSql(), query.getArgTypes(), query.getArgValues());
          System.out.println("CUSTOM_INDEX_PLAN " + types.get(i) + " " + member.getName() + "\n" + query.getSql() + "\n" + plan);
          String indexName = schema.getIndices().stream()
              .filter(index -> index.getOn().size() == 1 && member.getName().equals(index.getOn().get(0)))
              .findFirst().orElseThrow().getName();
          assertTrue(plan.contains(schema.getId() + "$ci_" + indexName), "No generated index for " + types.get(i) + ":\n" + plan);
        }
      }
    }
  }

  @Test void historyRetainsCustomValuesAndIndexesAfterUpdates() {
    NakshaCollection definition = mapped("h".repeat(41), new CustomIndexMapping(new JsonPath("properties", "score"), MemberType.INT32));
    definition.setPartitions(2);
    definition.setShift(30); // Seven-digit history key plus distribution suffix reaches exactly 63 bytes.
    definition.setIndices(new IndexList());
    createMapped(definition);
    NakshaCollection schema = stored(definition.getId());
    NakshaFeature first = singleFeature(executeWrite(new WriteRequest().add(new Write().createFeature(schema, feature("historical", "score", 10)))));
    NakshaFeature changed = first.copy(true);
    changed.getProperties().put("score", 20);
    executeWrite(new WriteRequest().add(new Write().updateFeature(schema, changed, true)));
    ReadFeatures request = read(schema.getId());
    request.setQueryHistory(true);
    request.setVersions(2);
    request.setQueryMembers(new Gte(Objects.requireNonNull(CustomIndexMappingCompiler.memberForPath(schema, new JsonPath("properties", "score"))), 10));
    Set<Integer> scores = executeRead(request).getFeatures().stream()
        .map(feature -> ((Number) feature.getProperties().get("score")).intValue()).collect(Collectors.toSet());
    assertEquals(Set.of(10, 20), scores);
    ReadFeatures legacy = read(schema.getId()).withPropertyQuery(new PQuery(new Property("properties", "score"), DoubleOp.GTE, 10));
    legacy.setQueryHistory(true);
    legacy.setVersions(2);
    assertNull(PropertyMemberQueryTranslator.adapt(legacy, schema, definition));
    assertEquals(2, executeRead(legacy).getFeatures().size());
    try (PgConnection connection = storage.adminConnection();
         PgCursor rows = connection.execute("SELECT indexname FROM pg_indexes WHERE schemaname=$1 AND tablename LIKE $2",
             new Object[]{catalog.getId(), schema.getId() + "$hst%"})) {
      List<String> names = new ArrayList<>();
      while (rows.next()) names.add(rows.column("indexname").toString());
      assertTrue(names.stream().anyMatch(name -> name.contains("$ci_ci01")), names.toString());
      assertTrue(names.stream().allMatch(name -> name.getBytes(StandardCharsets.UTF_8).length <= 63));
      assertTrue(names.stream().anyMatch(name -> name.contains("$ci_ci01") && name.getBytes(StandardCharsets.UTF_8).length == 63), names.toString());
    }
  }

  @Test void floatsAndTagQueriesMatchDecodedLegacySemantics() {
    NakshaCollection definition = mapped("custom_numeric_edges",
        new CustomIndexMapping(new JsonPath("properties", "score"), MemberType.FLOAT64),
        new CustomIndexMapping(new JsonPath("properties", "labels"), MemberType.TAG_LIST));
    definition.setIndices(new IndexList());
    createMapped(definition);
    NakshaCollection schema = stored(definition.getId());
    WriteRequest writes = new WriteRequest();
    List<Double> values = List.of(Double.NaN, Double.NEGATIVE_INFINITY, -0.0, 0.5, Double.POSITIVE_INFINITY);
    for (int i = 0; i < values.size(); i++) {
      NakshaFeature feature = feature("edge" + i, "score", values.get(i));
      feature.getProperties().put("labels", new TagList("red", "blue", "red=value"));
      writes.add(new Write().createFeature(schema, feature));
    }
    executeWrite(writes);
    List<PQuery> expressions = new ArrayList<>();
    for (AnyOp op : List.of(DoubleOp.EQ, DoubleOp.LT, DoubleOp.LTE, DoubleOp.GT, DoubleOp.GTE)) {
      expressions.add(new PQuery(new Property("properties", "score"), op, 0.0));
    }
    expressions.add(new PQuery(new Property("properties", "labels"), AnyOp.CONTAINS, "red"));
    expressions.add(new PQuery(new Property("properties", "labels"), AnyOp.CONTAINS, "red=value"));
    for (PQuery expression : expressions) {
      ReadFeatures legacy = read(schema.getId()).withPropertyQuery(expression);
      ReadFeatures translated = PropertyMemberQueryTranslator.adapt(legacy, schema, definition);
      assertNotNull(translated);
      assertEquals(ids(executeRead(legacy)), ids(executeRead(translated)));
    }
  }

  private Response upsert(NakshaCollection definition) {
    try (IWriteSession writer = storage.newWriteSession(null)) {
      NakshaCollection nativeCollection = CollectionIndexPolicy.toNativeCollection(definition, definition.getId(), catalog.getId());
      Response response = writer.execute(new WriteRequest().add(new Write().upsertCollection(nativeCollection)));
      if (response instanceof SuccessResponse) writer.commit();
      else writer.rollback();
      return response;
    }
  }

  @Test void existingCollectionSchemaCannotBeChanged() {
    NakshaCollection config = mapped("custom_immutable", new CustomIndexMapping(new JsonPath("properties", "score"), MemberType.INT32));
    createMapped(config);
    assertInstanceOf(SuccessResponse.class, upsert(config));
    NakshaCollection changedType = mapped(config.getId(), new CustomIndexMapping(new JsonPath("properties", "score"), MemberType.STRING));
    NakshaCollection added = mapped(config.getId(), new CustomIndexMapping(new JsonPath("properties", "score"), MemberType.INT32),
        new CustomIndexMapping(new JsonPath("properties", "label"), MemberType.STRING));
    NakshaCollection movedPath = mapped(config.getId(), new CustomIndexMapping(new JsonPath("properties", "different"), MemberType.INT32));
    for (NakshaCollection changed : List.of(changedType, added, movedPath, new NakshaCollection(config.getId(), catalog.getId()))) {
      ErrorResponse response = assertInstanceOf(ErrorResponse.class, upsert(changed));
      assertEquals(NakshaError.CONFLICT, response.getError().getCode());
    }
    assertEquals(XyzMembers.ALL.size() + 1, stored(config.getId()).getMembers().size());
  }

  @Test void pathChangesAreComparedByTypedSegments() {
    JsonPath[][] changes = {
        {new JsonPath("properties", "items", 0), new JsonPath("properties", "items", "0")},
        {new JsonPath("properties", "a, b"), new JsonPath("properties", "a", "b")}};
    for (int i = 0; i < changes.length; i++) {
      String id = "custom_typed_path_" + i;
      NakshaCollection original = mapped(id, new CustomIndexMapping(changes[i][0], MemberType.STRING));
      NakshaCollection nativeCollection = CollectionIndexPolicy.toNativeCollection(original, id, catalog.getId());
      executeWrite(new WriteRequest().add(new Write().createCollection(nativeCollection)));
      NakshaCollection changed = mapped(id, new CustomIndexMapping(changes[i][1], MemberType.STRING));
      ErrorResponse response = assertInstanceOf(ErrorResponse.class, upsert(changed));
      assertEquals(NakshaError.CONFLICT, response.getError().getCode(), changes[i][0] + " -> " + changes[i][1]);
    }
  }
}
