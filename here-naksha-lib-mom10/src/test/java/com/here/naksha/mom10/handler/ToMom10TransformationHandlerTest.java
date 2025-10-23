package com.here.naksha.mom10.handler;

import static com.here.naksha.mom10.util.FeaturesAssertionUtil.assertFeaturesEqual;
import static org.junit.jupiter.api.Assertions.*;

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
import com.here.naksha.mom10.handler.TransformationSuccess.ToMom10;
import com.here.naksha.mom10.util.FeaturesAssertionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ToMom10TransformationHandlerTest extends TransformationHandlerTest {

  // handler is stateless - we're ok with single shared instance for all tests
  private final ToMom10TransformationHandler handler = new ToMom10TransformationHandler(naksha);

  @Test
  void shouldPopulateMetaInFeatures() throws NoCursor {
    // Given
    List<XyzFeature> preMom10Features = new ArrayList<>();
    List<XyzFeature> expectedFeaturesWithMeta = new ArrayList<>();
    samplesWithModelVersion("9.9.7").forEach(sample -> {
      preMom10Features.add(sample.original());
      expectedFeaturesWithMeta.add(sample.transformed());
    });

    // And
    IEvent event = event(new TransformToMom10Request(preMom10Features));

    // When
    Result result = handler.processEvent(event);

    // Then
    ToMom10 success = assertInstanceOf(TransformationSuccess.ToMom10.class, result);

    // And
    List<XyzFeature> handledFeatures = ResultHelper.readFeaturesFromResult(success, XyzFeature.class);
    assertEquals(expectedFeaturesWithMeta.size(), handledFeatures.size());
    for (int i = 0; i < expectedFeaturesWithMeta.size(); i++) {
      assertFeaturesEqual(expectedFeaturesWithMeta.get(i), handledFeatures.get(i));
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
    IEvent event = event(new TransformToMom10Request(noFeatures));

    // When: handling empty event
    Result result = handler.processEvent(event);

    // Then: success without any features is returned
    ToMom10 success = assertInstanceOf(TransformationSuccess.ToMom10.class, result);
    assertFalse(success.getXyzFeatureCursor().hasNext());
  }

  @Test
  void shouldFailWhenVersionTooHigh() {
    // Given:
    List<XyzFeature> featuresWithVersionTooHigh = samplesWithModelVersion("10.0.0").map(TransformationSample::transformed).toList();
    IEvent event = event(new TransformToMom10Request(featuresWithVersionTooHigh));

    // When: handling empty event
    Result result = handler.processEvent(event);

    // Then: success without any features is returned
    ErrorResult errorResult = assertInstanceOf(ErrorResult.class, result);
    assertEquals(XyzError.ILLEGAL_ARGUMENT, errorResult.reason);
  }

  private static Stream<Request<?>> invalidRequests() {
    return Stream.concat(
        commonInvalidRequests(),
        Stream.of(new TransformFromMom10Request(randomFeatures()))
    );
  }
}