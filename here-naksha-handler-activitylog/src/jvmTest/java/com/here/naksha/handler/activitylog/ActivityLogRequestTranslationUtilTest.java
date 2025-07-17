package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.PROPERTY_ACTIVITY_LOG_ID;
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.PROPERTY_UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.here.naksha.test.common.assertions.PropertyQueryAssertions;
import java.util.List;
import naksha.base.JvmInt64;
import naksha.base.StringList;
import naksha.model.Guid;
import naksha.model.TupleNumber;
import naksha.model.Version;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ReadFeatures;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.POr;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.Property;
import naksha.model.request.query.StringOp;
import org.junit.jupiter.api.Test;

class ActivityLogRequestTranslationUtilTest {

  private static final String TEST_SPACE_ID = "test_space_id";

  @Test
  void shouldTreatOriginalIdAsGuuid() {
    // Given:
    String rawGuid = "urn:naksha:guid:test_feature_id:-3843734806738129423:-1832392554:-439412809:-9139335626361915124:2025:7:21:13:0";
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.setFeatureIds(StringList.of(rawGuid));

    // When:
    ActivityLogRequestTranslationUtil.transformOriginalRequest(readFeatures, TEST_SPACE_ID);

    // Then:
    StringList featureIds = readFeatures.getFeatureIds();
    assertEquals(0, featureIds.size());

    // And
    Guid actualGuid = Guid.fromString(rawGuid);
    assertEquals(actualGuid.id, featureIds.get(0));
    assertEquals(actualGuid.tupleNumber.version, readFeatures.getVersion());
  }

  @Test
  void shouldTranslateIdsToUuids() {
    // Given:
    String firstId = "id_1";
    String secondId = "id_2";
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.setFeatureIds(StringList.of(firstId, secondId));

    // When:
    ActivityLogRequestTranslationUtil.transformOriginalRequest(readFeatures, TEST_SPACE_ID);

    // Then:
    Guid actualGuid = Guid.fromString(rawGuid);
    assertEquals(actualGuid.id, featureIds.get(0));
    assertEquals(actualGuid.tupleNumber.version, readFeatures.getVersion());
  }

  @Test
  void shouldTranslateActivityLogIdToId() {
    // Given:
    String expectedId = "some_id";
    PQuery singleActivityLogIdQuery = new PQuery(PROPERTY_ACTIVITY_LOG_ID, StringOp.EQUALS, expectedId);
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.getQuery().setProperties(singleActivityLogIdQuery);

    // When:
    ActivityLogRequestTranslationUtil.transformOriginalRequest(readFeatures, TEST_SPACE_ID);

    // Then:
    PropertyQueryAssertions.assertThatPropertyQuery(readFeatures.getQuery().getProperties())
        .hasOp(StringOp.EQUALS)
        .hasProperty(List.of(NakshaFeature.ID_KEY))
        .hasValue(expectedId);
  }

  @Test
  void shouldTranslateActivityLogIdsToIds() {
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
    ActivityLogRequestTranslationUtil.transformOriginalRequest(readFeatures, TEST_SPACE_ID);

    // Then:
    PropertyQueryAssertions.assertThatPropertyQuery(readFeatures.getQuery().getProperties())
        .isPOr()
        .hasChildrenThat(
            first -> first
                .hasOp(StringOp.EQUALS)
                .hasProperty(List.of(NakshaFeature.ID_KEY))
                .hasValue(firstId),
            second -> second
                .hasOp(StringOp.EQUALS)
                .hasProperty(List.of(NakshaFeature.ID_KEY))
                .hasValue(secondId)
        );
  }

  @Test
  void shouldApplyMixedTranslations() {
    // Given:
    String id = "id";
    String activityLogId = "activity_log_id";
    IPropertyQuery mixedQuery = new POr(
        new PQuery(new Property(NakshaFeature.ID_KEY), StringOp.EQUALS, id),
        new PQuery(PROPERTY_ACTIVITY_LOG_ID, StringOp.EQUALS, activityLogId)
    );
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.getQuery().setProperties(mixedQuery);

    // When:
    ActivityLogRequestTranslationUtil.transformOriginalRequest(readFeatures, TEST_SPACE_ID);

    // Then:
    PropertyQueryAssertions.assertThatPropertyQuery(readFeatures.getQuery().getProperties())
        .isPOr()
        .hasChildrenThat(
            first -> first
                .hasOp(StringOp.EQUALS)
                .hasProperty(PROPERTY_UUID)
                .hasValue(id),
            second -> second
                .hasOp(StringOp.EQUALS)
                .hasProperty(List.of(NakshaFeature.ID_KEY))
                .hasValue(activityLogId)
        );
  }

  private Guid guid(String featureId, Version version) {
    return new Guid(
        featureId,
        new TupleNumber(
            new JvmInt64(0),
           0,
            new JvmInt64(0),
            version,


            )
    )
  }
}