package com.here.naksha.mom10.transform;

import static com.here.naksha.mom10.util.FeaturesAssertionUtil.assertFeaturesEqual;

import com.here.naksha.mom10.TransformationSamples.TransformationSample;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Mom10TransformationTest {

  @ParameterizedTest
  @MethodSource("com.here.naksha.mom10.TransformationSamples#streamSamples")
  void shouldPopulateMom10Meta(TransformationSample versionedFeatures) {
    // When
    Mom10Transformation.populateMom10Meta(versionedFeatures.original());

    // Then:
    assertFeaturesEqual(
        versionedFeatures.transformed(), // expected - untouched
        versionedFeatures.original() // actual - transformed
    );
  }

  @ParameterizedTest
  @MethodSource("com.here.naksha.mom10.TransformationSamples#streamSamples")
  void shouldDropMom10Meta(TransformationSample versionedFeatures) {
    // When
    Mom10Transformation.dropMom10Meta(versionedFeatures.transformed());

    // Then:
    assertFeaturesEqual(versionedFeatures.original(), versionedFeatures.transformed());
  }
}