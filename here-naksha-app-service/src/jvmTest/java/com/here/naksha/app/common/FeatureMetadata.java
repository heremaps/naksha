package com.here.naksha.app.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import naksha.base.JvmBoxingUtil;
import naksha.base.Base;
import naksha.model.XyzFeatureCollection;
import naksha.model.XyzNs;
import naksha.model.objects.NakshaFeature;

public record FeatureMetadata(String featureId, String uuid, long createdAt, long updatedAt) {

  public static FeatureMetadata from(NakshaFeature feature) {
    XyzNs xyzNamespace = feature.getProperties().getXyz();
    return new FeatureMetadata(
        feature.getId(),
        xyzNamespace.getUuid(),
        xyzNamespace.getCreatedAt().toLong(),
        xyzNamespace.getUpdatedAt().toLong()
    );
  }

  public static class ExtractionUtil {
    private ExtractionUtil() {}

    public static FeatureMetadata featureMetadataFromFeatureResp(String featureResponse) {
      NakshaFeature feature = JvmBoxingUtil.box(Base.fromJSON(featureResponse), NakshaFeature.class);
      return FeatureMetadata.from(feature);
    }

    public static FeatureMetadata featureMetadataFromCollectionResp(String featureCollectionResponseJson) {
      List<FeatureMetadata> featuresMetadata = featuresMetadata(featureCollectionResponseJson).toList();
      assertEquals(1, featuresMetadata.size(), "Expected single contained 0/multiple features");
      return featuresMetadata.get(0);
    }

    public static Map<String, FeatureMetadata> featuresMetadataById(String featureCollectionResponseJson) {
      return featuresMetadata(featureCollectionResponseJson)
          .collect(Collectors.toMap(fm -> fm.featureId, fm -> fm));
    }

    private static Stream<FeatureMetadata> featuresMetadata(String featureCollectionResponseJson) {
      XyzFeatureCollection featureCollection = JvmBoxingUtil.box(Base.fromJSON(featureCollectionResponseJson),
          XyzFeatureCollection.class);
      return featureCollection.getFeatures().stream().map(FeatureMetadata::from);
    }
  }
}
