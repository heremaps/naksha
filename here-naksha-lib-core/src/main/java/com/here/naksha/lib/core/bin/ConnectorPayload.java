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
package com.here.naksha.lib.core.bin;

import com.google.flatbuffers.BaseVector;
import com.google.flatbuffers.ByteVector;
import com.google.flatbuffers.Constants;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Deprecated
@SuppressWarnings("unused")
public final class ConnectorPayload extends Table {
  public static void ValidateVersion() {
    Constants.FLATBUFFERS_24_3_25();
  }

  public static ConnectorPayload getRootAsConnectorPayload(ByteBuffer _bb) {
    return getRootAsConnectorPayload(_bb, new ConnectorPayload());
  }

  public static ConnectorPayload getRootAsConnectorPayload(ByteBuffer _bb, ConnectorPayload obj) {
    _bb.order(ByteOrder.LITTLE_ENDIAN);
    return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb));
  }

  public void __init(int _i, ByteBuffer _bb) {
    __reset(_i, _bb);
  }

  public ConnectorPayload __assign(int _i, ByteBuffer _bb) {
    __init(_i, _bb);
    return this;
  }

  public String mimeType() {
    int o = __offset(4);
    return o != 0 ? __string(o + bb_pos) : null;
  }

  public ByteBuffer mimeTypeAsByteBuffer() {
    return __vector_as_bytebuffer(4, 1);
  }

  public ByteBuffer mimeTypeInByteBuffer(ByteBuffer _bb) {
    return __vector_in_bytebuffer(_bb, 4, 1);
  }

  public String etag() {
    int o = __offset(6);
    return o != 0 ? __string(o + bb_pos) : null;
  }

  public ByteBuffer etagAsByteBuffer() {
    return __vector_as_bytebuffer(6, 1);
  }

  public ByteBuffer etagInByteBuffer(ByteBuffer _bb) {
    return __vector_in_bytebuffer(_bb, 6, 1);
  }

  public int bytes(int j) {
    int o = __offset(8);
    return o != 0 ? bb.get(__vector(o) + j * 1) & 0xFF : 0;
  }

  public int bytesLength() {
    int o = __offset(8);
    return o != 0 ? __vector_len(o) : 0;
  }

  public ByteVector bytesVector() {
    return bytesVector(new ByteVector());
  }

  public ByteVector bytesVector(ByteVector obj) {
    int o = __offset(8);
    return o != 0 ? obj.__assign(__vector(o), bb) : null;
  }

  public ByteBuffer bytesAsByteBuffer() {
    return __vector_as_bytebuffer(8, 1);
  }

  public ByteBuffer bytesInByteBuffer(ByteBuffer _bb) {
    return __vector_in_bytebuffer(_bb, 8, 1);
  }

  public static int createConnectorPayload(
      FlatBufferBuilder builder, int mime_typeOffset, int etagOffset, int bytesOffset) {
    builder.startTable(3);
    ConnectorPayload.addBytes(builder, bytesOffset);
    ConnectorPayload.addEtag(builder, etagOffset);
    ConnectorPayload.addMimeType(builder, mime_typeOffset);
    return ConnectorPayload.endConnectorPayload(builder);
  }

  public static void startConnectorPayload(FlatBufferBuilder builder) {
    builder.startTable(3);
  }

  public static void addMimeType(FlatBufferBuilder builder, int mimeTypeOffset) {
    builder.addOffset(0, mimeTypeOffset, 0);
  }

  public static void addEtag(FlatBufferBuilder builder, int etagOffset) {
    builder.addOffset(1, etagOffset, 0);
  }

  public static void addBytes(FlatBufferBuilder builder, int bytesOffset) {
    builder.addOffset(2, bytesOffset, 0);
  }

  public static int createBytesVector(FlatBufferBuilder builder, byte[] data) {
    return builder.createByteVector(data);
  }

  public static int createBytesVector(FlatBufferBuilder builder, ByteBuffer data) {
    return builder.createByteVector(data);
  }

  public static void startBytesVector(FlatBufferBuilder builder, int numElems) {
    builder.startVector(1, numElems, 1);
  }

  public static int endConnectorPayload(FlatBufferBuilder builder) {
    int o = builder.endTable();
    return o;
  }

  public static void finishConnectorPayloadBuffer(FlatBufferBuilder builder, int offset) {
    builder.finish(offset);
  }

  public static void finishSizePrefixedConnectorPayloadBuffer(FlatBufferBuilder builder, int offset) {
    builder.finishSizePrefixed(offset);
  }

  public static final class Vector extends BaseVector {
    public Vector __assign(int _vector, int _element_size, ByteBuffer _bb) {
      __reset(_vector, _element_size, _bb);
      return this;
    }

    public ConnectorPayload get(int j) {
      return get(new ConnectorPayload(), j);
    }

    public ConnectorPayload get(ConnectorPayload obj, int j) {
      return obj.__assign(__indirect(__element(j), bb), bb);
    }
  }
}
