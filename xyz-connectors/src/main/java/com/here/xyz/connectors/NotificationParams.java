/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.xyz.connectors;

import java.util.Map;

public class NotificationParams {

  public final Map<String, Object> params;
  public final Map<String, Object> connectorParams;
  public final String tid;
  public final String aid;
  public final String jwt;
  public final Map<String, Object> metadata;

  public NotificationParams(Map<String, Object> params, Map<String, Object> connectorParams, Map<String, Object> metadata, String tid, String aid, String jwt) {
    this.params = params;
    this.connectorParams = connectorParams;
    this.metadata = metadata;
    this.tid = tid;
    this.aid = aid;
    this.jwt = jwt;
  }
}
