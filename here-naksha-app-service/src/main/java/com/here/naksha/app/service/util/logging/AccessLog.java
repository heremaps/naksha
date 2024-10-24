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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.here.naksha.lib.core.util.StreamInfo;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@JsonPropertyOrder({"time"})
@JsonInclude(Include.ALWAYS)
public class AccessLog extends AccessLogExtended implements JsonSerializable {
  public ClientInfo clientInfo;
  public RequestInfo reqInfo;
  public ResponseInfo respInfo;
  public StreamInfo streamInfo;

  public AccessLog() {
    super();
    clientInfo = new ClientInfo();
    reqInfo = new RequestInfo();
    respInfo = new ResponseInfo();
    streamInfo = new StreamInfo();
  }

  public void end() {
    super.end();
    timeWithoutStorageMs = ms - streamInfo.getTimeInStorageMs();
  }

  @JsonInclude(Include.ALWAYS)
  public static class RequestInfo extends RequestInfoExtended {
    public String method;
    public String uri;
    public String contentType;
    public String accept;
    public long size;
    public String referer;
    public String origin;
  }

  @JsonInclude(Include.ALWAYS)
  public static class ResponseInfo {
    public long statusCode;
    public String statusMsg;
    public long size;
    public String contentType;
  }

  @JsonInclude(Include.ALWAYS)
  public static class ClientInfo {
    public String remoteAddress;
    public String ip;
    public String userAgent;
    public String realm;
    public String userId;
    public String appId;
  }
}

@JsonInclude(Include.ALWAYS)
class AccessLogExtended {
  private static DateTimeFormatter dtFormatter =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss,SSS").withZone(ZoneId.of("UTC"));
  private long start;
  public String t = "STREAM";
  public String src;
  public String streamId;
  public long unixtime;
  public String time;
  public long ns;
  public long ms;
  public long timeWithoutStorageMs = 0;

  public AccessLogExtended() {
    start = System.nanoTime();
  }

  public void end() {
    final Instant now = Instant.now();
    long end = System.nanoTime();

    unixtime = now.toEpochMilli();
    time = dtFormatter.format(now);
    ns = end - start;
    ms = ns / 1000 / 1000;
  }
}

@JsonInclude(Include.ALWAYS)
class RequestInfoExtended {
  public String contentType;
  public String accept;
}
