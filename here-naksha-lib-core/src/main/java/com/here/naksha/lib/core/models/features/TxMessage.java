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
package com.here.naksha.lib.core.models.features;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import naksha.model.NakshaVersion;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This signal is just a comment generated by the SQL client.
 */
@SuppressWarnings("unused")
@AvailableSince(NakshaVersion.v2_0_0)
@JsonTypeName(value = "TxMessage")
public class TxMessage extends TxSignal {

  @AvailableSince(NakshaVersion.v2_0_0)
  public static final String TEXT = "text";

  @AvailableSince(NakshaVersion.v2_0_0)
  public static final String JSON = "json";

  @AvailableSince(NakshaVersion.v2_0_0)
  public static final String ATTACHMENT = "attachment";

  /**
   * Create a new comment signal.
   *
   * @param id         the local identifier of the event.
   * @param storageId  the storage identifier.
   * @param collection the collection impacted.
   * @param txn        the transaction number.
   * @param text       the text of the message.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  @JsonCreator
  public TxMessage(
      @JsonProperty(ID) @NotNull String id,
      @JsonProperty(STORAGE_ID) @NotNull String storageId,
      @JsonProperty(COLLECTION) @NotNull String collection,
      @JsonProperty(XyzNamespace.TXN) @NotNull String txn,
      @JsonProperty(TEXT) @NotNull String text) {
    super(id, storageId, collection, txn);
    assert !id.equals(collection) && id.startsWith("msg:");
    this.text = text;
  }

  /**
   * The human-readable message.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  @JsonProperty(TEXT)
  private @NotNull String text;

  /**
   * The JSON details; if any.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  @JsonProperty(JSON)
  private @Nullable Object json;

  /**
   * A binary attachment; if any.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  @JsonProperty(ATTACHMENT)
  private byte @Nullable [] attachment;

  /**
   * Returns the text of the message.
   *
   * @return the text of the message.
   */
  public @NotNull String getText() {
    return text;
  }

  public void setText(@NotNull String text) {
    this.text = text;
  }

  /**
   * Returns the JSON object attached to the message; if any.
   *
   * @return the JSON object attached to the message; if any.
   */
  public @Nullable Object getJson() {
    return json;
  }

  public void setJson(@Nullable Object json) {
    this.json = json;
  }

  /**
   * Returns the binary attachment of the message; if any.
   *
   * @return the binary attachment of the message; if any.
   */
  public byte @Nullable [] getAttachment() {
    return attachment;
  }

  public void setAttachment(byte @Nullable [] attachment) {
    this.attachment = attachment;
  }
}
