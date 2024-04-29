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
package com.here.naksha.lib.core.models.storage;

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static com.here.naksha.lib.core.util.StringCache.string;
import static com.here.naksha.lib.jbon.ConstantsKt.XYZ_OP_CREATE;
import static com.here.naksha.lib.jbon.ConstantsKt.XYZ_OP_DELETE;
import static com.here.naksha.lib.jbon.ConstantsKt.XYZ_OP_PURGE;
import static com.here.naksha.lib.jbon.ConstantsKt.XYZ_OP_UPDATE;
import static com.here.naksha.lib.jbon.ConstantsKt.XYZ_OP_UPSERT;
import static com.here.naksha.lib.jbon.ConstantsKt.newDataView;

import com.here.naksha.lib.core.util.json.Json;
import com.here.naksha.lib.jbon.JbDict;
import com.here.naksha.lib.jbon.JbDictManager;
import com.here.naksha.lib.jbon.JbFeature;
import com.here.naksha.lib.jbon.JbMap;
import com.here.naksha.lib.jbon.JvmEnv;
import com.here.naksha.lib.jbon.XyzBuilder;
import com.here.naksha.lib.nak.Flags;
import com.here.naksha.lib.nak.GZip;
import java.util.Map;
import java.util.Objects;
import javax.annotation.concurrent.NotThreadSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.locationtech.jts.algorithm.Centroid;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;

