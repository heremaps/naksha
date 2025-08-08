package com.here.naksha.cli.storages;

import com.here.naksha.cli.parsers.JsonFileParser;
import com.here.naksha.cli.parsers.JsonFileParserException;
import com.here.naksha.lib.core.models.geojson.HQuad;
import naksha.base.JvmList;
import naksha.geo.LineStringCoord;
import naksha.geo.PointCoord;
import naksha.geo.SpBoundingBox;
import naksha.geo.SpLineString;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

final class GeneratingStorageService {
    private final JsonFileParser jsonFileParser = new JsonFileParser();

    List<NakshaFeature> generateFeatures(GeneratingStorageConfigProperties configProperties) {
        int count = requireCount(configProperties.getCount());
        List<String> tileIds = loadAndRequireTileIds(configProperties);
        NakshaFeature templateFeature = loadTemplateFeatureOrEmpty(configProperties.getFeatureTemplateFilePath());

        List<NakshaFeature> features = new ArrayList<>();
        Random random = ThreadLocalRandom.current();

        for (int i = 0; i < count; ++i) {
            String featureId = Integer.toString(i);
            String tileId = randomTileId(tileIds, random);
            NakshaFeature feature = generateFeature(templateFeature, featureId, tileId, random);
            features.add(feature);
        }

        return features;
    }

    private int requireCount(@Nullable Integer count) {
        if (count == null) {
            throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Provide count in the config properties.");
        }

        return count;
    }

    private NakshaFeature loadTemplateFeatureOrEmpty(@Nullable String featureTemplateFilePath) {
        if (featureTemplateFilePath != null) {
            return loadTemplateFeature(featureTemplateFilePath);
        }

        return new NakshaFeature();
    }

    private NakshaFeature loadTemplateFeature(String featureTemplateFilePath) {
        try {
            Path path = Path.of(featureTemplateFilePath);
            return jsonFileParser.parse(path, NakshaFeature.class);
        } catch (JsonFileParserException e) {
            throw new NakshaException(NakshaError.EXCEPTION, "Problem while loading the feature template!", e);
        }
    }

    private String randomTileId(List<String> tileIds, Random random) {
        return tileIds.get(random.nextInt(tileIds.size()));
    }

    private NakshaFeature generateFeature(NakshaFeature baseFeature, String id, String tileId, Random random) {
        NakshaFeature feature = baseFeature.copy(false);
        feature.setId(id);
        feature.setGeometry(randomLineInTile(tileId, random));
        feature.setReferencePoint(null);

        return feature;
    }

    private SpLineString randomLineInTile(String tileId, Random random) {
        HQuad hQuad = new HQuad(tileId, true);
        SpBoundingBox tileBbox = hQuad.getBoundingBox();

        int pointsInLine = random.nextInt(2, 10);
        LineStringCoord coords = new LineStringCoord();

        for (int i = 0; i < pointsInLine; ++i) {
            coords.add(randomPointCoord(tileBbox, random));
        }

        return new SpLineString(coords);
    }

    private PointCoord randomPointCoord(SpBoundingBox boundingBox, Random random) {
        double lonDist = boundingBox.getMaxLongitude() - boundingBox.getMinLongitude();
        double latDist = boundingBox.getMaxLatitude() - boundingBox.getMinLatitude();

        double currentLon = boundingBox.getMinLongitude() + random.nextDouble(lonDist);
        double currentLat = boundingBox.getMinLatitude() + random.nextDouble(latDist);

        return new PointCoord(currentLon, currentLat);
    }

    private List<String> loadAndRequireTileIds(GeneratingStorageConfigProperties configProperties) {
        List<String> tileIds = new ArrayList<>();

        if (configProperties.getTileIds() != null) {
            JvmList tileIdsInJvmList = configProperties.getTileIds();

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
            throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Provide tileIds in the config properties.");
        }

        return tileIds;
    }

    private List<String> loadTileIdsFromCsv(String pathToFile) {
        try {
            Path path = Paths.get(pathToFile);
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new NakshaException(NakshaError.EXCEPTION, "Problem while loading tileIds from CSV file!", e);
        }
    }
}
