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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The database code will send notifications in this format. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PsqlNotification {

  public static final String TXN = "txn";
  public static final String TXI = "txi";

  /** The channel on which notifications are send. */
  public static final String CHANNEL = "naksha:notifications";

  /** The transaction that happened. */
  @JsonProperty(TXN)
  public String txn;

  /** Unique transaction identifier. */
  @JsonProperty(TXI)
  public String txi;
}