/**
 * A codec that is able to encode a feature from its parts and to decode a feature into its parts. The implementation is not thread safe.
 *
 * @param <FEATURE> The feature type that can be processed by this codec.
 * @param <SELF>    The implementation type.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@NotThreadSafe
public abstract class FeatureCodec<FEATURE, SELF extends FeatureCodec<FEATURE, SELF>>
    extends FeatureBox<FEATURE, SELF> {

  public static final int GEO_TYPE_NULL = 0;
  public static final int GEO_TYPE_WKB = 1;
  public static final int GEO_TYPE_EWKB = 2;
  public static final int GEO_TYPE_TWKB = 3;

  /**
   * Tries to decode (disassemble) a feature set via {@link #setFeature(Object)} or {@link #withFeature(Object)} into its parts. Unless
   * decoding fails (raising an exception), the individual parts should thereafter be readable.
   *
   * <p>If the method raises an exception, the state is undefined and should be {@link #clear()}ed.
   *
   * @param force If {@code true}, re-encodes in any case; if false double decoding is avoided.
   * @return this.
   */
  public abstract @NotNull SELF decodeParts(boolean force);

  /**
   * Tries to encode (assemble) a new feature from the currently set parts. Unless encoding fails (raising an exception),
   * {@link #getFeature()} should return the new assembled feature.
   *
   * <p>If the method raises an exception, the state is undefined and should be {@link #clear()}ed.
   *
   * @param force If {@code true}, re-encodes in any case; if false double decoding is avoided.
   * @return this.
   */
  public abstract @NotNull SELF encodeFeature(boolean force);

  protected abstract Flags getDefaultFlags();

  /**
   * Copy all the values from other codec supplied as an argument. This is useful while iterating through in-memory based codec list,
   * without having to allocate memory with deep clone.
   *
   * @param otherCodec The other codec which is to be cloned
   * @return this.
   */
  public @NotNull SELF copy(@NotNull FeatureCodec<FEATURE, ?> otherCodec) {
    withParts(otherCodec);
    this.isDecoded = otherCodec.isEncoded;
    this.isEncoded = otherCodec.isEncoded;
    this.feature = otherCodec.feature;
    this.err = otherCodec.err;
    this.rawError = otherCodec.rawError;
    return self();
  }

  /**
   * Load the raw values (feature parts) for the given foreign codec into this code to re-encode.
   *
   * @param otherCodec The other codec from which to load the parts.
   * @return this.
   */
  public @NotNull SELF withParts(@NotNull FeatureCodec<?, ?> otherCodec) {
    op = otherCodec.op;
    id = otherCodec.id;
    uuid = otherCodec.uuid;
    xyzNsBytes = otherCodec.xyzNsBytes;
    tagsBytes = otherCodec.tagsBytes;
    featureBytes = otherCodec.featureBytes;
    geometryBytes = otherCodec.geometryBytes;
    flags = otherCodec.flags;
    geometry = otherCodec.geometry;
    err = otherCodec.err;
    rawError = otherCodec.rawError;
    return self();
  }

  /**
   * Returns the operation linked to the codec.
   *
   * @return the operation linked to the boxed feature.
   */
  public @Nullable String getOp() {
    return op;
  }

  /**
   * Sets the operation linked to the codec.
   *
   * @param op The operation to set.
   * @return The previously set operation.
   */
  public @Nullable String setOp(@Nullable CharSequence op) {
    final String old = this.op;
    this.op = string(op);
    return old;
  }

  /**
   * Sets the operation linked to the codec.
   *
   * @param op The operation to set.
   * @return this.
   */
  public @NotNull SELF withOp(@Nullable CharSequence op) {
    setOp(op);
    return self();
  }

  /**
   * Clears the feature codec, that means all parts, the decoder, the feature and the encoder.
   *
   * @return this.
   */
  @Override
  public @NotNull SELF clear() {
    isDecoded = false;
    isEncoded = false;
    err = null;
    feature = null;
    id = null;
    uuid = null;
    geometryBytes = null;
    flags = null;
    geometry = null;
    featureBytes = null;
    xyzNsBytes = null;
    tagsBytes = null;
    return self();
  }

  /**
   * Clears all feature parts and the decoder, but leaves the feature alive.
   *
   * @return this.
   */
  public @NotNull SELF clearParts() {
    isDecoded = false;
    err = null;
    id = null;
    uuid = null;
    featureBytes = null;
    xyzNsBytes = null;
    tagsBytes = null;
    geometryBytes = null;
    flags = null;
    geometry = null;
    return self();
  }

  /**
   * Clears the feature and the encoder, but leaves the parts alive.
   *
   * @return this.
   */
  public @NotNull SELF clearFeature() {
    isEncoded = false;
    err = null;
    feature = null;
    return self();
  }

  // common params
  /**
   * If the feature was decoded.
   */
  protected boolean isDecoded;

  /**
   * If the feature was encoded.
   */
  protected boolean isEncoded;

  /**
   * If the decoder or encoder have detected an error.
   */
  protected @Nullable CodecError err;

  /**
   * The operation to be applied.
   */
  protected @Nullable String op;

  /**
   * The {@code id} of the feature.
   */
  protected @Nullable String id;

  /**
   * The {@code uuid} of the feature.
   */
  protected @Nullable String uuid;

  /**
   * The <link href="https://libgeos.org/specifications/wkb/">Extended WKB</b> encoded geometry.
   */
  protected byte @Nullable [] geometryBytes;

  /**
   * The flags {@link Flags}.
   */
  protected @Nullable Flags flags = getDefaultFlags();

  /**
   * The JTS geometry build from the {@link #geometryBytes}.
   */
  protected @Nullable Geometry geometry;

  /**
   * The JBON of the feature.
   */
  protected byte[] featureBytes;

  // DB - request params
  /**
   * tags
   */
  protected byte[] tagsBytes;
  /**
   * XyzOp jbon we send to DB, we do not get it back from DB.
   */
  protected byte[] xyzOp;

  // DB - response params
  /**
   * XyzNamespace jbon, returned from DB, we do not pass it in DB request.
   */
  protected byte[] xyzNsBytes;
  /**
   * The JSON of the error.
   */
  protected @Nullable String rawError;

  /**
   * Sets the given geometry and clears the WKB.
   *
   * @param geometry The geometry to set.
   * @return The previously set geometry.
   */
  @SuppressWarnings("unchecked")
  public <G extends Geometry> @Nullable G setGeometry(@Nullable Geometry geometry) {
    final Geometry old = getGeometry();
    this.geometry = geometry;
    this.geometryBytes = null;
    this.flags = null;
    return (G) old;
  }

  /**
   * Sets the given geometry and clears the WKB.
   *
   * @param geometry The geometry to load.
   * @return this.
   */
  public @NotNull SELF withGeometry(@Nullable Geometry geometry) {
    this.geometry = geometry;
    this.geometryBytes = null;
    this.flags = null;
    return self();
  }

  /**
   * Returns the decoded geometry.
   *
   * @param <G> The geometry-type being expected.
   * @return the decoded geometry.
   */
  @SuppressWarnings("unchecked")
  public <G extends Geometry> @Nullable G getGeometry() {
    if (geometry == null) {
      final byte[] wkb = this.geometryBytes;
      if (wkb != null && wkb.length > 0) {
        try (final Json jp = Json.get()) {
          this.geometry = jp.twkbReader.read(wkb);
        } catch (ParseException e) {
          throw unchecked(e);
        }
      }
    }
    return (G) geometry;
  }

  /**
   * Sets the WKB and clears the geometry.
   *
   * @param wkb The <link href="https://libgeos.org/specifications/wkb/">Extended WKB</b> encoded geometry.
   * @return The previously set WKB.
   */
  public byte @Nullable [] setGeometryBytes(byte @Nullable [] wkb) {
    final byte[] old = this.geometryBytes;
    this.geometry = null;
    this.geometryBytes = wkb;
    return old;
  }

  /**
   * Sets the WKB and clears the geometry.
   *
   * @param wkb The <link href="https://libgeos.org/specifications/wkb/">Extended WKB</b> encoded geometry.
   * @return this.
   */
  public @NotNull SELF withGeometryBytes(byte @Nullable [] wkb) {
    setGeometryBytes(wkb);
    return self();
  }

  /**
   * Returns the geometry encoded as <link href="https://libgeos.org/specifications/wkb/">Extended WKB</b>.
   *
   * @return The <link href="https://libgeos.org/specifications/wkb/">Extended WKB</b> encoded geometry.
   */
  public byte @Nullable [] getGeometryBytes() {
    if (geometryBytes == null) {
      final Geometry geometry = getGeometry();
      if (geometry != null) {
        try (final Json jp = Json.get()) {
          this.geometryBytes = jp.twkbWriter.write(geometry);
          if (flags == null) {
            this.flags = new Flags();
          }
          this.flags.setGeometryEncoding(GEO_TYPE_TWKB);
        }
      }
    }
    return geometryBytes;
  }

  /**
   * Type of binary format.
   *
   * @return
   */
  public @Nullable Flags getFlags() {
    return flags;
  }

  public void setFlags(@Nullable Flags flags) {
    this.flags = flags;
  }

  public @Nullable Integer getCombinedFlags() {
    if (flags == null) {
      return null;
    } else {
      return flags.toCombinedFlags();
    }
  }

  /**
   * Sets the JSON of the error.
   *
   * @param rawError The JSON to set.
   */
  public void setRawError(@Nullable String rawError) {
    this.rawError = rawError;
  }

  /**
   * Returns the {@code id} of the feature; if it has any.
   *
   * @return the {@code id} of the feature; if it has any.
   */
  public @Nullable String getId() {
    return id;
  }

  /**
   * Set the {@code id} of the feature. Often it is not necessary to set the {@code id}, because normally it is anyway encoded within the
   * JSON of the feature.
   *
   * @param id The {@code id} to set.
   * @return The previously set id.
   */
  public @Nullable String setId(@Nullable CharSequence id) {
    final String old = this.id;
    this.id = string(id);
    return old;
  }

  /**
   * Set the {@code id} of the feature. Often it is not necessary to set the {@code id}, because normally it is anyway encoded within the
   * JSON of the feature.
   *
   * @param id The {@code id} to set.
   * @return this.
   */
  public final @NotNull SELF withId(@Nullable CharSequence id) {
    setId(id);
    return self();
  }

  /**
   * Returns the {@code uuid} (unique state identifier) of the feature; if it has any.
   *
   * @return the {@code uuid} (unique state identifier) of the feature; if it has any.
   */
  public @Nullable String getUuid() {
    return uuid;
  }

  /**
   * Sets the {@code uuid} (unique state identifier) of the feature.
   *
   * @param uuid The new {@code uuid} to be set.
   * @return the previously set {@code uuid}.
   */
  public @Nullable String setUuid(@Nullable CharSequence uuid) {
    final String old = this.uuid;
    this.uuid = string(uuid);
    return old;
  }

  /**
   * Sets the {@code uuid} (unique state identifier) of the feature.
   *
   * @param uuid The new {@code uuid} to be set.
   * @return this.
   */
  public final @NotNull SELF withUuid(@Nullable CharSequence uuid) {
    setUuid(uuid);
    return self();
  }

  /**
   * Tests if the codec has an error from the last encoding or decoding.
   *
   * @return {@code true} if the codec has an error; {@code false} otherwise.
   */
  public boolean hasError() {
    return err != null;
  }

  /**
   * Returns the encoder or decoder error, if any.
   *
   * @return the encoder or decoder error, if any.
   */
  public @Nullable CodecError getError() {
    return err;
  }

  /**
   * Sets decoded PSQL error to naksha friendly error.
   *
   * @param err
   */
  public void setErr(@Nullable CodecError err) {
    this.err = err;
  }

  /**
   * Returns encoded feature
   *
   * @return
   */
  public byte[] getFeatureBytes() {
    return featureBytes;
  }

  /**
   * Sets encoded feature.
   *
   * @param featureBytes
   */
  public void setFeatureBytes(byte[] featureBytes) {
    this.featureBytes = featureBytes;
  }

  /**
   * Returns XyzOp as jbon byte array.
   *
   * @return
   */
  public byte[] getXyzOp() {
    return xyzOp;
  }

  /**
   * Sets tags as jbon byte array.
   *
   * @param tagsBytes
   */
  public void setTagsBytes(byte[] tagsBytes) {
    this.tagsBytes = tagsBytes;
  }

  /**
   * Returns tags as jbon byte array.
   *
   * @return
   */
  public byte[] getTagsBytes() {
    return tagsBytes;
  }

  /**
   * Sets xyz as jbon byte array.
   *
   * @param xyzNsBytes
   */
  public void setXyzNsBytes(byte[] xyzNsBytes) {
    this.xyzNsBytes = xyzNsBytes;
  }

  /**
   * Returns tags as jbon byte array.
   *
   * @return
   */
  public byte[] getXyzNsBytes() {
    return xyzNsBytes;
  }

  @Override
  public @Nullable Object setFeature(@Nullable FEATURE feature) {
    clearFeature();
    isEncoded = true;
    return super.setFeature(feature);
  }

  public void decodeXyzOp(@Nullable JbDict globalDict) {
    XyzBuilder xyzBuilder = new XyzBuilder(newDataView(1024), globalDict);
    xyzOp = xyzBuilder.buildXyzOp(mapOperationToPerform(op), id, uuid, calculateGrid());
  }

  protected @Nullable Integer calculateGrid() {
    if (geometry != null && !geometry.isEmpty()) {
      Coordinate centroid = Centroid.getCentroid(geometry);
      //      return GeohashUtils.encodeLatLon(centroid.y, centroid.x, 14);
      // FIXME TODO use heretile method after merge
      return 0;
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  protected FEATURE getFeatureFromJbon(@NotNull Class<FEATURE> featureClass) {
    // FIXME use existing DictManager
    byte[] uncompressedFeatureBytes = featureBytes != null && flags.isFeatureEncodedWithGZip()
        ? GZip.INSTANCE.gunzip(featureBytes)
        : featureBytes;

    JbFeature jbFeature = new JbFeature(new JbDictManager())
        .mapBytes(uncompressedFeatureBytes, 0, uncompressedFeatureBytes.length);
    Map<String, Object> featureAsMap = (Map<String, Object>)
        new JbMap().mapReader(jbFeature.getReader()).toIMap();
    return JvmEnv.get().convert(featureAsMap, featureClass);
  }

  private int mapOperationToPerform(String action) {
    if (Objects.equals(EWriteOp.CREATE.value(), action)) {
      return XYZ_OP_CREATE;
    } else if (Objects.equals(EWriteOp.UPDATE.value(), action)) {
      return XYZ_OP_UPDATE;
    } else if (Objects.equals(EWriteOp.PUT.value(), action)) {
      return XYZ_OP_UPSERT;
    } else if (Objects.equals(EWriteOp.DELETE.value(), action)) {
      return XYZ_OP_DELETE;
    } else if (Objects.equals(EWriteOp.PURGE.value(), action)) {
      return XYZ_OP_PURGE;
    }
    throw new UnsupportedOperationException(String.format("Action type %s is not supported", action));
  }
}
