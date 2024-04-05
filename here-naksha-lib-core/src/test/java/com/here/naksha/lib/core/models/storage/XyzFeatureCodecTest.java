package com.here.naksha.lib.core.models.storage;

import static com.here.naksha.lib.core.models.geojson.implementation.XyzProperties.XYZ_NAMESPACE;
import static com.here.naksha.lib.jbon.BigInt64Kt.BigInt64;
import static com.here.naksha.lib.jbon.ConstantsKt.newDataView;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.here.naksha.lib.core.SessionTest;
import com.here.naksha.lib.core.models.geojson.implementation.EXyzAction;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzPoint;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.jbon.JbDictManager;
import com.here.naksha.lib.jbon.JbFeature;
import com.here.naksha.lib.jbon.JbMap;
import com.here.naksha.lib.jbon.XyzBuilder;
import com.here.naksha.lib.jbon.XyzOp;
import com.here.naksha.lib.jbon.XyzTags;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class XyzFeatureCodecTest extends SessionTest {

  @Test
  void shouldEncodeFeature() {
    // given

    XyzFeatureCodec encoder = new XyzFeatureCodec();
    XyzFeature feature = new XyzFeature("ID");
    feature.setGeometry(new XyzPoint(1, 2));
    XyzProperties properties = new XyzProperties();
    properties.put("any", "123");
    feature.setProperties(properties);
    encoder.setFeature(feature);
    encoder.setOp("CREATE");
    encoder.decodeParts(true);

    XyzBuilder xyzBuilder = new XyzBuilder(newDataView(512), null);
    Instant now = Instant.now();
    byte[] jbonNs = xyzBuilder.buildXyzNs(
        BigInt64(now.toEpochMilli()),
        BigInt64(now.toEpochMilli()),
        BigInt64(2),
        (short) 0,
        1,
        BigInt64(now.toEpochMilli()),
        null,
        "uuid",
        "app_id",
        "author",
        11111
    );

    XyzFeatureCodec decoder = new XyzFeatureCodec();

    // when
    decoder.setFeatureBytes(encoder.getFeatureBytes());
    decoder.setXyzNsBytes(jbonNs);
    decoder.setGeometryBytes(encoder.getGeometryBytes());
    decoder.encodeFeature(true);

    // then
    XyzFeature restoredFeature = decoder.feature;
    assertEquals("123", restoredFeature.getProperties().get("any"));
    assertEquals(feature.getGeometry().getJTSGeometry(), restoredFeature.getGeometry().getJTSGeometry());
    XyzNamespace xyz = restoredFeature.xyz();
    assertEquals("app_id", xyz.getAppId());
    assertEquals("author", xyz.getAuthor());
    assertEquals("uuid", xyz.getUuid());
    assertEquals(now.toEpochMilli(), xyz.getCreatedAt());
    assertEquals(now.toEpochMilli(), xyz.getUpdatedAt());
    assertEquals(11111, xyz.getGrid());
    assertEquals(1, xyz.getVersion());
    assertEquals(EXyzAction.CREATE, xyz.getAction());
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldDecodeFeature() {
    // given
    XyzFeature feature = new XyzFeature("ID");
    XyzProperties properties = new XyzProperties();
    feature.setProperties(properties);
    feature.setGeometry(new XyzPoint(1, 2));

    XyzNamespace xyzNs = new XyzNamespace();
    List<String> requestedTags = List.of("tag1", "tag2:true", "tag3=1");
    xyzNs.setTags(requestedTags, false);
    xyzNs.setUuid("uuid");
    properties.setXyzNamespace(xyzNs);

    XyzFeatureCodec codec = new XyzFeatureCodec();

    // when
    codec.setFeature(feature);
    codec.setOp("CREATE");
    codec.decodeParts(true);

    // then
    JbDictManager dictManager = new JbDictManager();
    XyzTags jbTags = new XyzTags(dictManager).mapBytes(codec.getTagsBytes(), 0, codec.getTagsBytes().length);
    Object[] tags = jbTags.tagsArray();
    assertArrayEquals(requestedTags.toArray(), tags);

    JbFeature jbFeature = new JbFeature(dictManager).mapBytes(codec.getFeatureBytes(), 0, codec.getFeatureBytes().length);
    Map<String, Object> featureSentToDb = (Map<String, Object>) new JbMap().mapReader(jbFeature.getReader()).toIMap();

    // empty geometry
    assertNull(featureSentToDb.get("geometry"));
    // empty xyz
    assertEquals(null, ((Map<String, Object>) featureSentToDb.get("properties")).get(XYZ_NAMESPACE));
    assertNotNull(codec.getGeometryBytes());

    // verify operations
    XyzOp xyzOp = new XyzOp().mapBytes(codec.xyzOp, 0, codec.xyzOp.length);
    assertEquals(0, xyzOp.op());
    assertEquals("uuid", xyzOp.uuid());
    assertEquals("ID", xyzOp.id());
  }
}
