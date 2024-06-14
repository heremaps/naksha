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
package com.here.naksha.lib.psql;

public class RequestedParams {

  final byte[][] features;
  final byte[][] tags;
  final byte[][] geo;

  public RequestedParams() {
    features = null;
    tags = null;
    geo = null;
  }

  public RequestedParams(byte[][] features, byte[][] tags, byte[][] geo) {
    this.features = features;
    this.tags = tags;
    this.geo = geo;
  }
}
