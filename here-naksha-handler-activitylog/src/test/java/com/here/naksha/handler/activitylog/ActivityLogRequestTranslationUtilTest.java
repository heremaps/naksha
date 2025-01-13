package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.PROPERTY_ACTIVITY_LOG_ID;

import naksha.model.objects.NakshaFeature;
import naksha.model.request.ReadFeatures;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.Property;
import naksha.model.request.query.StringOp;
import org.junit.jupiter.api.Test;

class ActivityLogRequestTranslationUtilTest {

  @Test
  void shouldTranslateIdToUuid() {
    // Given:
    String expectedId = "some_id";
    PQuery singleIdQuery = new PQuery(new Property(NakshaFeature.ID_KEY), StringOp.EQUALS, expectedId);
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.getQuery().setProperties(singleIdQuery);

    // When:
    ActivityLogRequestTranslationUtil.translatePropertyOperation(readFeatures);

    // Then:
    POpAssertion.assertThatOperation(readFeatures.getPropertyOp())
        .hasType(POpType.EQ)
        .hasPRef(uuid())
        .hasValue(expectedId);
  }

  @Test
  void shouldTranslateIdsToUuids() {
    // Given:
    String firstId = "id_1";
    String secondId = "id_2";
    POp idsQuery = POp.or(
        POp.eq(id(), firstId),
        POp.eq(id(), secondId)
    );
    ReadFeatures readFeatures = new ReadFeatures().withPropertyOp(idsQuery);

    // When:
    ActivityLogRequestTranslationUtil.translatePropertyOperation(readFeatures);

    // Then:
    POpAssertion.assertThatOperation(readFeatures.getPropertyOp())
        .hasType(POpType.OR)
        .hasChildrenThat(
            first -> first
                .hasType(POpType.EQ)
                .hasPRef(uuid())
                .hasValue(firstId),
            second -> second
                .hasType(POpType.EQ)
                .hasPRef(uuid())
                .hasValue(secondId)
        );
  }

  @Test
  void shouldTranslateActivityLogIdToId() {
    // Given:
    String expectedId = "some_id";
    POp singleActivityLogIdQuery = POp.eq(PROPERTY_ACTIVITY_LOG_ID, expectedId);
    ReadFeatures readFeatures = new ReadFeatures().withPropertyOp(singleActivityLogIdQuery);

    // When:
    ActivityLogRequestTranslationUtil.translatePropertyOperation(readFeatures);

    // Then:
    POpAssertion.assertThatOperation(readFeatures.getPropertyOp())
        .hasType(POpType.EQ)
        .hasPRef(id())
        .hasValue(expectedId);
  }

  @Test
  void shouldTranslateActivityLogIdsToIds() {
    // Given:
    String firstId = "id_1";
    String secondId = "id_2";
    POp activityLogIdsQuery = POp.or(
        POp.eq(PROPERTY_ACTIVITY_LOG_ID, firstId),
        POp.eq(PROPERTY_ACTIVITY_LOG_ID, secondId)
    );
    ReadFeatures readFeatures = new ReadFeatures().withPropertyOp(activityLogIdsQuery);

    // When:
    ActivityLogRequestTranslationUtil.translatePropertyOperation(readFeatures);

    // Then:
    POpAssertion.assertThatOperation(readFeatures.getPropertyOp())
        .hasType(POpType.OR)
        .hasChildrenThat(
            first -> first
                .hasType(POpType.EQ)
                .hasPRef(id())
                .hasValue(firstId),
            second -> second
                .hasType(POpType.EQ)
                .hasPRef(id())
                .hasValue(secondId)
        );
  }

  @Test
  void shouldApplyMixedTranslations() {
    // Given:
    String id = "id";
    String activityLogId = "activity_log_id";
    POp mixedQuery = POp.or(
        POp.eq(id(), id),
        POp.eq(PROPERTY_ACTIVITY_LOG_ID, activityLogId)
    );
    ReadFeatures readFeatures = new ReadFeatures().withPropertyOp(mixedQuery);

    // When:
    ActivityLogRequestTranslationUtil.translatePropertyOperation(readFeatures);

    // Then:
    POpAssertion.assertThatOperation(readFeatures.getPropertyOp())
        .hasType(POpType.OR)
        .hasChildrenThat(
            first -> first
                .hasType(POpType.EQ)
                .hasPRef(uuid())
                .hasValue(id),
            second -> second
                .hasType(POpType.EQ)
                .hasPRef(id())
                .hasValue(activityLogId)
        );
  }
}