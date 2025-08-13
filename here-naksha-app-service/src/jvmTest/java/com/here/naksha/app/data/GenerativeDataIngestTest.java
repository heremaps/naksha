package com.here.naksha.app.data;

import com.here.naksha.app.data.GenerativeDataIngest.TopologyFeatureGenerator;
import com.here.naksha.lib.core.models.geojson.WebMercatorTile;
import java.util.Random;
import naksha.geo.GeoUtil;
import naksha.model.objects.NakshaFeature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.prep.PreparedGeometry;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GenerativeDataIngestTest {

  @ParameterizedTest
  @ValueSource(strings = {"12201213", "12201302", "12201303"})
  void shouldGenerateGeometryMatchingTile(String tileId) {
    // Given:
    PreparedGeometry tilePolygon = WebMercatorTile.forQuadkey(tileId).getAsPolygon();

    // And:
    TopologyFeatureGenerator generator = new TopologyFeatureGenerator(new Random());

    // When:
    final var feature = generator.randomFeatureForTile(tileId);
    final var geometry = feature.getGeometry();
    assertNotNull(geometry);

    // Then
    Assertions.assertTrue(tilePolygon.containsProperly(GeoUtil.toJtsGeometry(feature.getGeometry())));
  }
}