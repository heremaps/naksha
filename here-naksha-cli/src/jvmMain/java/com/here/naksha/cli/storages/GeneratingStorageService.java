package com.here.naksha.cli.storages;

import com.here.naksha.lib.core.models.geojson.HQuad;
import naksha.base.JvmList;
import naksha.geo.BBox;
import naksha.geo.LineStringCoord;
import naksha.geo.PointCoord;
import naksha.geo.SpLineString;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.model.objects.NakshaFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

final class GeneratingStorageService {
    List<NakshaFeature> generateFeatures(GeneratingStorageConfigProperties configProperties) {
        Integer count = configProperties.getCount();

        if (count == null) {
            throw new NakshaException(NakshaError.NOT_FOUND, "Provide count in the config properties.");
        }

        List<String> tileIds = getTileIds(configProperties);

        List<NakshaFeature> features = new ArrayList<>();
        Random random = ThreadLocalRandom.current();

        for (int i = 0; i < count; ++i) {
            String featureId = Integer.toString(i);
            String tileId = randomTileId(tileIds, random);
            NakshaFeature feature = generateFeature(featureId, tileId, random);
            features.add(feature);
        }

        return features;
    }

    private String randomTileId(List<String> tileIds, Random random) {
        return tileIds.get(random.nextInt(tileIds.size()));
    }

    private NakshaFeature generateFeature(String id, String tileId, Random random) {
        NakshaFeature feature = new NakshaFeature(id);
        feature.setGeometry(randomLineInTile(tileId, random));

        return feature;
    }

    private SpLineString randomLineInTile(String tileId, Random random) {
        HQuad hQuad = new HQuad(tileId, true);
        final var tileBbox = hQuad.getBoundingBox();

        int pointsInLine = random.nextInt(2, 10);
        LineStringCoord coords = new LineStringCoord();

        for (int i = 0; i < pointsInLine; ++i) {
            coords.add(randomPointCoord(tileBbox, random));
        }

        return new SpLineString(coords);
    }

    private PointCoord randomPointCoord(BBox boundingBox, Random random) {
        double lonDist = boundingBox.getMaxLongitude() - boundingBox.getMinLongitude();
        double latDist = boundingBox.getMaxLatitude() - boundingBox.getMinLatitude();

        double currentLon = boundingBox.getMinLongitude() + random.nextDouble(lonDist);
        double currentLat = boundingBox.getMinLatitude() + random.nextDouble(latDist);

        return new PointCoord(currentLon, currentLat);
    }

    private List<String> getTileIds(GeneratingStorageConfigProperties configProperties) {
        List<String> tileIds = new ArrayList<>();

        if (configProperties.getTileIds() != null) {
            final var tileIdsInJvmList = configProperties.getTileIds();

            tileIdsInJvmList.forEach(o -> {
                if (o instanceof String tileId) {
                    tileIds.add(tileId);
                }
            });
        }

        if (configProperties.getTileIdsCsvFilePath() != null) {
            List<String> loadedTileIds = loadTileIdsFromCsv(configProperties.getTileIdsCsvFilePath());
            tileIds.addAll(loadedTileIds);
        }

        if (tileIds.isEmpty()) {
            throw new NakshaException(NakshaError.NOT_FOUND, "Provide tileIds in the config properties.");
        }

        return tileIds;
    }

    private List<String> loadTileIdsFromCsv(String pathToFile) {
        Path path = Paths.get(pathToFile);
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new NakshaException(NakshaError.EXCEPTION, "Problem while loading tileIds from CSV file!", e);
        }
    }
}
