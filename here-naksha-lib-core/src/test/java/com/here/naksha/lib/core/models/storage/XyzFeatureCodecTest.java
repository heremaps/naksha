package com.here.naksha.lib.core.models.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.here.naksha.lib.core.SessionTest;
import com.here.naksha.lib.core.models.geojson.implementation.EXyzAction;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzPoint;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.jbon.JbSession;
import com.here.naksha.lib.jbon.JvmEnv;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

public class XyzFeatureCodecTest extends SessionTest {

  @Test
  void shouldSuccessfullyDecodeAndEncodeFeature() {
    // given
    XyzFeature feature = new XyzFeature("ID");
    feature.setGeometry(new XyzPoint(1, 2));
    XyzProperties properties = new XyzProperties();
    properties.put("any", "123");
    properties.getXyzNamespace().setUuid("uuid");
    properties.getXyzNamespace().setAuthor("author");
    properties.getXyzNamespace().setAppId("app_id");
    feature.setProperties(properties);

    XyzFeatureCodec codec = new XyzFeatureCodec();

    // when
    codec.setFeature(feature);
    codec.decodeParts(true);
    codec.encodeFeature(true);

    // then
    XyzFeature restoredFeature = codec.feature;
    assertEquals(feature.getId(), restoredFeature.getId());
    assertEquals("123", restoredFeature.getProperties().get("any"));
    assertEquals(feature.getGeometry().getJTSGeometry(), restoredFeature.getGeometry().getJTSGeometry());
  }

  @Test
  void shouldDecodeAndEncodeXyzNamespace() {
    // given
    XyzFeature feature = new XyzFeature("ID");
    XyzProperties properties = new XyzProperties();
    feature.setProperties(properties);

    XyzNamespace xyzNs = new XyzNamespace();
    xyzNs.setTags(List.of("tag1", "tag2:true", "tag3=1"), false);
    xyzNs.setVersion(123);
    xyzNs.setTxn(333);
    xyzNs.setStreamId("stream-1");
    xyzNs.setUpdatedAt(Instant.now().toEpochMilli());
    xyzNs.setCreatedAt(Instant.now().toEpochMilli());
    xyzNs.setAuthor("here");
    xyzNs.setAppId("here_app");
    xyzNs.setAction(EXyzAction.CREATE);
    xyzNs.setExtend(7);
    xyzNs.setPuuid("puuid");
    xyzNs.setUuid("uuid");

    properties.setXyzNamespace(xyzNs);

    XyzFeatureCodec codec = new XyzFeatureCodec();

    // when
    codec.setFeature(feature);
    codec.decodeParts(true);
    codec.encodeFeature(true);

    // then
    XyzNamespace restoredXyz = codec.getFeature().xyz();

    assertEquals(xyzNs.getTxn(), restoredXyz.getTxn());
    assertEquals(xyzNs.getVersion(), restoredXyz.getVersion());
    assertEquals(xyzNs.getUpdatedAt(), restoredXyz.getUpdatedAt());
    assertEquals(xyzNs.getCreatedAt(), restoredXyz.getCreatedAt());
    assertEquals(xyzNs.getAuthor(), restoredXyz.getAuthor());
    assertEquals(xyzNs.getAppId(), restoredXyz.getAppId());
    assertEquals(xyzNs.getAction(), restoredXyz.getAction());
    assertEquals(xyzNs.getExtend(), restoredXyz.getExtend());
    assertEquals(xyzNs.getPuuid(), restoredXyz.getPuuid());
    assertEquals(xyzNs.getUuid(), restoredXyz.getUuid());
    assertEquals(xyzNs.getTags(), restoredXyz.getTags());
  }
}
