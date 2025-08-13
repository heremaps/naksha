package com.here.naksha.handler.activitylog;

import com.here.naksha.test.common.FileUtil;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;
import naksha.base.FromJsonOptions;
import naksha.base.Platform;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static java.util.Objects.requireNonNull;

class ActivityLogEnhancerTest {

  private static final String SAMPLES_DIR = "src/jvmTest/resources/enhancer_samples/";
  private static final String NEW_FEATURE_JSON = "new_feature.json";
  private static final String OLD_FEATURE_JSON = "old_feature.json";
  private static final String EXPECTED_FEATURE_JSON = "expected_enhanced_feature.json";

  private static final String SPACE_ID = "enhancer_test_space_id";

  @ParameterizedTest
  @MethodSource("samples")
  void shouldEnhanceFeatureWithPredecessor(String sampleDir, NakshaFeature oldFeature, NakshaFeature newFeature, String expectedFeatureJson)
      throws JSONException {
    // When
    NakshaFeature enhancedFeature = ActivityLogEnhancer.enhanceWithActivityLog(newFeature, oldFeature, SPACE_ID);

    // And
    String enhancedFeatureJson = Platform.toJson(enhancedFeature);

    // Then
    JSONAssert.assertEquals(
        "Comparison failed for sample: " + sampleDir,
        expectedFeatureJson,
        enhancedFeatureJson,
        JSONCompareMode.LENIENT
    );
  }

  private static Stream<Arguments> samples() {
    return sampleDirs()
        .map(path -> {
          String stringPath = path.toString() + "/";
          return Arguments.arguments(
              path.getFileName().toString(),
              featureFromFile(stringPath, OLD_FEATURE_JSON),
              featureFromFile(stringPath, NEW_FEATURE_JSON),
              FileUtil.loadFileOrFail(stringPath, EXPECTED_FEATURE_JSON)
          );
        });
  }

  private static NakshaFeature featureFromFile(String sampleDir, String fileName) {
    String fileContent = FileUtil.loadFileOrFail(sampleDir, fileName);
    return Platform.fromJson(requireNonNull(fileContent), NakshaFeature.TYPE);
  }

  private static @NotNull Stream<@NotNull Path> sampleDirs() {
    final var samplesRoot = requireNonNull(Paths.get(SAMPLES_DIR));
    final var fileArray = requireNonNull(samplesRoot.toFile().listFiles(File::isDirectory));
    return Arrays.stream(fileArray).map(File::toPath);
  }
}