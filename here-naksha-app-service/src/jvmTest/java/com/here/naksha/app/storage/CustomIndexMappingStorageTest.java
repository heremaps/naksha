package com.here.naksha.app.storage;

import static com.here.naksha.app.common.TestUtil.urlEncoded;
import static org.junit.jupiter.api.Assertions.*;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaAppInjection;
import com.here.naksha.app.service.NakshaApp;
import com.here.naksha.app.service.http.ops.PropertyQueryUtil;
import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import naksha.model.objects.CustomIndexMapping;
import naksha.model.objects.CustomIndexMappingList;
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
class  CustomIndexMappingStorageTest extends ApiTest {
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
    definition.setCustomIndexMapping(new CustomIndexMappingList(mappings));
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

  private Member member(NakshaCollection schema, String property) {
    Member member = CustomIndexMappingCompiler.memberForPath(schema, new JsonPath("properties", property));
    assertNotNull(member);
    return member;
  }

  private ReadFeatures read(String id) {
    return new ReadFeatures().withCatalogId(catalog.getId()).withCollectionId(id);
  }

  /** Asserts that the translated property query finds the features of the direct member query, returns their ids. */
  private Set<String> assertTranslatedLike(NakshaCollection schema, NakshaCollection config, IPropertyQuery expression, Op direct) {
    ReadFeatures translated = PropertyMemberQueryTranslator.adapt(read(schema.getId()).withPropertyQuery(expression), schema, config);
    assertNotNull(translated, expression.toString());
    Set<String> expected = ids(schema.getId(), direct);
    assertEquals(expected, ids(executeRead(translated)), expression.toString());
    return expected;
  }

  /** Asserts that the storage rejects the translated property query like the direct member query. */
  private void assertRejectedLike(NakshaCollection schema, NakshaCollection config, IPropertyQuery expression, Op direct) {
    ReadFeatures translated = PropertyMemberQueryTranslator.adapt(read(schema.getId()).withPropertyQuery(expression), schema, config);
    assertNotNull(translated, expression.toString());
    ReadFeatures request = read(schema.getId());
    request.setQueryMembers(direct);
    assertEquals(NakshaError.ILLEGAL_ARGUMENT, readError(request).getError().getCode(), expression.toString());
    assertEquals(NakshaError.ILLEGAL_ARGUMENT, readError(translated).getError().getCode(), expression.toString());
  }

  private ErrorResponse readError(ReadRequest request) {
    try (IReadSession reader = storage.newReadSession(null)) {
      return assertInstanceOf(ErrorResponse.class, reader.execute(request));
    }
  }

