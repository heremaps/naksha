package com.here.naksha.app.service.http;

import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

import io.netty.handler.codec.http.HttpResponseStatus;
import naksha.model.NakshaError;
import naksha.model.request.ErrorResponse;
import org.jetbrains.annotations.NotNull;

public class NakshaHttpErrorResponse extends ErrorResponse {

  private static final String STREAM_ID_KEY = "streamId";

  public static NakshaHttpErrorResponse httpErrorResponse(@NotNull String errorCode, @NotNull String message, @NotNull String streamId) {
    return httpErrorResponse(new NakshaError(errorCode, message), streamId)
  }

  public static NakshaHttpErrorResponse httpErrorResponse(@NotNull NakshaError error, @NotNull String streamId) {
    NakshaHttpErrorResponse errorResponse = new NakshaHttpErrorResponse();
    errorResponse.setError(error);
    errorResponse.setStreamId(streamId);
    return errorResponse;
  }

  public void setStreamId(@NotNull String streamId) {
    setRaw(STREAM_ID_KEY, streamId);
  }

  public String getStreamId() {
    return (String) getRaw(STREAM_ID_KEY);
  }

  private @NotNull HttpResponseStatus getHttpStatus() {
    String errorCode = getError().getCode();
    switch (errorCode) {
      case NakshaError.EXCEPTION -> { return HttpResponseStatus.INTERNAL_SERVER_ERROR; }
      case NakshaError.NOT_IMPLEMENTED -> { return HttpResponseStatus.NOT_IMPLEMENTED; }
      case NakshaError.ILLEGAL_ARGUMENT -> { return HttpResponseStatus.BAD_REQUEST; }
      case NakshaError.PAYLOAD_TOO_LARGE -> { return HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE; }
      case NakshaError.BAD_GATEWAY -> { return HttpResponseStatus.BAD_GATEWAY; }
      case NakshaError.CONFLICT -> { return HttpResponseStatus.CONFLICT; }
      case NakshaError.UNAUTHORIZED -> { return HttpResponseStatus.UNAUTHORIZED; }
      case NakshaError.FORBIDDEN -> { return HttpResponseStatus.FORBIDDEN; }
      case NakshaError.TOO_MANY_REQUESTS -> { return HttpResponseStatus.TOO_MANY_REQUESTS; }
      case NakshaError.TIMEOUT -> { return HttpResponseStatus.GATEWAY_TIMEOUT; }
      case NakshaError.NOT_FOUND -> { return NOT_FOUND; }
    }
    throw new IllegalArgumentException("Unknown error, unable to map to http status: " + errorCode);
  }
}
