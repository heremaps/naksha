/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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

import static naksha.base.JvmAnyObjectUtil.getOrCreateProperty;
import static naksha.base.JvmAnyObjectUtil.getProperty;
import static naksha.base.JvmAnyObjectUtil.getPropertyOrReturnDefault;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import naksha.base.AnyObject;
import naksha.base.Platform;
import naksha.model.StreamInfo;

public class AccessLog extends AccessLogExtended {

  private static final String CLIENT_INFO_KEY = "clientInfo";
  private static final String REQUEST_INFO_KEY = "reqInfo";
  private static final String RESPONSE_INFO_KEY = "respInfo";
  private static final String STREAM_INFO_KEY = "streamInfo";

  public ClientInfo getClientInfo() {
    return getOrCreateProperty(this, CLIENT_INFO_KEY, ClientInfo.class);
  }

  public void setClientInfo(ClientInfo clientInfo) {
    setRaw(CLIENT_INFO_KEY, clientInfo);
  }

  public RequestInfo getReqInfo() {
    return getOrCreateProperty(this, REQUEST_INFO_KEY, RequestInfo.class);
  }

  public void setReqInfo(RequestInfo reqInfo) {
    setRaw(REQUEST_INFO_KEY, reqInfo);
  }

  public ResponseInfo getRespInfo() {
    return getOrCreateProperty(this, RESPONSE_INFO_KEY, ResponseInfo.class);
  }

  public void setRespInfo(ResponseInfo respInfo) {
    setRaw(RESPONSE_INFO_KEY, respInfo);
  }

  public StreamInfo getStreamInfo() {
    return getOrCreateProperty(this, STREAM_INFO_KEY, StreamInfo.class);
  }

  public StreamInfo getStreamInfoOrCreateNewWithStreamId(String streamId) {
    return getOrCreateProperty(this, STREAM_INFO_KEY, StreamInfo.class, (o,k) -> new StreamInfo(streamId));
  }

  public void setStreamInfo(StreamInfo streamInfo) {
    setRaw(STREAM_INFO_KEY, streamInfo);
  }

  public void end() {
    super.end();
  }

  public static class RequestInfo extends RequestInfoExtended {

    private static final String METHOD_KEY = "method";
    private static final String URI_KEY = "uri";
    private static final String CONTENT_TYPE_KEY = "contentType";
    private static final String ACCEPT_KEY = "accept";
    private static final String REQUEST_SIZE_KEY = "size";
    private static final String REFERER_KEY = "referer";
    private static final String ORIGIN_KEY = "origin";

    public String getMethod() {
      return getProperty(this, METHOD_KEY, String.class);
    }

    public void setMethod(String method) {
      setRaw(METHOD_KEY, method);
    }

    public String getUri() {
      return getProperty(this, URI_KEY, String.class);
    }

    public void setUri(String uri) {
      setRaw(URI_KEY, uri);
    }

    public String getContentType() {
      return getProperty(this, CONTENT_TYPE_KEY, String.class);
    }

    public void setContentType(String contentType) {
      setRaw(CONTENT_TYPE_KEY, contentType);
    }

    public String getAccept() {
      return getProperty(this, ACCEPT_KEY, String.class);
    }

    public void setAccept(String accept) {
      setRaw(ACCEPT_KEY, accept);
    }

    public long getRequestSize() {
      return getProperty(this, REQUEST_SIZE_KEY, Long.class);
    }

    public void setRequestSize(long requestSize) {
      setRaw(REQUEST_SIZE_KEY, requestSize);
    }

    public String getReferer() {
      return getProperty(this, REFERER_KEY, String.class);
    }

    public void setReferer(String referer) {
      setRaw(REFERER_KEY, referer);
    }

    public String getOrigin() {
      return getProperty(this, ORIGIN_KEY, String.class);
    }

    public void setOrigin(String origin) {
      setRaw(ORIGIN_KEY, origin);
    }
  }

  public static class ResponseInfo extends AnyObject {

    private static final String STATUS_CODE_KEY = "statusCode";
    private static final String STATUS_MSG_KEY = "statusMsg";
    private static final String RESPONSE_SIZE_KEY = "size";
    private static final String CONTENT_TYPE_KEY = "contentType";

    public long getStatusCode() {
      return getProperty(this, STATUS_CODE_KEY, Long.class);
    }

    public void setStatusCode(long statusCode) {
      setRaw(STATUS_CODE_KEY, statusCode);
    }

    public String getStatusMsg() {
      return getProperty(this, STATUS_MSG_KEY, String.class);
    }

    public void setStatusMsg(String statusMsg) {
      setRaw(STATUS_MSG_KEY, statusMsg);
    }

