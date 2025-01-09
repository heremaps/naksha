package com.here.naksha.handler.activitylog.util;

import com.here.naksha.handler.activitylog.ActivityLogComparator;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.Original;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzActivityLog;
import naksha.base.JvmProxyUtil;
import naksha.model.XyzFeatureCollection;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.test.common.FileUtil;
import naksha.model.XyzNs;
import naksha.model.objects.NakshaFeature;

import java.util.List;

import static naksha.model.objects.NakshaProperties.XYZ_ACTIVITY_LOG_NS;

public class DatahubSamplesUtil {

  public static final String SAMPLE_SPACE_ID = "SDNujm7h";

  private static final String SAMPLES_DIR = "src/test/resources/dh_samples/";
  private static final String SAMPLES_FILE = "PropSearchForFeatureId.json";

  public static DatahubSample loadDatahubSample() {
    String sampleJson = loadDatahubSampleJson();
    return new DatahubSample(
        historyFeatures(sampleJson),
        activityFeatures(sampleJson)
    );
  }

  private static String loadDatahubSampleJson() {
    return FileUtil.loadFileOrFail(SAMPLES_DIR, SAMPLES_FILE);
  }

  private static List<NakshaFeature> historyFeatures(String sampleFeaturesJson) {
    List<NakshaFeature> features = activityFeatures(sampleFeaturesJson);
    features.forEach(feature -> {
      String originFeatureId = feature.getProperties().getActivityLog().getId();
      feature.setId(originFeatureId);
      feature.getProperties().remove(XYZ_ACTIVITY_LOG_NS);
    });
    return features;
  }

  private static List<NakshaFeature> activityFeatures(String sampleFeaturesJson) {
    List<NakshaFeature> features = featuresFromCollectionJson(sampleFeaturesJson);
    features.forEach(feature -> {
      String originId = feature.getId();
      XyzActivityLog datahubActivityLog = JvmProxyUtil.box(feature.getProperties().get(XYZ_ACTIVITY_LOG_NS), XyzActivityLog.class);
      XyzNs datahubXyzNamespace = feature.getProperties().getXyz();
      Original datahubOriginal = datahubActivityLog.getOriginal();
      String originAction = datahubActivityLog.getAction();
      String originPuuid = datahubOriginal.getPuuid();
      long updatedAt = datahubOriginal.getUpdatedAt();
      long createdAt = datahubOriginal.getCreatedAt();
      feature.getProperties().getXyz().setUuid(originId);
      if (originAction.equals("SAVE")) {
        originAction = "CREATE";
        datahubActivityLog.setAction("CREATE");
      }
      if (datahubOriginal.getSpace() == null) {
        datahubOriginal.setSpace(SAMPLE_SPACE_ID);
      }
      datahubXyzNamespace.setAction(EXyzAction.get(EXyzAction.class, originAction));
      datahubXyzNamespace.setPuuid(originPuuid);
      datahubXyzNamespace.setUpdatedAt(updatedAt);
      datahubXyzNamespace.setCreatedAt(createdAt);
    });
    features.sort(new ActivityLogComparator());
    return features;
  }

  private static List<NakshaFeature> featuresFromCollectionJson(String featuresCollectionJson) {
    XyzFeatureCollection collection = JsonSerializable.deserialize(featuresCollectionJson, XyzFeatureCollection.class);
    return collection.getFeatures();
  }

  public record DatahubSample(List<NakshaFeature> historyFeatures, List<NakshaFeature> activityFeatures) {

  }
}
