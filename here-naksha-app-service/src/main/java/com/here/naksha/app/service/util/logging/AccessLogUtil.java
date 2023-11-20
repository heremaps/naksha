/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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
package com.here.naksha.app.service.util.logging;

import static io.vertx.core.http.HttpHeaders.*;
import static io.vertx.core.http.HttpMethod.*;

import com.here.naksha.app.service.http.auth.JWTPayload;
import com.here.naksha.lib.core.util.StreamInfo;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.RoutingContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

public class AccessLogUtil {

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AccessLogUtil.class);
  private static final String REALM = "rlm";

  private static final String JWT = "jwt";
  public static final String STREAM_INFO_CTX_KEY = "streamInfo";
  private static final String ACCESS_LOG = "accessLog";
  public static final String X_FORWARDED_FOR = "X-Forwarded-For";
  private static List<String> skipLoggingHeaders = Collections.singletonList(X_FORWARDED_FOR);

  private static final String IPV4_REGEX = "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$";
  private static final String IPV6_STD_REGEX = "^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$";
  private static final String IPV6_HEX_COMPRESSED_REGEX =
      "^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$";

  private static final Pattern IPV4_PATTERN = Pattern.compile(IPV4_REGEX);
  private static final Pattern IPV6_STD_PATTERN = Pattern.compile(IPV6_STD_REGEX);
  private static final Pattern IPV6_HEX_COMPRESSED_PATTERN = Pattern.compile(IPV6_HEX_COMPRESSED_REGEX);

  private static String getIp(RoutingContext context) {
    String ips = context.request().getHeader(X_FORWARDED_FOR);
    if (!StringUtils.isEmpty(ips)) {
      String ip = ips.split(", ")[0];

      if (IPV4_PATTERN.matcher(ip).matches()
          || IPV6_STD_PATTERN.matcher(ip).matches()
          || IPV6_HEX_COMPRESSED_PATTERN.matcher(ip).matches()) {
        return ip;
      }
    }

    return context.request().connection().remoteAddress().host();
  }

  private static void appendHeaders(MultiMap headers, StringBuilder buf) {
    for (Map.Entry<String, String> header : headers) {
      if (!skipLoggingHeaders.contains(header.getKey())) {
        buf.append(header.getKey());
        buf.append(" : ");
        buf.append(header.getValue());
        buf.append('\n');
      }
    }
  }

  private static @NotNull AccessLog getOrCreateAccessLog(final @NotNull RoutingContext context) {
    AccessLog accessLog = getAccessLog(context);
    if (accessLog == null) {
      accessLog = new AccessLog();
      context.put(ACCESS_LOG, accessLog);
    }
    return accessLog;
  }

  public static @Nullable AccessLog getAccessLog(final @Nullable RoutingContext context) {
    return (context == null) ? null : context.get(ACCESS_LOG);
  }

  public static @Nullable JWTPayload getOrCreateJWT(final @Nullable RoutingContext context) {
    if (context == null) {
      return null;
    }
    JWTPayload payload = context.get(JWT);
    if (payload == null && context.user() != null) {
      payload = DatabindCodec.mapper().convertValue(context.user().principal(), JWTPayload.class);
      context.put(JWT, payload);
    }

    return payload;
  }

  public static @Nullable StreamInfo getStreamInfo(final @Nullable RoutingContext context) {
    return (context == null) ? null : context.get(STREAM_INFO_CTX_KEY);
  }

  /**
   * Add the basic request information into the AccessLog.
   *
   * @param context the routing context.
   */
  public static void addRequestInfo(final @Nullable RoutingContext context) {
    if (context == null) return;
    final AccessLog accessLog = getOrCreateAccessLog(context);
    final HttpMethod method = context.request().method();
    accessLog.reqInfo.method = context.request().method().name();
    // Remove access_token part from uri for security concerns
    final String uri = context.request().uri();
    final int endPos = uri.indexOf("?");
    accessLog.reqInfo.uri = (endPos > 0) ? uri.substring(0, endPos) : uri;
    accessLog.reqInfo.referer = context.request().getHeader(REFERER);
    accessLog.reqInfo.origin = context.request().getHeader(ORIGIN);
    if (POST.equals(method) || PUT.equals(method) || PATCH.equals(method)) {
      accessLog.reqInfo.size = context.body() == null ? 0 : context.body().length();
    }
    accessLog.clientInfo.ip = getIp(context);
    accessLog.clientInfo.remoteAddress =
        context.request().connection().remoteAddress().toString();
    accessLog.clientInfo.userAgent = context.request().getHeader(USER_AGENT);
    accessLog.clientInfo.realm = context.request().getHeader(REALM);
    accessLog.reqInfo.contentType = context.request().getHeader(CONTENT_TYPE);
    accessLog.reqInfo.accept = context.request().getHeader(ACCEPT);

    context.put(STREAM_INFO_CTX_KEY, accessLog.streamInfo);
  }

  /**
   * Add the response information into the AccessLog object.
   * As the authentication is done after the request has been received, this method will as well add
   * the clientInfo to the request information. So, even while the clientInfo is part of the request
   * information, for technical reasons it's added together with the response information,
   * because the JWT token is processed after the {@link #addRequestInfo(RoutingContext)} was invoked
   * and therefore this method does not have the necessary information.
   *
   * @param context the routing context
   */
  public static @Nullable AccessLog addResponseInfo(final @Nullable RoutingContext context) {
    if (context == null) return null;
    final AccessLog accessLog = getAccessLog(context);
    if (accessLog == null) return null;
    accessLog.respInfo.statusCode = context.response().getStatusCode();
    accessLog.respInfo.statusMsg = context.response().getStatusMessage();
    accessLog.respInfo.size = context.response().bytesWritten();
    accessLog.respInfo.contentType = context.response().headers().get(CONTENT_TYPE);

    final JWTPayload tokenPayload = getOrCreateJWT(context);
    if (tokenPayload != null) {
      accessLog.clientInfo.userId = tokenPayload.aid;
      accessLog.clientInfo.appId = tokenPayload.cid;
    }
    return accessLog;
  }

  public static void writeAccessLog(final @Nullable RoutingContext context) {
    if (context == null) return;
    final AccessLog accessLog = getAccessLog(context);
    if (accessLog == null) return;

    logger.info(accessLog.serialize());

    // Log relevant details for generating API metrics
    final AccessLog.RequestInfo req = accessLog.reqInfo;
    final AccessLog.ResponseInfo res = accessLog.respInfo;
    final StreamInfo si = accessLog.streamInfo;
    logger.info(
        "[REST API stats => eventType,spaceId,storageId,method,uri,status,timeTakenMs,resSize] - RESTAPIStats {} {} {} {} {} {} {}",
        (si == null || si.getSpaceId() == null || si.getSpaceId().isEmpty()) ? "-" : si.getSpaceId(),
        (si == null || si.getStorageId() == null || si.getStorageId().isEmpty()) ? "-" : si.getStorageId(),
        req.method,
        req.uri,
        res.statusCode,
        accessLog.ms,
        res.size);
  }
}