    public long getResponseSize() {
      return getProperty(this, RESPONSE_SIZE_KEY, Long.class);
    }

    public void setResponseSize(long responseSize) {
      setRaw(RESPONSE_SIZE_KEY, responseSize);
    }

    public String getContentType() {
      return getProperty(this, CONTENT_TYPE_KEY, String.class);
    }

    public void setContentType(String contentType) {
      setRaw(CONTENT_TYPE_KEY, contentType);
    }
  }

  public static class ClientInfo extends AnyObject {

    private static final String REMOTE_ADDRESS_KEY = "remoteAddress";
    private static final String IP_KEY = "ip";
    private static final String USER_AGENT_KEY = "userAgent";
    private static final String REALM_KEY = "realm";
    private static final String USER_ID_KEY = "userId";
    private static final String APP_ID_KEY = "appId";

    public String getRemoteAddress() {
      return getProperty(this, REMOTE_ADDRESS_KEY, String.class);
    }

    public void setRemoteAddress(String remoteAddress) {
      setRaw(REMOTE_ADDRESS_KEY, remoteAddress);
    }

    public String getIp() {
      return getProperty(this, IP_KEY, String.class);
    }

    public void setIp(String ip) {
      setRaw(IP_KEY, ip);
    }

    public String getUserAgent() {
      return getProperty(this, USER_AGENT_KEY, String.class);
    }

    public void setUserAgent(String userAgent) {
      setRaw(USER_AGENT_KEY, userAgent);
    }

    public String getRealm() {
      return getProperty(this, REALM_KEY, String.class);
    }

    public void setRealm(String realm) {
      setRaw(REALM_KEY, realm);
    }

    public String getUserId() {
      return getProperty(this, USER_ID_KEY, String.class);
    }

    public void setUserId(String userId) {
      setRaw(USER_ID_KEY, userId);
    }

    public String getAppId() {
      return getProperty(this, APP_ID_KEY, String.class);
    }

    public void setAppId(String appId) {
      setRaw(APP_ID_KEY, appId);
    }
  }
}

class AccessLogExtended extends AnyObject {

  private static DateTimeFormatter dtFormatter =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss,SSS").withZone(ZoneId.of("UTC"));

  // needed for calculations
  private long start;

  // serializable field
  private static final String SRC_KEY = "src";
  private static final String STREAM_ID_KEY = "streamId";
  private static final String UNIX_TIME_KEY = "unixtime";
  private static final String TIME_KEY = "time";
  private static final String NS_KEY = "ns";
  private static final String MS_KEY = "ms";

  public AccessLogExtended() {
    start = System.nanoTime();
  }

  public void end() {
    final Instant now = Instant.now();
    long end = System.nanoTime();

    setUnixTime(now.toEpochMilli());
    setTime(dtFormatter.format(now));
    long ns = end - start;
    setNs(ns);
    setMs(ns / 1000 / 1000);
  }

  public String getSrc() {
    return getProperty(this, SRC_KEY, String.class);
  }

  public void setSrc(String src) {
    setRaw(SRC_KEY, src);
  }

  public String getStreamId() {
    return getProperty(this, STREAM_ID_KEY, String.class);
  }

  public void setStreamId(String streamId) {
    setRaw(STREAM_ID_KEY, streamId);
  }

  public long getUnixTime() {
    return getProperty(this, UNIX_TIME_KEY, Long.class);
  }

  public void setUnixTime(long unixTime) {
    setRaw(UNIX_TIME_KEY, unixTime);
  }

  public String getTime() {
    return getProperty(this, TIME_KEY, String.class);
  }

  public void setTime(String time) {
    setRaw(TIME_KEY, time);
  }

  public long getNs() {
    return getProperty(this, NS_KEY, Long.class);
  }

  public void setNs(long ns) {
    setRaw(NS_KEY, ns);
  }

  public long getMs() {
    return getProperty(this, MS_KEY, Long.class);
  }

  public void setMs(long ms) {
    setRaw(MS_KEY, ms);
  }
}

class RequestInfoExtended extends AnyObject {
  private static final String CONTENT_TYPE_KEY = "contentType";
  private static final String ACCEPT_KEY = "accept";

  public String getContentType() {
    return getProperty(this, CONTENT_TYPE_KEY, String.class);
  }

  public void setContentType(String contentType) {
    setRaw(CONTENT_TYPE_KEY, contentType);
  }

  public String getAccept() {
    return getProperty(this, ACCEPT_KEY, String.class);
  }

  public void setAccept(String accept) {
    setRaw(ACCEPT_KEY, accept);
  }
}
