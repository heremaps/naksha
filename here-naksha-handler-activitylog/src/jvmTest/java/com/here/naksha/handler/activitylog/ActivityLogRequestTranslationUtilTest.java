package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.PROPERTY_ACTIVITY_LOG_ID;
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.transformOriginalRequest;
import static com.here.naksha.handler.activitylog.GuidUtil.guid;
import static com.here.naksha.handler.activitylog.GuidUtil.randomVersion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import naksha.base.StringList;
import naksha.model.Guid;
import naksha.model.GuidList;
import naksha.model.Version;
import naksha.model.request.ReadFeatures;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.POr;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.StringOp;
import org.junit.jupiter.api.Test;

class ActivityLogRequestTranslationUtilTest {

  private static final String TEST_SPACE_ID = "test_space_id";

  @Test
  void shouldTranslateSingleGuidPassedAsFeatureId() {
    // Given: single GUID passed in ReadFeatures
    String featureId = "test_feature_id";
    Guid guid = guid(featureId, randomVersion());
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.setFeatureIds(StringList.of(guid.toString()));

    // When: translating the original request
    transformOriginalRequest(readFeatures, TEST_SPACE_ID);

    // Then: request will reach correct collection and history
    verifyAllHistoricalVersionsInCollection(readFeatures);

    // And: no feature ids are passed from original requesy
    assertTrue(readFeatures.getFeatureIds().isEmpty());

    // And: there is a single guid passed from original featureId
    GuidList finalGuids = readFeatures.getGuids();
    assertEquals(1, finalGuids.size());
    assertEquals(guid, finalGuids.get(0));
  }

  @Test
  void shouldTranslateMultipleGuidsPassedAsFeatureIds() {
    // Given: multiple GUIDs passed in ReadFeatures
    GuidList guids = GuidList.of(
        guid("f1", randomVersion()),
        guid("f2", randomVersion()),
        guid("f3", randomVersion())
    );
    List<String> rawGuids = guids.stream().map(Guid::toString).toList();
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.setFeatureIds(StringList.fromList(rawGuids));

    // When: translating the original request
    transformOriginalRequest(readFeatures, TEST_SPACE_ID);

    // Then: request will reach correct collection and history
    verifyAllHistoricalVersionsInCollection(readFeatures);

    // And: no max version is set - it will be covered in post filtering
    assertNull(readFeatures.getVersion());

    // And: translated request does not contain any feature id (no acitivytLog was defined)
    assertTrue(readFeatures.getFeatureIds().isEmpty());

    // And: all guids defined in featureIds were moved to ReadFeatures.guids
    GuidList finalGuids = readFeatures.getGuids();
    assertEquals(guids.size(), finalGuids.size());
    assertTrue(finalGuids.containsAll(guids));
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

    // And:
    assertNull(readFeatures.getQuery().getProperties());

    // And: there are no guids (nothing was declared in original featureIds)
    assertTrue(readFeatures.getGuids().isEmpty());
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

    // And: all ids defined in AcitvityLogNs are now part of featureIds
    StringList featureIds = readFeatures.getFeatureIds();
    assertEquals(2, featureIds.size());
    assertTrue(featureIds.containsAll(List.of(firstId, secondId)));

    // And: the pQuery left is effectively dead
    assertNull(readFeatures.getQuery().getProperties());

    // And: there are no guids (nothing was declared in original featureIds)
    assertTrue(readFeatures.getGuids().isEmpty());
  }

  @Test
  void shouldApplyMixedTranslations() {
    // Given: guids passed in ReadFeatures.featureIds
    String id = "id";
    Version version = randomVersion();
    Guid guid = guid(id, version);
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.setFeatureIds(StringList.of(guid.toString()));

    // And: featureId passed as activity log ns prop
    String activityLogId = "activity_log_id";
    IPropertyQuery activityLogIdQuery = new PQuery(PROPERTY_ACTIVITY_LOG_ID, StringOp.EQUALS, activityLogId);
    readFeatures.withPropertyQuery(activityLogIdQuery);

    // When:
    transformOriginalRequest(readFeatures, TEST_SPACE_ID);

    // Then: request will reach correct collection and history
    verifyAllHistoricalVersionsInCollection(readFeatures);

    // And: feature ids are populated from ActivityLogNs
    StringList finalFeatureIds = readFeatures.getFeatureIds();
    assertEquals(1, finalFeatureIds.size());
    assertEquals(activityLogId, finalFeatureIds.get(0));

    // And: guuids are populared from original feature ids
    GuidList finalGuids = readFeatures.getGuids();
    assertEquals(1, finalGuids.size());
    assertEquals(guid, finalGuids.get(0));
  }

  private void verifyAllHistoricalVersionsInCollection(ReadFeatures readFeatures) {
    assertTrue(readFeatures.getQueryHistory());
    StringList collectionIds = readFeatures.getCollectionId();
    assertEquals(1, collectionIds.size());
    assertEquals(TEST_SPACE_ID, collectionIds.get(0));
    assertEquals(Integer.MAX_VALUE, readFeatures.getVersions());
  }
}