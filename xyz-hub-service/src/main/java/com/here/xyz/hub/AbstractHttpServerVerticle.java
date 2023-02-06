/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.xyz.hub;

import static com.here.xyz.hub.rest.Api.CLIENT_CLOSED_REQUEST;
import static com.here.xyz.hub.rest.Api.HeaderValues.APPLICATION_JSON;
import static com.here.xyz.hub.rest.Api.HeaderValues.STREAM_ID;
import static com.here.xyz.hub.rest.Api.HeaderValues.STREAM_INFO;
import static com.here.xyz.hub.rest.Api.HeaderValues.STRICT_TRANSPORT_SECURITY;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import static io.vertx.core.http.HttpHeaders.AUTHORIZATION;
import static io.vertx.core.http.HttpHeaders.CACHE_CONTROL;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static io.vertx.core.http.HttpHeaders.ETAG;
import static io.vertx.core.http.HttpHeaders.IF_MODIFIED_SINCE;
import static io.vertx.core.http.HttpHeaders.IF_NONE_MATCH;
import static io.vertx.core.http.HttpHeaders.USER_AGENT;
import static io.vertx.core.http.HttpMethod.DELETE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.OPTIONS;
import static io.vertx.core.http.HttpMethod.PATCH;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;

import com.here.xyz.config.ServiceConfig;
import com.here.xyz.hub.rest.Api;
import com.here.xyz.hub.rest.HttpException;
import com.here.xyz.hub.task.TaskPipeline;
import com.here.xyz.hub.util.logging.LogUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.validation.BadRequestException;
import io.vertx.ext.web.validation.BodyProcessorException;
import io.vertx.ext.web.validation.ParameterProcessorException;
import io.vertx.ext.web.validation.impl.ParameterLocation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AbstractHttpServerVerticle extends AbstractVerticle {

  public static final HttpServerOptions SERVER_OPTIONS = new HttpServerOptions()
      .setCompressionSupported(true)
      .setDecompressionSupported(true)
      .setHandle100ContinueAutomatically(true)
      .setTcpQuickAck(true)
      .setTcpFastOpen(true)
      .setMaxInitialLineLength(16 * 1024)
      .setIdleTimeout(300);
  public static final String STREAM_INFO_CTX_KEY = "streamInfo";

  private static final Logger logger = LogManager.getLogger();
  /**
   * The methods the client is allowed to use.
   */
  private final List<HttpMethod> allowMethods = Arrays.asList(OPTIONS, GET, POST, PUT, DELETE, PATCH);

  /**
   * The headers, which can be exposed as part of the response.
   */
  private final List<CharSequence> exposeHeaders = Arrays.asList(STREAM_ID, STREAM_INFO, ETAG);

  /**
   * The headers the client is allowed to send.
   */
  private final List<CharSequence> allowHeaders = Arrays.asList(
      AUTHORIZATION, CONTENT_TYPE, USER_AGENT, IF_MODIFIED_SINCE, IF_NONE_MATCH, CACHE_CONTROL, STREAM_ID
  );

  public Future<Void> createHttpServer(int port, Router router) {
    Promise<Void> promise = Promise.promise();

    vertx.createHttpServer(SERVER_OPTIONS)
        .requestHandler(router)
        .listen(port, result -> {
          if (result.succeeded()) {
            logger.info("HTTP Server started on port {}", port);
            promise.complete();
          } else {
            logger.error("An error occurred, during the initialization of the server.", result.cause());
            promise.fail(result.cause());
          }
        });

    return promise.future();
  }

  /**
   * Add default handlers.
   * <p>
   * Call this method after all other routes are defined.
   *
   * @param router
   */
  protected void addDefaultHandlers(Router router) {
    //Add additional handler to the router
    router.route().failureHandler(this::failureHandler);
    // starts at the 2nd route, since the first one is automatically added from openapi's RouterBuilder.createRouter
    router.route().order(1)
        .handler(this::receiveHandler)
        .handler(this::maxRequestSizeHandler)
        .handler(createCorsHandler());
    //Default NotFound handler
    router.route().last().handler(createNotFoundHandler());
  }

  /**
   * The final response handler.
   */
  protected void onResponseEnd(RoutingContext context) {
    final Marker marker = Api.Context.getMarker(context);
    if (!context.response().headWritten()) {
      //The response was closed (e.g. by the client) before it could be written
      logger.info(marker, "The request was cancelled. No response has been sent.");
      onRequestCancelled(context);
    }
    logger.info(marker, "{}", LogUtil.responseToLogEntry(context));
    LogUtil.addResponseInfo(context).end();
    LogUtil.writeAccessLog(context);
  }

  protected void onRequestCancelled(RoutingContext context) {
    context.response().setStatusCode(CLIENT_CLOSED_REQUEST.code());
    context.response().setStatusMessage(CLIENT_CLOSED_REQUEST.reasonPhrase());
  }

  protected void failureHandler(@NotNull RoutingContext context) {
    String message = "A failure occurred during the execution.";
    if (context.failure() != null) {
      Throwable t = context.failure();
      if (t instanceof io.vertx.ext.web.handler.HttpException) {
        //Transform Vert.x HTTP exception into ours
        HttpResponseStatus status = HttpResponseStatus.valueOf(((io.vertx.ext.web.handler.HttpException) t).getStatusCode());
        if (status == UNAUTHORIZED) {
          message = "Missing auth credentials.";
        }
        t = new HttpException(status, message, t);
      }
      if (t instanceof BodyProcessorException) {
        sendErrorResponse(context, new HttpException(BAD_REQUEST, "Failed to parse body."));
        Buffer bodyBuffer = context.getBody();
        String body = null;
        if (bodyBuffer != null) {
          String bodyString = bodyBuffer.toString();
          body = bodyString.substring(0, Math.min(4096, bodyString.length()));
        }

        logger.warn("Exception processing body: {}. Body was: {}", t.getMessage(), body);
      } else if (t instanceof ParameterProcessorException) {
        ParameterLocation location = ((ParameterProcessorException) t).getLocation();
        String paramName = ((ParameterProcessorException) t).getParameterName();
        sendErrorResponse(context, new HttpException(BAD_REQUEST, "Invalid request input parameter value for "
            + location.name().toLowerCase() + "-parameter \"" + location.lowerCaseIfNeeded(paramName) + "\". Reason: "
            + ((ParameterProcessorException) t).getErrorType()));
      } else if (t instanceof BadRequestException) {
        sendErrorResponse(context, new HttpException(BAD_REQUEST, "Invalid request."));
      } else {
        sendErrorResponse(context, t);
      }
    } else {
      HttpResponseStatus status = context.statusCode() >= 400 ? HttpResponseStatus.valueOf(context.statusCode()) : INTERNAL_SERVER_ERROR;
      sendErrorResponse(context, new HttpException(status, message));
    }
  }

  /**
   * The default NOT FOUND handler.
   */
  protected Handler<RoutingContext> createNotFoundHandler() {
    return context -> sendErrorResponse(context, new HttpException(NOT_FOUND, "The requested resource does not exist."));
  }

  /**
   * The max request size handler.
   */
  protected void maxRequestSizeHandler(final @NotNull RoutingContext context) {
    final ServiceConfig config = Service.get().config;
    long limit = config.MAX_UNCOMPRESSED_REQUEST_SIZE;

    String errorMessage = "The request payload is bigger than the maximum allowed.";
    String uploadLimit;
    HttpResponseStatus status = REQUEST_ENTITY_TOO_LARGE;

    if (config.UPLOAD_LIMIT_HEADER_NAME != null
        && (uploadLimit = context.request().headers().get(config.UPLOAD_LIMIT_HEADER_NAME)) != null) {

      try {
        /** Override limit if we are receiving an UPLOAD_LIMIT_HEADER_NAME value */
        limit = Long.parseLong(uploadLimit);

        /** Add limit to streamInfo response header */
        setStreamInfo(context, "MaxReqSize", limit);
      } catch (NumberFormatException e) {
        sendErrorResponse(context,
            new HttpException(BAD_REQUEST, "Value of header: " + config.UPLOAD_LIMIT_HEADER_NAME + " has to be a number."));
        return;
      }

      /** Override http response code if its configured */
      if (config.UPLOAD_LIMIT_REACHED_HTTP_CODE > 0) {
        status = HttpResponseStatus.valueOf(config.UPLOAD_LIMIT_REACHED_HTTP_CODE);
      }

      /** Override error Message if its configured */
      if (config.UPLOAD_LIMIT_REACHED_MESSAGE != null) {
        errorMessage = config.UPLOAD_LIMIT_REACHED_MESSAGE;
      }
    }

    if (limit > 0) {
      if (context.getBody() != null && context.getBody().length() > limit) {
        sendErrorResponse(context, new HttpException(status, errorMessage));
        return;
      }
    }
    context.next();
  }

  private static final long maxAge = TimeUnit.MINUTES.toSeconds(1);

  /**
   * The initial request handler.
   */
  protected void receiveHandler(final @NotNull RoutingContext context) {
    final ServiceConfig config = Service.get().config;
    if (context.request().getHeader(STREAM_ID) == null) {
      context.request().headers().add(STREAM_ID, RandomStringUtils.randomAlphanumeric(10));
    }

    // Log the request information.
    LogUtil.addRequestInfo(context);
    final HttpServerResponse response = context.response();
    response.putHeader(STREAM_ID, context.request().getHeader(STREAM_ID));
    response.putHeader(STRICT_TRANSPORT_SECURITY, "max-age=" + maxAge);
    response.endHandler(ar -> onResponseEnd(context));
    context.addHeadersEndHandler(v -> headersEndHandler(context, config.CUSTOM_STREAM_INFO_HEADER_NAME));
    context.next();
  }

  protected static void headersEndHandler(RoutingContext context, String customStreamInfoKey) {
    Map<String, Object> streamInfo;
    if (context != null && (streamInfo = context.get(STREAM_INFO_CTX_KEY)) != null) {
      String streamInfoValues = "";
      for (Entry<String, Object> e : streamInfo.entrySet()) {
        streamInfoValues += e.getKey() + "=" + e.getValue() + ";";
      }

      context.response().putHeader(STREAM_INFO, streamInfoValues);
      if (customStreamInfoKey != null) {
        context.response().putHeader(customStreamInfoKey, streamInfoValues);
      }
    }
  }

  /**
   * Creates and sends an error response to the client.
   */
  public static void sendErrorResponse(final RoutingContext context, Throwable exception) {
    //If the request was cancelled, neither a response has to be sent nor the error should be logged.
    if (exception instanceof TaskPipeline.PipelineCancelledException) {
      return;
    }
    if (exception instanceof IllegalStateException && exception.getMessage().startsWith("Request method must be one of")) {
      exception = new HttpException(METHOD_NOT_ALLOWED, exception.getMessage(), exception);
    }

    ErrorMessage error;

    try {
      final Marker marker = Api.Context.getMarker(context);

      error = new ErrorMessage(context, exception);
      if (error.statusCode == 500) {
        error.message = null;
        logger.error(marker, "Sending error response: {} {} {}", error.statusCode, error.reasonPhrase, exception);
        logger.error(marker, "Error:", exception);
      } else {
        logger.warn(marker, "Sending error response: {} {} {}", error.statusCode, error.reasonPhrase, exception);
        logger.warn(marker, "Error:", exception);
      }
    } catch (Exception e) {
      logger.error("Error {} while preparing error response {}", e, exception);
      logger.error("Error:", e);
      logger.error("Original error:", exception);
      error = new ErrorMessage();
    }

    context.response()
        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
        .setStatusCode(error.statusCode)
        .setStatusMessage(error.reasonPhrase)
        .end(Json.encode(error));
  }

  /**
   * Add support for cross origin requests.
   */
  protected @NotNull CorsHandler createCorsHandler() {
    final CorsHandler cors = CorsHandler.create(".*").allowCredentials(true);
    allowMethods.forEach(cors::allowedMethod);
    allowHeaders.stream().map(String::valueOf).forEach(cors::allowedHeader);
    exposeHeaders.stream().map(String::valueOf).forEach(cors::exposedHeader);
    return cors;
  }

  public static void setStreamInfo(@NotNull RoutingContext context, @NotNull String streamInfoKey, @Nullable Object streamInfoValue) {
    Object streamInfo = context.get(STREAM_INFO_CTX_KEY);
    if (!(streamInfo instanceof Map)) {
      context.put(STREAM_INFO_CTX_KEY, streamInfo = new HashMap<String, Object>());
    }
    //noinspection unchecked
    ((Map<String, Object>) streamInfo).put(streamInfoKey, streamInfoValue);
  }

  /**
   * Represents an error object response.
   */
  protected static class ErrorMessage {

    public String type = "error";
    public int statusCode = INTERNAL_SERVER_ERROR.code();
    public String reasonPhrase = INTERNAL_SERVER_ERROR.reasonPhrase();
    public String message;
    public String streamId;

    public ErrorMessage() {
    }

    public ErrorMessage(RoutingContext context, Throwable e) {
      Marker marker = Api.Context.getMarker(context);
      streamId = marker.getName();
      message = e.getMessage();
      if (e instanceof HttpException) {
        statusCode = ((HttpException) e).status.code();
        reasonPhrase = ((HttpException) e).status.reasonPhrase();
      } else if (e instanceof BadRequestException) {
        statusCode = BAD_REQUEST.code();
        reasonPhrase = BAD_REQUEST.reasonPhrase();
      }

      // The authentication providers do not pass the exception message
      if (statusCode == 401 && message == null) {
        message = "Access to this resource requires valid authentication credentials.";
      }
    }
  }
}
