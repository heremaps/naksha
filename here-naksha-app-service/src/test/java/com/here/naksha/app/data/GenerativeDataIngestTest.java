package com.here.naksha.app.data;

import com.here.naksha.app.data.GenerativeDataIngest.TopologyFeatureGenerator;
import com.here.naksha.lib.core.models.geojson.WebMercatorTile;
import java.util.Random;
import naksha.geo.ProxyGeoUtil;
import naksha.model.objects.NakshaFeature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.prep.PreparedGeometry;

class GenerativeDataIngestTest {

  @ParameterizedTest
  @ValueSource(strings = {"12201213", "12201302", "12201303"})
  void shouldGenerateGeometryMatchingTile(String tileId) {
    // Given:
    PreparedGeometry tilePolygon = WebMercatorTile.forQuadkey(tileId).getAsPolygon();

    // And:
    TopologyFeatureGenerator generator = new TopologyFeatureGenerator(new Random());

    // When:
    NakshaFeature feature = generator.randomFeatureForTile(tileId);

    // Then
    Assertions.assertTrue(tilePolygon.containsProperly(ProxyGeoUtil.toJtsGeometry(feature.getGeometry())));
  }
}