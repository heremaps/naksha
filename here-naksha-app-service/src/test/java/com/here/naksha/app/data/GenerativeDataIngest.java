package com.here.naksha.app.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.here.naksha.app.common.TestUtil;
import com.here.naksha.app.service.models.FeatureCollectionRequest;
import com.here.naksha.lib.core.models.geojson.WebMercatorTile;
import com.here.naksha.lib.core.models.geojson.coordinates.BBox;
import com.here.naksha.lib.core.models.geojson.coordinates.LineStringCoordinates;
import com.here.naksha.lib.core.models.geojson.coordinates.PointCoordinates;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzLineString;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenerativeDataIngest extends AbstractDataIngest {

  private static final Logger logger = LoggerFactory.getLogger(GenerativeDataIngest.class);

  private final int MAX_BATCH_SIZE = 100;

  private final List<String> tileIds;

  private final Random random;

  private final TopologyFeatureGenerator topologyFeatureGenerator;

  public GenerativeDataIngest(List<String> tileIds) {
    this.tileIds = tileIds;
    this.random = ThreadLocalRandom.current();
    this.topologyFeatureGenerator = new TopologyFeatureGenerator(random);
  }

  private void ingestRandomFeatures(int totalCount) throws URISyntaxException, IOException, InterruptedException {
    int ingestedCount = 0;
    String streamId = UUID.randomUUID().toString();
    while (ingestedCount < totalCount) {
      int batchSize = Math.min(MAX_BATCH_SIZE, totalCount - ingestedCount);
      String requestBody = batchRequest(batchSize);
      logger.info("Sending batch of {} features to Naksha", batchSize);
      sendFeaturesToNaksha(requestBody, streamId);
      ingestedCount += batchSize;
      logger.info("Ingested {} generated features, {} features left", ingestedCount, totalCount - ingestedCount);
    }
    logger.info("Ingestion of generated features ended, sent total of {} features", ingestedCount);
  }

  private void sendFeaturesToNaksha(String requestBody, String streamId) throws URISyntaxException, IOException, InterruptedException {
    final HttpResponse<String> response = nakshaClient.put(
        "hub/spaces/" + nhSpaceId + "/features?access_token=" + nhToken, requestBody, streamId);

    // Perform assertion
    assertEquals(
        200,
        response.statusCode(),
        "ResCode mismatch while importing batch with streamId" + streamId);
  }

  private String batchRequest(int size) {
    return new FeatureCollectionRequest()
        .withFeatures(randomFeatures(size))
        .serialize();
  }

  private List<XyzFeature> randomFeatures(int count) {
    return Stream.generate(this::randomFeature)
        .limit(count)
        .toList();
  }

  private XyzFeature randomFeature() {
    return topologyFeatureGenerator.randomFeatureForTile(randomTile());
  }

  private String randomTile() {
    return tileIds.get(random.nextInt(tileIds.size()));
  }

  static class TopologyFeatureGenerator {

    private static final String ID_PREFIX = "generated_feature_";
    private static final String SAMPLES_DIR = "src/test/resources/ingest_data/";
    private static final String BASE_JSON = "topology/sample_topology_feature.json";

    private static final AtomicLong COUNTER = new AtomicLong(0);
    private final Random random;
    private final XyzFeature baseFeature;

    public TopologyFeatureGenerator(Random random) {
      this.random = random;
      this.baseFeature = TestUtil.parseJsonFileOrFail(SAMPLES_DIR, BASE_JSON, XyzFeature.class);
    }

    XyzFeature randomFeatureForTile(String tileId) {
      XyzFeature generated = new XyzFeature(ID_PREFIX + COUNTER.incrementAndGet());
      generated.setProperties(baseFeature.getProperties());
      generated.setGeometry(randomLineInTile(tileId));
      generated.setBbox(null);
      return generated;
    }

    private XyzLineString randomLineInTile(String tileId) {
      BBox tileBbox = WebMercatorTile.forQuadkey(tileId).getBBox(false);
      double lonDist = tileBbox.maxLon() - tileBbox.minLon();
      double latDist = tileBbox.maxLat() - tileBbox.minLat();
      double currentLon = tileBbox.minLon() + random.nextDouble(lonDist);
      double currentLat = tileBbox.minLat() + random.nextDouble(latDist);
      int pointsInLine = random.nextInt(2, 10);
      LineStringCoordinates coordinates = new LineStringCoordinates();
      for (int i = 0; i < pointsInLine; i++) {
        coordinates.add(new PointCoordinates(currentLon, currentLat));
        currentLon = currentLon + random.nextDouble(tileBbox.maxLon() - currentLon);
        currentLat = currentLat + random.nextDouble(tileBbox.maxLat() - currentLat);
      }
      return new XyzLineString().withCoordinates(coordinates);
    }
  }
}
