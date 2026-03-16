package com.here.naksha.mom10;

import static org.junit.jupiter.api.Named.named;

import java.util.stream.Stream;
import naksha.base.AnyObject;
import naksha.model.mom.MomMetaNs;
import naksha.model.objects.NakshaFeature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Mom10VerificationTest {

  static final class VerificationCase {
    private final AnyObject rawFeature;
    private final boolean isAtLeastMom10;

    VerificationCase(AnyObject rawFeature, boolean isAtLeastMom10) {
      this.rawFeature = rawFeature;
      this.isAtLeastMom10 = isAtLeastMom10;
    }

    AnyObject getRawFeature() {
      return rawFeature;
    }

    boolean isAtLeastMom10() {
      return isAtLeastMom10;
    }
  }

  @ParameterizedTest
  @MethodSource
  void shouldVerifyIfFeatureIsInMom10(VerificationCase verificationCase) {
    Assertions.assertEquals(
        verificationCase.isAtLeastMom10,
        Mom10Verification.isMom10OrGreater(verificationCase.rawFeature)
    );
  }

  private static Stream<Named<VerificationCase>> shouldVerifyIfFeatureIsInMom10() {
    return Stream.of(
        named("10.0.0 version in correct field => true", new VerificationCase(featureWithVersionInMeta("10.0.0"), true)),
        named("10.0 version in correct field => false", new VerificationCase(featureWithVersionInMeta("10.0"), false)),
        named("10 version in correct field => false", new VerificationCase(featureWithVersionInMeta("10"), false)),
        named("10.0.1-lorem-ipsum version in correct field => true",
            new VerificationCase(featureWithVersionInMeta("10.0.1-lorem-ipsum"), true)),
        named("12.0.3 version in correct field => true", new VerificationCase(featureWithVersionInMeta("12.0.3"), true)),
        named("9.9.9 version in correct field => false", new VerificationCase(featureWithVersionInMeta("9.9.9"), false)),
        named("Invalid string in correct field => false",
            new VerificationCase(featureWithVersionInMeta("10.plus.1.equals.11-not_a_semver"), false)),
        named("10.0.0 version in incorrect field => false", new VerificationCase(featureWithVersionInOldMetaNs("10.0.0"), false)),
        named("Newer version in incorrect field => false", new VerificationCase(featureWithVersionInOldMetaNs("12.0.0"), false)),
        named("Older version in incorrect field => false", new VerificationCase(featureWithVersionInOldMetaNs("8.91.0"), false)),
        named("Invalid string in incorrect field => false",
            new VerificationCase(featureWithVersionInOldMetaNs("11.minus.2.equals.9"), false))
    );
  }

  private static NakshaFeature featureWithVersionInOldMetaNs(String modelVersion) {
    MomMetaNs metaNs = new MomMetaNs();
    metaNs.put("modelVersion", modelVersion);
    NakshaFeature feature = new NakshaFeature();
    feature.getProperties().setMeta(metaNs);
    return feature;
  }

  private static NakshaFeature featureWithVersionInMeta(String modelVersion) {
    NakshaFeature feature = new NakshaFeature();
    AnyObject newMeta = new AnyObject();
    newMeta.put(MetaProperties.MODEL_VERSION, modelVersion);
    feature.getProperties().put(MetaProperties.META, newMeta);
    return feature;
  }
}