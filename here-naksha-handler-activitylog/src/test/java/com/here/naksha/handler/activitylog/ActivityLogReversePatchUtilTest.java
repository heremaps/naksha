package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogReversePatch.ReverseOp.REVERSE_INSERT;
import static com.here.naksha.handler.activitylog.ActivityLogReversePatch.ReverseOp.REVERSE_UPDATE;
import static com.here.naksha.handler.activitylog.ReversePatchAssertions.assertThat;

import com.here.naksha.handler.activitylog.ActivityLogReversePatch.ReverseOp;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActivityLogReversePatchUtilTest {

  @Test
  void shouldConvertDifferenceToPatch() {
    // Given
    XyzFeature before = xyzFeature(Map.of(
        "name", "John",
        "age", 30,
        "address", Map.of(
            "city", "Funkytown",
            "street", "Sesame Street",
            "number", 79
        )
    ));

    // And
    XyzFeature after = xyzFeature(Map.of(
        "name", "John",
        "age", 31,
        "address", Map.of(
            "city", "Funkytown",
            "street", "Sesame Street",
            "number", 87
        ),
        "occupation", "teacher"
    ));

    // When:
    ActivityLogReversePatch reversePatch = ActivityLogReversePatchUtil.reversePatch(before, after);

    // Then:
    assertThat(reversePatch)
        .hasRemoveOpsCount(1) // we added 'occupation'
        .hasReplaceOpsCount(2) // we changed 'age' and 'number'
        .hasAddOpsCount(0) // we did not remove anything
        .hasReverseOps(
            new ReverseOp(REVERSE_UPDATE, "properties.age", 30),
            new ReverseOp(REVERSE_UPDATE, "properties.address.number", 79),
            new ReverseOp(REVERSE_INSERT, "properties.occupation", null)
        );
  }

  private XyzFeature xyzFeature(Map<String, Object> props) {
    XyzFeature feature = new XyzFeature();
    XyzProperties properties = new XyzProperties();
    properties.putAll(props);
    feature.setProperties(properties);
    return feature;
  }
}