package com.here.naksha.mom10.transform;

import static com.here.naksha.mom10.util.FeaturesAssertionUtil.assertFeaturesEqual;

import com.here.naksha.mom10.Mom10Transformation;
import com.here.naksha.mom10.TransformationSamples.TransformationSample;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Mom10TransformationTest {

  @ParameterizedTest
  @MethodSource("com.here.naksha.mom10.TransformationSamples#streamSamples")
  void shouldPopulatePreMom10Namespaces(TransformationSample versionedFeatures) {
    // When
    Mom10Transformation.populatePreMom10Namespaces(versionedFeatures.getMom10());

    // Then:
    assertFeaturesEqual(
        versionedFeatures.getNakshaInternal(), // expected - untouched
        versionedFeatures.getMom10() // actual - transformed
    );
  }

  @ParameterizedTest
  @MethodSource("com.here.naksha.mom10.TransformationSamples#streamSamples")
  void shouldDropPreMom10Namespaces(TransformationSample versionedFeatures) {
    // When
    Mom10Transformation.dropPreMom10Namespaces(versionedFeatures.getNakshaInternal());

    // Then:
    assertFeaturesEqual(versionedFeatures.getMom10(), versionedFeatures.getNakshaInternal());
  }
}