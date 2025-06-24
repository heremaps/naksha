package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.PROPERTY_ACTIVITY_LOG_ID;
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.PROPERTY_UUID;

import com.here.naksha.test.common.assertions.PropertyQueryAssertions;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ReadFeatures;
import naksha.model.request.query.*;
import org.junit.jupiter.api.Test;

import java.util.List;

class ActivityLogRequestTranslationUtilTest {

  @Test
  void shouldTranslateIdToUuid() {
    // Given:
    String expectedId = "some_id";
    PQuery singleIdQuery = new PQuery(new Property(NakshaFeature.ID_KEY), StringOp.EQUALS, expectedId);
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.getQuery().setProperties(singleIdQuery);

    // When:
    ActivityLogRequestTranslationUtil.transformOriginalRequest(readFeatures);

    // Then:
    PropertyQueryAssertions.assertThatPropertyQuery(readFeatures.getQuery().getProperties())
        .hasOp(StringOp.EQUALS)
        .hasProperty(PROPERTY_UUID)
        .hasValue(expectedId);
  }

  @Test
  void shouldTranslateIdsToUuids() {
    // Given:
    String firstId = "id_1";
    String secondId = "id_2";
    final Property idProperty = new Property(NakshaFeature.ID_KEY);
    IPropertyQuery idsQuery = new POr(
        new PQuery(idProperty, StringOp.EQUALS, firstId),
            new PQuery(idProperty, StringOp.EQUALS, secondId)
    );
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.getQuery().setProperties(idsQuery);

    // When:
    ActivityLogRequestTranslationUtil.transformOriginalRequest(readFeatures);

    // Then:
    PropertyQueryAssertions.assertThatPropertyQuery(readFeatures.getQuery().getProperties())
        .isPOr()
        .hasChildrenThat(
            first -> first
                .hasOp(StringOp.EQUALS)
                .hasProperty(PROPERTY_UUID)
                .hasValue(firstId),
            second -> second
                .hasOp(StringOp.EQUALS)
                .hasProperty(PROPERTY_UUID)
                .hasValue(secondId)
        );
  }

  @Test
  void shouldTranslateActivityLogIdToId() {
    // Given:
    String expectedId = "some_id";
    PQuery singleActivityLogIdQuery = new PQuery(PROPERTY_ACTIVITY_LOG_ID, StringOp.EQUALS, expectedId);
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.getQuery().setProperties(singleActivityLogIdQuery);

    // When:
    ActivityLogRequestTranslationUtil.transformOriginalRequest(readFeatures);

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
    ActivityLogRequestTranslationUtil.transformOriginalRequest(readFeatures);

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
    ActivityLogRequestTranslationUtil.transformOriginalRequest(readFeatures);

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
}