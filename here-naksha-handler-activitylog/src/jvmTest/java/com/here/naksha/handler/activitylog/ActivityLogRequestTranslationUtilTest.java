package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.PROPERTY_ACTIVITY_LOG_ID;
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.transformOriginalRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import naksha.base.JvmInt64;
import naksha.base.StringList;
import naksha.model.Guid;
import naksha.model.TupleNumber;
import naksha.model.Version;
import naksha.model.request.ReadFeatures;
import naksha.model.request.ResultFilter;
import naksha.model.request.ResultFilterList;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.POr;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.PTrue;
import naksha.model.request.query.StringOp;
import org.junit.jupiter.api.Test;

class ActivityLogRequestTranslationUtilTest {

  private static final String TEST_SPACE_ID = "test_space_id";
  private final Random random = new Random();

  @Test
  void shouldTranslateSingleGuidPassedAsFeatureId() {
    // Given: single GUID passed in ReadFeatures
    String featureId = "test_feature_id";
    Version version = randomVersion();
    String rawGuid = rawGuid(featureId, version);
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.setFeatureIds(StringList.of(rawGuid));

    // When: translating the original request
    transformOriginalRequest(readFeatures, TEST_SPACE_ID);

    // Then: request will reach correct collection and history
    verifyAllHistoricalVersionsInCollection(readFeatures);

    // And: there is a single featureId withing the request
    StringList featureIds = readFeatures.getFeatureIds();
    assertEquals(1, featureIds.size());
    assertEquals(featureId, featureIds.get(0));

    // And: translated request contains max version defined in original GUID, no post-filtering needed
    assertEquals(version, readFeatures.getVersion());
    assertTrue(readFeatures.getResultFilters().isEmpty());
  }

  @Test
  void shouldTranslateMultipleGuidsPassedAsFeatureIds() {
    // Given: multiple GUIDs passed in ReadFeatures
    Map<String, Version> featureVersions = Map.of(
        "f1", randomVersion(),
        "f2", randomVersion(),
        "f3", randomVersion()
    );
    List<String> rawGuids = featureVersions.entrySet().stream()
        .map(entry -> rawGuid(entry.getKey(), entry.getValue()))
        .toList();
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.setFeatureIds(StringList.fromList(rawGuids));

    // When: translating the original request
    transformOriginalRequest(readFeatures, TEST_SPACE_ID);

    // Then: request will reach correct collection and history
    verifyAllHistoricalVersionsInCollection(readFeatures);

    // And: no max version is set - it will be covered in post filtering
    assertNull(readFeatures.getVersion());

    // And: translated request contains featureIds from input guids
    StringList featureIds = readFeatures.getFeatureIds();
    assertEquals(3, featureIds.size());
    assertTrue(featureIds.containsAll(featureVersions.keySet()));

    // And: request version is null - mutliple version querying is not available during query processing stage
    //      but translated request contains filter for version postprocessing, so versions are taken into account
    ResultFilterList resultFilters = readFeatures.getResultFilters();
    assertEquals(1, resultFilters.size());
    ResultFilter versionFilter = resultFilters.get(0);
    assertEquals(new MaxVersionResultFilter(featureVersions), versionFilter);
  }

  @Test
  void shouldTranslateActivityLogIdToFeatureId() {
    // Given:
    String featureId = "some_id";
    PQuery singleActivityLogIdQuery = new PQuery(PROPERTY_ACTIVITY_LOG_ID, StringOp.EQUALS, featureId);
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.getQuery().setProperties(singleActivityLogIdQuery);

    // When:
    transformOriginalRequest(readFeatures, TEST_SPACE_ID);

    // Then: request will reach correct collection and history
    verifyAllHistoricalVersionsInCollection(readFeatures);

    // And: no max version is set
    assertNull(readFeatures.getVersion());

    // And: there is a single featureId withing the request
    StringList featureIds = readFeatures.getFeatureIds();
    assertEquals(1, featureIds.size());
    assertEquals(featureId, featureIds.get(0));

    // And: there is dummy POr(PTrue, PTrue) query
    assertNull(readFeatures.getQuery().getProperties());
  }

  @Test
  void shouldTranslateActivityLogIdsToFeatureIds() {
    // Given:
    String firstId = "id_1";
    String secondId = "id_2";
    IPropertyQuery activityLogIdsQuery = new POr(
        new PQuery(PROPERTY_ACTIVITY_LOG_ID, StringOp.EQUALS, firstId),
        new PQuery(PROPERTY_ACTIVITY_LOG_ID, StringOp.EQUALS, secondId)
    );
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.getQuery().setProperties(activityLogIdsQuery);

    // When:
    transformOriginalRequest(readFeatures, TEST_SPACE_ID);

    // Then: request will reach correct collection and history
    verifyAllHistoricalVersionsInCollection(readFeatures);

    // And: no max version is set
    assertNull(readFeatures.getVersion());

    // And: there is a single featureId withing the request
    StringList featureIds = readFeatures.getFeatureIds();
    assertEquals(2, featureIds.size());
    assertTrue(featureIds.containsAll(List.of(firstId, secondId)));

    // And: the pQuery left is effectively dead
    // TODO CASL-1123: in the future we should simply delete such IPropertyQuery
    POr root = (POr) readFeatures.getQuery().getProperties();
    assertTrue(root.stream().allMatch(PTrue.class::isInstance));
  }

  @Test
  void shouldApplyMixedTranslations() {
    // Given: guids passed in ReadFeatures.featureIds
    String id = "id";
    Version version = randomVersion();
    String guid = rawGuid(id, version);
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.setFeatureIds(StringList.of(guid));

    // And: featureId passed as activity log ns prop
    String activityLogId = "activity_log_id";
    IPropertyQuery activityLogIdQuery = new PQuery(PROPERTY_ACTIVITY_LOG_ID, StringOp.EQUALS, activityLogId);
    readFeatures.withPropertyQuery(activityLogIdQuery);

    // When:
    transformOriginalRequest(readFeatures, TEST_SPACE_ID);

    // Then: request will reach correct collection and history
    verifyAllHistoricalVersionsInCollection(readFeatures);

    // And: no max version is set (we can't use the one from guid as it would limit features mentioned in activity log ns)
    assertNull(readFeatures.getVersion());

    // And: all the feature ids are part of final query
    StringList featureIds = readFeatures.getFeatureIds();
    assertEquals(2, featureIds.size());
    assertTrue(featureIds.containsAll(List.of(id, activityLogId)));

    // And: post-filtering is applied only to features included as guid
    assertNull(readFeatures.getQuery().getProperties());
    ResultFilterList resultFilters = readFeatures.getResultFilters();
    assertEquals(1, resultFilters.size());
    assertEquals(new MaxVersionResultFilter(Map.of(id, version)), resultFilters.get(0));
  }

  private void verifyAllHistoricalVersionsInCollection(ReadFeatures readFeatures) {
    assertTrue(readFeatures.getQueryHistory());
    StringList collectionIds = readFeatures.getCollectionIds();
    assertEquals(1, collectionIds.size());
    assertEquals(TEST_SPACE_ID, collectionIds.get(0));
    assertEquals(Integer.MAX_VALUE, readFeatures.getVersions());
  }

  private Version randomVersion() {
    return new Version(random.nextLong());
  }

  private String rawGuid(String featureId, Version version) {
    return new Guid(featureId, new TupleNumber(
        new JvmInt64(0), 0, 0, new JvmInt64(0), version, 0
    )).toString();
  }
}