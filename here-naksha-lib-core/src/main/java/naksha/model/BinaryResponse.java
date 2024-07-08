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
package naksha.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.flatbuffers.FlatBufferBuilder;
import com.here.naksha.lib.core.bin.ConnectorPayload;
import com.here.naksha.lib.core.util.Hasher;
import com.here.naksha.lib.core.view.ViewSerialize;
import java.nio.ByteBuffer;
import naksha.model.response.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A wrapper class which is based on {@link Response} for binary responses from connectors.
 * Internally it uses an actual binary representation for the payload.
 *
 * <p>An instance of {@link ConnectorPayload} will be used internally to convert it to binary form.
 * For all other protocol versions the payload will be encoded as JSON.
 *
 */
public class BinaryResponse extends Response {

  @JsonCreator
  public BinaryResponse(@JsonProperty byte @NotNull [] bytes, @JsonProperty @NotNull String mimeType) {
    super(BINARY_TYPE);
    this.bytes = bytes;
    this.mimeType = mimeType;
    setCalculatedEtag("\"" + Hasher.getHash(bytes) + "\"");
  }

  public static final String BINARY_SUPPORT_VERSION = "0.6.0";

  @JsonProperty
  private final @NotNull String mimeType;

  @JsonProperty
  private final byte @NotNull [] bytes;

  @JsonIgnore
  private boolean etagNeedsRecalculation;

  @JsonIgnore
  private @Nullable String calculatedEtag;

  public @NotNull String getMimeType() {
    return mimeType;
  }

  public byte @NotNull [] getBytes() {
    return bytes;
  }

  public @Nullable String getEtag() {
    if (etagNeedsRecalculation) {
      setCalculatedEtag("\"" + Hasher.getHash(bytes) + "\"");
    }
    return calculatedEtag;
  }

  @JsonIgnore
  private void setCalculatedEtag(@Nullable String etag) {
    calculatedEtag = etag;
    etagNeedsRecalculation = false;
  }

  public byte @NotNull [] toByteArray(@Nullable Class<? extends ViewSerialize> viewClass) {
    return toByteArray();
  }

  public byte @NotNull [] toByteArray() {
    FlatBufferBuilder builder = new FlatBufferBuilder();
    int payload = ConnectorPayload.createConnectorPayload(
        builder,
        builder.createString(getMimeType()),
        builder.createString(getEtag()),
        builder.createByteVector(getBytes()));
    builder.finish(payload);
    return buffer2ByteArray(builder.dataBuffer());
  }

  /**
   * Deserializes a binary response from the connector.
   *
   * @param byteArray The bytes coming in from a connector
   * @return The binary response.
   */
  public static @NotNull BinaryResponse fromByteArray(byte @NotNull [] byteArray) {
    final ConnectorPayload payload = ConnectorPayload.getRootAsConnectorPayload(ByteBuffer.wrap(byteArray));
    final ByteBuffer byteBuffer = payload.bytesAsByteBuffer();
    final byte[] bytes = buffer2ByteArray(byteBuffer);
    final String mimeType = payload.mimeType();
    assert mimeType != null;
    final BinaryResponse binaryResponse = new BinaryResponse(bytes, mimeType);
    binaryResponse.setEtag(payload.etag());
    return binaryResponse;
  }

  private static byte @NotNull [] buffer2ByteArray(@NotNull ByteBuffer buffer) {
    byte[] byteArray = new byte[buffer.remaining()];
    buffer.get(byteArray);
    return byteArray;
  }

  /**
   * Set the e-tag (a hash above all features), when it was calculated.
   *
   * @param etag the e-tag, if null, the e-tag is removed.
   */
  @SuppressWarnings("WeakerAccess")
  public void setEtag(String etag) {
    this.calculatedEtag = etag;
  }
}
