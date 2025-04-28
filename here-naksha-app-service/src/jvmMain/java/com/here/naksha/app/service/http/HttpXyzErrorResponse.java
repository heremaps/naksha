package com.here.naksha.app.service.http;

import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

import com.here.naksha.lib.core.models.payload.XyzResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import naksha.model.NakshaError;
import org.jetbrains.annotations.NotNull;

public class HttpXyzErrorResponse extends XyzResponse {

  private static final String TYPE_KEY = "type";
  private static final String TYPE = "ErrorResponse";
  private static final String ERROR_CODE_KEY = "error";
  private static final String ERROR_MESSAGE_KEY = "errorMessage";

  public static HttpXyzErrorResponse httpErrorResponse(@NotNull String errorCode, @NotNull String message, @NotNull String streamId) {
    HttpXyzErrorResponse errorResponse = new HttpXyzErrorResponse();
    errorResponse.setErrorCode(errorCode);
    errorResponse.setErrorMessage(message);
    errorResponse.setStreamId(streamId);
    errorResponse.put(TYPE_KEY, TYPE);
    return errorResponse;
  }

  public static HttpXyzErrorResponse httpErrorResponse(@NotNull NakshaError error, @NotNull String streamId) {
    return httpErrorResponse(error.getCode(), error.getMsg(), streamId);
  }

  public void setErrorCode(String errorCode) {
    setRaw(ERROR_CODE_KEY, errorCode);
  }

  public String getErrorCode() {
    return (String) getRaw(ERROR_CODE_KEY);
  }

  public void setErrorMessage(String errorMessage) {
    setRaw(ERROR_MESSAGE_KEY, errorMessage);
  }

  public String getErrorMessage() {
    return (String) getRaw(ERROR_MESSAGE_KEY);
  }

  private @NotNull HttpResponseStatus getHttpStatus() {
    String errorCode = getErrorCode();
    switch (errorCode) {
      case NakshaError.EXCEPTION -> {
        return HttpResponseStatus.INTERNAL_SERVER_ERROR;
      }
      case NakshaError.NOT_IMPLEMENTED -> {
        return HttpResponseStatus.NOT_IMPLEMENTED;
      }
      case NakshaError.ILLEGAL_ARGUMENT -> {
        return HttpResponseStatus.BAD_REQUEST;
      }
      case NakshaError.PAYLOAD_TOO_LARGE -> {
        return HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
      }
      case NakshaError.BAD_GATEWAY -> {
        return HttpResponseStatus.BAD_GATEWAY;
      }
      case NakshaError.CONFLICT -> {
        return HttpResponseStatus.CONFLICT;
      }
      case NakshaError.UNAUTHORIZED -> {
        return HttpResponseStatus.UNAUTHORIZED;
      }
      case NakshaError.FORBIDDEN -> {
        return HttpResponseStatus.FORBIDDEN;
      }
      case NakshaError.TOO_MANY_REQUESTS -> {
        return HttpResponseStatus.TOO_MANY_REQUESTS;
      }
      case NakshaError.TIMEOUT -> {
        return HttpResponseStatus.GATEWAY_TIMEOUT;
      }
      case NakshaError.NOT_FOUND -> {
        return NOT_FOUND;
      }
    }
    throw new IllegalArgumentException("Unknown error, unable to map to http status: " + errorCode);
  }
}
