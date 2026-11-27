package com.here.naksha.app.service.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpResponseStatus;
import naksha.base.NakshaError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpXyzErrorResponseTest {
  @ParameterizedTest
  @ValueSource(strings = {NakshaError.FEATURE_EXISTS, NakshaError.COLLECTION_EXISTS, NakshaError.CATALOG_EXISTS, NakshaError.CONFLICT})
  void duplicateEntitiesKeepThePublicConflictContract(String code) {
    NakshaError original = new NakshaError(code, "already exists");
    HttpXyzErrorResponse response = HttpXyzErrorResponse.httpErrorResponse(original, "test-stream");
    assertEquals(NakshaError.CONFLICT, response.getErrorCode());
    assertEquals(HttpResponseStatus.CONFLICT, HttpXyzErrorResponse.mapErrorCodeToHttpStatus(code));
    assertEquals(HttpResponseStatus.CONFLICT, HttpXyzErrorResponse.mapErrorCodeToHttpStatus(response.getErrorCode()));
    assertEquals(code, original.getCode());
    assertEquals("already exists", response.getErrorMessage());
    assertEquals("test-stream", response.getStreamId());
  }

  @Test
  void illegalStateIsAnInternalErrorRatherThanAnErrorMappingException() {
    assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR,
        HttpXyzErrorResponse.mapErrorCodeToHttpStatus(NakshaError.ILLEGAL_STATE));
  }
}
