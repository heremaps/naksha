package com.here.naksha.mom10.transform;

import static com.here.naksha.mom10.util.FeaturesAssertionUtil.assertFeaturesEqual;

import com.here.naksha.mom10.TransformationSamples.TransformationSample;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Mom10TransformationTest {

  @ParameterizedTest
  @MethodSource("com.here.naksha.mom10.TransformationSamples#streamSamples")
  void shouldPopulatePreMom10Namespaces(TransformationSample versionedFeatures) {
    // When
    Mom10Transformation.populatePreMom10Namespaces(versionedFeatures.mom10());

    // Then:
    assertFeaturesEqual(
        versionedFeatures.nakshaInternal(), // expected - untouched
        versionedFeatures.mom10() // actual - transformed
    );
  }

  @ParameterizedTest
  @MethodSource("com.here.naksha.mom10.TransformationSamples#streamSamples")
  void shouldDropPreMom10Namespaces(TransformationSample versionedFeatures) {
    // When
    Mom10Transformation.dropPreMom10Namespaces(versionedFeatures.nakshaInternal());

    // Then:
    assertFeaturesEqual(versionedFeatures.mom10(), versionedFeatures.nakshaInternal());
  }
}