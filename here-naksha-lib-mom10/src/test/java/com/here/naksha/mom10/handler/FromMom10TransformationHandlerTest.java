package com.here.naksha.mom10.handler;

import static com.here.naksha.mom10.util.FeaturesAssertionUtil.assertFeaturesEqual;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.exceptions.NoCursor;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.storage.ErrorResult;
import com.here.naksha.lib.core.models.storage.Request;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.util.storage.ResultHelper;
import com.here.naksha.mom10.TransformationSamples.TransformationSample;
import com.here.naksha.mom10.handler.TransformationSuccess.FromMom10;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FromMom10TransformationHandlerTest extends TransformationHandlerTest {

  // handler is stateless - we're ok with single shared instance for all tests
  private final FromMom10TransformationHandler handler = new FromMom10TransformationHandler(naksha);

  @Test
  void shouldDropMetaFromFeatures() throws NoCursor {
    // Given
    List<XyzFeature> inputFeaturesWithMeta = new ArrayList<>();
    List<XyzFeature> expectedFeaturesWithoutMeta = new ArrayList<>();
    samplesWithModelVersion("10.0.0").forEach(sample -> {
      inputFeaturesWithMeta.add(sample.transformed());
      expectedFeaturesWithoutMeta.add(sample.original());
    });

    // And
    IEvent event = event(new TransformFromMom10Request(inputFeaturesWithMeta));

    // When
    Result result = handler.processEvent(event);

    // Then
    FromMom10 success = assertInstanceOf(TransformationSuccess.FromMom10.class, result);

    // And
    List<XyzFeature> handledFeatures = ResultHelper.readFeaturesFromResult(success, XyzFeature.class);
    assertEquals(expectedFeaturesWithoutMeta.size(), handledFeatures.size());
    for (int i = 0; i < expectedFeaturesWithoutMeta.size(); i++) {
      assertFeaturesEqual(expectedFeaturesWithoutMeta.get(i), handledFeatures.get(i));
    }
  }

  @ParameterizedTest
  @MethodSource("invalidRequests")
  void shouldFailOnInvalidRequestType(Request invalidRequest) {
    // Given: Event with unexpected Request type
    IEvent event = event(invalidRequest);

    // When: handling unexpected event
    Result result = handler.processEvent(event);

    // Then: Not Implemented Error Result is returned
    ErrorResult errorResult = assertInstanceOf(ErrorResult.class, result);
    assertEquals(XyzError.NOT_IMPLEMENTED, errorResult.reason);
  }

  @ParameterizedTest
  @MethodSource("nullAndEmptyList")
  void shouldSucceedOnEmptyRequest(List<XyzFeature> noFeatures) throws NoCursor {
    // Given: Legal event without any features
    IEvent event = event(new TransformFromMom10Request(noFeatures));

    // When: handling empty event
    Result result = handler.processEvent(event);

    // Then: success without any features is returned
    FromMom10 success = assertInstanceOf(TransformationSuccess.FromMom10.class, result);
    assertFalse(success.getXyzFeatureCursor().hasNext());
  }

  @Test
  void shouldFailWhenVersionTooLow() {
    // Given:
    List<XyzFeature> featuresWithVersionTooLow = samplesWithModelVersion("9.0.0").map(TransformationSample::transformed).toList();
    IEvent event = event(new TransformFromMom10Request(featuresWithVersionTooLow));

    // When: handling empty event
    Result result = handler.processEvent(event);

    // Then: success without any features is returned
    ErrorResult errorResult = assertInstanceOf(ErrorResult.class, result);
    assertEquals(XyzError.ILLEGAL_ARGUMENT, errorResult.reason);
  }

  private static Stream<Request<?>> invalidRequests() {
    return Stream.concat(
        commonInvalidRequests(),
        Stream.of(new TransformToMom10Request(randomFeatures()))
    );
  }
}