  /** Parses the query string with the property search parser of the REST API. */
  private IPropertyQuery rest(String queryString) {
    IPropertyQuery query = PropertyQueryUtil.propertyQueryFromParams(new QueryParameterList(queryString));
    assertNotNull(query, queryString);
    return query;
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

  @Test void booleanMembersWithoutIndicesRoundtripAndTranslateToNativeQueries() {
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
    Property active = new Property("properties", "active");
    assertEquals(Set.of("active"), assertTranslatedLike(schema, config, new PQuery(active, AnyOp.IS_TRUE), new IsTrue(member)));
    assertEquals(Set.of("inactive"), assertTranslatedLike(schema, config, new PQuery(active, AnyOp.IS_FALSE), new IsFalse(member)));
    assertEquals(Set.of("active"), assertTranslatedLike(schema, config, new PQuery(active, AnyOp.CONTAINS, true), new IsTrue(member)));
    assertEquals(Set.of("null", "missing"), assertTranslatedLike(schema, config, new PQuery(active, AnyOp.IS_NULL), new IsNull(member)));
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
    assertEquals(types.size(), source.getCustomIndexMapping().size());
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
    // The Double operand is passed unchanged, the storage rejects it for INT32 like a direct member query.
    assertRejectedLike(schema, config, legacy.getQuery().getProperties(), new Equals(rating, 4.5));
  }

  @Test void translatedQueriesMatchDirectMemberQueriesAndKeepFallback() {
    NakshaCollection definition = mapped("custom_translate",
        new CustomIndexMapping(new JsonPath("properties", "score"), MemberType.INT64),
        new CustomIndexMapping(new JsonPath("properties", "ratio"), MemberType.FLOAT64),
        new CustomIndexMapping(new JsonPath("properties", "label"), MemberType.STRING),
        new CustomIndexMapping(new JsonPath("properties", "labels"), MemberType.TAG_LIST));
    definition.setIndices(new IndexList());
    createMapped(definition);
    NakshaCollection schema = stored(definition.getId());
    List<Object> values = Arrays.asList(50, 51, -5, null, "bad", 2147483648L);
    List<NakshaFeature> features = new ArrayList<>();
    for (int i = 0; i < values.size(); i++) features.add(feature("f" + i, "score", values.get(i)));
    features.get(0).getProperties().put("ratio", 0.1);
    features.get(0).getProperties().put("label", "abc");
    features.get(0).getProperties().put("labels", new TagList("red", "[\"red\"]"));
    features.get(1).getProperties().put("ratio", 0.5);
    features.get(1).getProperties().put("label", "abd");
    features.get(1).getProperties().put("labels", new TagList("red"));
    features.add(new NakshaFeature("missing"));
    WriteRequest writes = new WriteRequest();
    for (NakshaFeature feature : features) writes.add(new Write().createFeature(schema, feature));
    executeWrite(writes);
    Map<String, NakshaFeature> readback = byId(executeRead(read(schema.getId())));
    for (String id : List.of("f3", "f4", "missing")) assertNull(readback.get(id).getProperties().get("score"), id);
    assertEquals(2147483648L, ((Number) readback.get("f5").getProperties().get("score")).longValue());

    Member score = member(schema, "score");
    Member ratio = member(schema, "ratio");
    Member label = member(schema, "label");
    Member labels = member(schema, "labels");
    Property scorePath = new Property("properties", "score");
    Property labelPath = new Property("properties", "label");
    Property labelsPath = new Property("properties", "labels");
    // Operands are passed unchanged, REST sends integers as Long and decimals as Double.
    assertEquals(Set.of("f2"), assertTranslatedLike(schema, definition, rest("p.score=lt=50"), new Lt(score, 50L)));
    assertEquals(Set.of("f1", "f5"), assertTranslatedLike(schema, definition, rest("p.score=gt=50"), new Gt(score, 50L)));
    assertEquals(Set.of("f0"), assertTranslatedLike(schema, definition, rest("p.score=50"), new Equals(score, 50L)));
    assertEquals(Set.of("f0", "f1", "f5"), assertTranslatedLike(schema, definition, new PQuery(scorePath, DoubleOp.GTE, 50L), new Gte(score, 50L)));
    assertEquals(Set.of("f1"), assertTranslatedLike(schema, definition, new PQuery(scorePath, DoubleOp.CONTAINS, 51L), new Equals(score, 51L)));
    assertEquals(Set.of("f5"), assertTranslatedLike(schema, definition, rest("p.score=gt=2147483647&p.score=lte=2147483648"),
        new And(new Gt(score, 2147483647L), new Lte(score, 2147483648L))));
    assertEquals(Set.of("f0"), assertTranslatedLike(schema, definition, rest("p.ratio=0.1"), new Equals(ratio, 0.1)));
    assertEquals(Set.of("f0"), assertTranslatedLike(schema, definition, new PQuery(labelPath, StringOp.CONTAINS, "abc"), new Equals(label, "abc")));
    assertEquals(Set.of("f0", "f1"), assertTranslatedLike(schema, definition,
        new PQuery(labelPath, StringOp.STARTS_WITH, "ab"), new StartsWith(label, "ab")));

    // A plain string is one tag, a JSON array requires all of its tags.
    String literalArray = "[\"[\\\"red\\\"]\"]";
    assertEquals(Set.of("f0", "f1"), assertTranslatedLike(schema, definition, rest("p.labels=cs=red"), new TagListContains(labels, "red")));
    assertEquals(Set.of("f0", "f1"), assertTranslatedLike(schema, definition,
        rest("p.labels=cs=" + urlEncoded("[\"red\"]")), new TagListContainsAllOf(labels, "red")));
    assertEquals(Set.of("f0"), assertTranslatedLike(schema, definition,
        rest("p.labels=cs=" + urlEncoded("[\"red\",\"[\\\"red\\\"]\"]")), new TagListContainsAllOf(labels, "red", "[\"red\"]")));
    // A one-element array searches a JSON-looking tag literally.
    assertEquals(Set.of("f0"), assertTranslatedLike(schema, definition,
        rest("p.labels=cs=" + urlEncoded(literalArray)), new TagListContainsAllOf(labels, "[\"red\"]")));
    // The storage matches every tag list by an empty array, but no missing one.
    assertEquals(Set.of("f0", "f1"), assertTranslatedLike(schema, definition,
        rest("p.labels=cs=" + urlEncoded("[]")), new TagListContainsAllOf(labels)));
    // Comma separated values remain OR, repeated parameters AND.
    assertEquals(Set.of("f0", "f1"), assertTranslatedLike(schema, definition,
        rest("p.labels=cs=" + urlEncoded("[\"red\"]") + "," + urlEncoded(literalArray)),
        new Or(new TagListContainsAllOf(labels, "red"), new TagListContainsAllOf(labels, "[\"red\"]"))));
    assertEquals(Set.of("f0"), assertTranslatedLike(schema, definition,
        rest("p.labels=cs=" + urlEncoded("[\"red\"]") + "&p.labels=cs=" + urlEncoded(literalArray)),
        new And(new TagListContainsAllOf(labels, "red"), new TagListContainsAllOf(labels, "[\"red\"]"))));
    assertNull(PropertyMemberQueryTranslator.adapt(
        read(schema.getId()).withPropertyQuery(rest("p.labels=cs=" + urlEncoded("[1]"))), schema, definition));

    // Null and missing values follow the SQL semantics, also when negated.
    Set<String> noScore = Set.of("f3", "f4", "missing");
    assertEquals(noScore, assertTranslatedLike(schema, definition, new PQuery(scorePath, AnyOp.IS_NULL), new IsNull(score)));
    assertEquals(Set.of("f0", "f1", "f2", "f5"), assertTranslatedLike(schema, definition,
        new PQuery(scorePath, AnyOp.EXISTS), new Not(new IsNull(score))));
    assertEquals(noScore, assertTranslatedLike(schema, definition,
        new PNot(new PQuery(scorePath, AnyOp.EXISTS)), new Not(new Not(new IsNull(score)))));
    assertEquals(Set.of("f1", "f2", "f5"), assertTranslatedLike(schema, definition,
        new PNot(new PQuery(scorePath, DoubleOp.EQ, 50L)), new Not(new Equals(score, 50L))));

    // Compound expressions keep their structure.
    assertEquals(Set.of("f0"), assertTranslatedLike(schema, definition, rest("p.score=50,51&p.label=abc"),
        new And(new Or(new Equals(score, 50L), new Equals(score, 51L)), new Equals(label, "abc"))));
    PAnd negated = new PAnd(new PQuery(scorePath, AnyOp.EXISTS), new PNot(new PQuery(labelsPath, StringOp.CONTAINS, literalArray)));
    assertEquals(Set.of("f1"), assertTranslatedLike(schema, definition, negated,
        new And(new Not(new IsNull(score)), new Not(new TagListContainsAllOf(labels, "[\"red\"]")))));

    // Without a native counterpart for every leaf, the complete expression stays on the legacy path.
    PQuery unmapped = new PQuery(new Property("properties", "unmapped"), StringOp.EQUALS, "x");
    ReadFeatures mixed = read(schema.getId()).withPropertyQuery(new POr(new PQuery(scorePath, DoubleOp.EQ, 50L), unmapped));
    assertNull(PropertyMemberQueryTranslator.adapt(mixed, schema, definition));
    assertEquals(Set.of("f0"), ids(executeRead(mixed)));
    ReadFeatures stringOnInteger = read(schema.getId()).withPropertyQuery(new PQuery(scorePath, StringOp.EQUALS, "bad"));
    assertNull(PropertyMemberQueryTranslator.adapt(stringOnInteger, schema, definition));
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

  @Test void floatEdgesAndTagQueriesMatchDirectMemberQueries() {
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
    Member score = member(schema, "score");
    Member labels = member(schema, "labels");
    Property scorePath = new Property("properties", "score");
    Property labelsPath = new Property("properties", "labels");
    // The storage decides how NaN, infinities and -0.0 compare.
    assertTranslatedLike(schema, definition, new PQuery(scorePath, DoubleOp.EQ, 0.0), new Equals(score, 0.0));
    assertTranslatedLike(schema, definition, new PQuery(scorePath, DoubleOp.LT, 0.0), new Lt(score, 0.0));
    assertTranslatedLike(schema, definition, new PQuery(scorePath, DoubleOp.LTE, 0.0), new Lte(score, 0.0));
    assertTranslatedLike(schema, definition, new PQuery(scorePath, DoubleOp.GT, 0.0), new Gt(score, 0.0));
    assertTranslatedLike(schema, definition, new PQuery(scorePath, DoubleOp.GTE, 0.0), new Gte(score, 0.0));
    // REST sends "p.score=0" as Long, which the storage rejects for FLOAT64.
    assertRejectedLike(schema, definition, rest("p.score=0"), new Equals(score, 0L));
    Set<String> all = Set.of("edge0", "edge1", "edge2", "edge3", "edge4");
    assertEquals(all, assertTranslatedLike(schema, definition, new PQuery(labelsPath, AnyOp.CONTAINS, "red"), new TagListContains(labels, "red")));
    assertEquals(all, assertTranslatedLike(schema, definition,
        new PQuery(labelsPath, AnyOp.CONTAINS, "red=value"), new TagListContains(labels, "red=value")));
  }

  @Test void numericOperandsReachTheStorageUnchanged() {
    NakshaCollection definition = mapped("custom_numeric_operands",
        new CustomIndexMapping(new JsonPath("properties", "int32"), MemberType.INT32),
        new CustomIndexMapping(new JsonPath("properties", "float32"), MemberType.FLOAT32),
        new CustomIndexMapping(new JsonPath("properties", "int64"), MemberType.INT64),
        new CustomIndexMapping(new JsonPath("properties", "float64"), MemberType.FLOAT64));
    definition.setIndices(new IndexList());
    createMapped(definition);
    NakshaCollection schema = stored(definition.getId());
    NakshaFeature fits = feature("fits", "int32", 20);
    fits.getProperties().put("float32", 4.5f);
    fits.getProperties().put("int64", 20L);
    fits.getProperties().put("float64", 4.5);
    WriteRequest writes = new WriteRequest();
    writes.add(new Write().createFeature(schema, fits));
    writes.add(new Write().createFeature(schema, feature("overflow", "int32", 2147483648L)));
    executeWrite(writes);
    // INT32 does not store a value beyond its range.
    assertNull(byId(executeRead(read(schema.getId()))).get("overflow").getProperties().get("int32"));
    Member int32 = member(schema, "int32");
    Member float32 = member(schema, "float32");
    Member int64 = member(schema, "int64");
    Member float64 = member(schema, "float64");

    assertEquals(Set.of("fits"), assertTranslatedLike(schema, definition, rest("p.int64=20"), new Equals(int64, 20L)));
    assertEquals(Set.of("fits"), assertTranslatedLike(schema, definition, rest("p.int64=gte=20"), new Gte(int64, 20L)));
    assertEquals(Set.of("fits"), assertTranslatedLike(schema, definition, rest("p.float64=4.5"), new Equals(float64, 4.5)));
    assertEquals(Set.of("fits"), assertTranslatedLike(schema, definition, rest("p.float64=lt=5.5"), new Lt(float64, 5.5)));
    // Operands of the exact member type still work for direct member queries.
    assertEquals(Set.of("fits"), ids(schema.getId(), new Equals(int32, 20)));
    assertEquals(Set.of("fits"), ids(schema.getId(), new Equals(float32, 4.5f)));

    // REST integers are Long and decimals Double, the storage rejects them where they don't fit the member type.
    assertRejectedLike(schema, definition, rest("p.int32=20"), new Equals(int32, 20L));
    assertRejectedLike(schema, definition, rest("p.int32=gt=2147483647"), new Gt(int32, 2147483647L));
    assertRejectedLike(schema, definition, rest("p.int32=4.5"), new Equals(int32, 4.5));
    assertRejectedLike(schema, definition, rest("p.int32=lt=4.5"), new Lt(int32, 4.5));
    assertRejectedLike(schema, definition, rest("p.float32=4.5"), new Equals(float32, 4.5));
    assertRejectedLike(schema, definition, rest("p.float32=gte=4.5"), new Gte(float32, 4.5));
    assertRejectedLike(schema, definition, rest("p.int64=4.5"), new Equals(int64, 4.5));
    assertRejectedLike(schema, definition, rest("p.float64=4"), new Equals(float64, 4L));
    // The rejection is not hidden by falling back to the legacy path, also not inside a compound expression.
    assertRejectedLike(schema, definition, rest("p.int64=20&p.int32=20"), new And(new Equals(int64, 20L), new Equals(int32, 20L)));
  }

  @Test void tagMapKeysAndObjectsFromRestMatchDirectMemberQueries() {
    NakshaCollection definition = mapped("custom_tag_map",
        new CustomIndexMapping(new JsonPath("properties", "address"), MemberType.TAG_MAP));
    definition.setIndices(new IndexList());
    createMapped(definition);
    NakshaCollection schema = stored(definition.getId());
    TagMap berlin = tags("city", "Berlin");
    berlin.put("zip", 10115L);
    berlin.put("rating", 4.5);
    berlin.put("open", true);
    TagMap hamburg = tags("city", "Hamburg");
    hamburg.put("zip", 20095L);
    hamburg.put("rating", 3.5);
    hamburg.put("open", false);
    TagMap nullCity = tags("city", null);
    nullCity.put("zip", 1L);
    WriteRequest writes = new WriteRequest();
    writes.add(new Write().createFeature(schema, feature("berlin", "address", berlin)));
    writes.add(new Write().createFeature(schema, feature("hamburg", "address", hamburg)));
    writes.add(new Write().createFeature(schema, feature("nullcity", "address", nullCity)));
    writes.add(new Write().createFeature(schema, feature("nocity", "address", tags("zip", 2L))));
    writes.add(new Write().createFeature(schema, feature("empty", "address", new TagMap())));
    writes.add(new Write().createFeature(schema, new NakshaFeature("noaddress")));
    executeWrite(writes);
    Member address = member(schema, "address");

    // Direct keys below the mapped tag map.
    assertEquals(Set.of("berlin"), assertTranslatedLike(schema, definition,
        rest("p.address.city=Berlin"), new TagEquals(address, "city", "Berlin")));
    assertEquals(Set.of("berlin", "hamburg"), assertTranslatedLike(schema, definition, rest("p.address.city=Berlin,Hamburg"),
        new Or(new TagEquals(address, "city", "Berlin"), new TagEquals(address, "city", "Hamburg"))));
    assertEquals(Set.of("hamburg", "nullcity", "nocity", "empty"), assertTranslatedLike(schema, definition,
        rest("p.address.city=ne=Berlin"), new Not(new TagEquals(address, "city", "Berlin"))));
    assertEquals(Set.of("hamburg"), assertTranslatedLike(schema, definition,
        rest("p.address.city=cs=Hamburg"), new TagEquals(address, "city", "Hamburg")));
    assertEquals(Set.of("berlin"), assertTranslatedLike(schema, definition,
        rest("p.address.zip=10115"), new TagEquals(address, "zip", 10115L)));
    assertEquals(Set.of("hamburg"), assertTranslatedLike(schema, definition,
        rest("p.address.zip=gt=15000"), new TagGt(address, "zip", 15000L)));
    assertEquals(Set.of("nullcity", "nocity"), assertTranslatedLike(schema, definition,
        rest("p.address.zip=lte=2"), new TagLte(address, "zip", 2L)));
    assertEquals(Set.of("berlin"), assertTranslatedLike(schema, definition,
        rest("p.address.rating=gte=4.5"), new TagGte(address, "rating", 4.5)));
    assertEquals(Set.of("hamburg"), assertTranslatedLike(schema, definition,
        rest("p.address.rating=lt=4"), new TagLt(address, "rating", 4L)));
    assertEquals(Set.of("berlin"), assertTranslatedLike(schema, definition,
        rest("p.address.open=true"), new TagEquals(address, "open", true)));
    assertEquals(Set.of("hamburg"), assertTranslatedLike(schema, definition,
        rest("p.address.open=false"), new TagEquals(address, "open", false)));
    assertEquals(Set.of("hamburg"), assertTranslatedLike(schema, definition,
        rest("p.address.city=Berlin,Hamburg&p.address.zip=gt=15000"),
        new And(new Or(new TagEquals(address, "city", "Berlin"), new TagEquals(address, "city", "Hamburg")),
            new TagGt(address, "zip", 15000L))));

    // An explicit null value differs from a missing key.
    assertEquals(Set.of("berlin", "hamburg", "nullcity"), assertTranslatedLike(schema, definition,
        rest("p.address.city!=.null"), new TagMapHasKey(address, "city")));
    assertEquals(Set.of("nocity", "empty"), assertTranslatedLike(schema, definition,
        rest("p.address.city=.null"), new Not(new TagMapHasKey(address, "city"))));
    assertEquals(Set.of("nullcity"), assertTranslatedLike(schema, definition,
        new PQuery(new Property("properties", "address", "city"), AnyOp.IS_NULL), new TagIsNull(address, "city")));

    // Whole objects, the REST parser adds an array alternative, which a tag map can't contain.
    assertEquals(Set.of("berlin"), assertTranslatedLike(schema, definition,
        rest("p.address=cs=" + urlEncoded("{\"city\":\"Berlin\",\"open\":true}")),
        new And(new TagEquals(address, "city", "Berlin"), new TagEquals(address, "open", true))));
    assertEquals(Set.of("nullcity"), assertTranslatedLike(schema, definition,
        rest("p.address=cs=" + urlEncoded("{\"city\":null}")), new TagIsNull(address, "city")));
    assertEquals(Set.of("berlin", "hamburg", "nullcity", "nocity", "empty"), assertTranslatedLike(schema, definition,
        rest("p.address=cs=" + urlEncoded("{}")), new Not(new IsNull(address))));
    assertEquals(Set.of("noaddress"), assertTranslatedLike(schema, definition,
        rest("p.address=.null"), new Not(new Not(new IsNull(address)))));

    // Nested objects and deeper paths keep the complete expression on the legacy path.
    for (String query : List.of("p.address=cs=" + urlEncoded("{\"geo\":{\"lat\":1}}"), "p.address.geo.lat=1",
        "p.address.city=Berlin&p.address.geo.lat=1", "p.address=Berlin")) {
      assertNull(PropertyMemberQueryTranslator.adapt(read(schema.getId()).withPropertyQuery(rest(query)), schema, definition), query);
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
