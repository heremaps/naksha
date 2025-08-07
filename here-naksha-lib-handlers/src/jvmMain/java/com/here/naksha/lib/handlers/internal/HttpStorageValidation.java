package com.here.naksha.lib.handlers.internal;

import com.here.naksha.storage.http.HttpStorageProperties;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;
import naksha.base.NakshaError;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import org.jetbrains.annotations.NotNull;

public class HttpStorageValidation {

  private HttpStorageValidation() {
  }

  private static final long MIN_HTTP_CONNECT_TIMEOUT_SEC = 0;
  private static final long MAX_HTTP_CONNECT_TIMEOUT_SEC = 30;

  private static final long MIN_HTTP_SOCKET_TIMEOUT_SEC = 0;
  private static final long MAX_HTTP_SOCKET_TIMEOUT_SEC = 90;

  private static final Set<String> ALLOWED_PROTOCOLS = Set.of("http", "https");

  static Response validateConfigForHttpStorage(NakshaStorage httpStorageConfig) {
    HttpStorageProperties httpStorageProperties;
    try {
      httpStorageProperties = httpStorageConfig.getProperties(HttpStorageProperties.TYPE);
    } catch (Exception exception) {
      return new ErrorResponse(
          NakshaError.ILLEGAL_ARGUMENT,
          "Unable to convert 'properties' to " + HttpStorageProperties.class.getName(),
          exception);
    }
    return httpStoragePropertiesValidation(httpStorageProperties);
  }

  private static Response httpStoragePropertiesValidation(HttpStorageProperties httpStorageProperties) {
    boolean isConnectionTimeoutValid = isBetween(
        httpStorageProperties.getConnectTimeout(), MIN_HTTP_CONNECT_TIMEOUT_SEC, MAX_HTTP_CONNECT_TIMEOUT_SEC);
    boolean isSocketTimeoutValid = isBetween(
        httpStorageProperties.getSocketTimeout(), MIN_HTTP_SOCKET_TIMEOUT_SEC, MAX_HTTP_SOCKET_TIMEOUT_SEC);
    boolean isUrlValid = isUrlValid(httpStorageProperties.getUrl());
    if (isConnectionTimeoutValid && isSocketTimeoutValid && isUrlValid) {
      return new SuccessResponse();
    }
    String errorMsg =
        getErrorMsg(httpStorageProperties, isConnectionTimeoutValid, isSocketTimeoutValid, isUrlValid);
    return new ErrorResponse(NakshaError.ILLEGAL_ARGUMENT, errorMsg);
  }

  @NotNull
  private static String getErrorMsg(
      HttpStorageProperties httpStorageProperties,
      boolean isConnectionTimeoutValid,
      boolean isSocketTimeoutValid,
      boolean isUrlValid) {
    ArrayList<String> errorMsgs = new ArrayList<>(3);
    if (!isConnectionTimeoutValid) {
      errorMsgs.add("Invalid connection timeout: %d, allowed values (sec): %d - %d"
          .formatted(
              httpStorageProperties.getConnectTimeout(),
              MIN_HTTP_CONNECT_TIMEOUT_SEC,
              MAX_HTTP_CONNECT_TIMEOUT_SEC));
    }
    if (!isSocketTimeoutValid) {
      errorMsgs.add("Invalid socket timeout: %d, allowed values (sec): %d - %d"
          .formatted(
              httpStorageProperties.getSocketTimeout(),
              MIN_HTTP_SOCKET_TIMEOUT_SEC,
              MAX_HTTP_SOCKET_TIMEOUT_SEC));
    }
    if (!isUrlValid) {
      errorMsgs.add("Invalid url: %s".formatted(httpStorageProperties.getUrl()));
    }
    return String.join("\n", errorMsgs);
  }

  private static boolean isBetween(long value, long min, long max) {
    return value >= min && value <= max;
  }

  private static boolean isUrlValid(String maybeUrl) {
    try {
      URL url = new URL(maybeUrl);
      return ALLOWED_PROTOCOLS.contains(url.getProtocol());
    } catch (MalformedURLException e) {
      return false;
    }
  }
}
