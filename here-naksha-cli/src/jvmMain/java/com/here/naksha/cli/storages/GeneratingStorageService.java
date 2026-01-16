package com.here.naksha.cli.storages;

import com.here.naksha.cli.parsers.JsonFileParser;
import com.here.naksha.cli.parsers.JsonFileParserException;
import com.here.naksha.lib.core.models.geojson.WebMercatorTile;
import naksha.base.Int64;
import naksha.base.Platform;
import naksha.base.StringList;
import naksha.geo.LineStringCoord;
import naksha.geo.PointCoord;
import naksha.geo.SpBoundingBox;
import naksha.geo.SpLineString;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.FeatureTuple;
import naksha.model.request.FeatureTupleList;
import org.jetbrains.annotations.NotNull;
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
    public static final String DEFAULT_IDS_PREFIX = "gen";
    private final JsonFileParser jsonFileParser = new JsonFileParser();

    @NotNull
    FeatureTupleList generateFeatureTuples(
            @NotNull IStorage storage,
            @NotNull GeneratingStorageConfigProperties configProperties
    ) {
        int numOfFeaturesToGenerate = requireCount(configProperties.getCount());
        FeatureTupleList featureTuples = new FeatureTupleList();
        featureTuples.setCapacity(numOfFeaturesToGenerate);
        for (int i = 0; i < numOfFeaturesToGenerate; ++i) {
            TupleNumber dummyTupleNumber = new TupleNumber(
                storage.getNumber(), 0, 0, Platform.intToInt64(i), new Version(0), i
            );
            FeatureTuple featureTuple = new FeatureTuple(dummyTupleNumber, null);
            featureTuples.add(featureTuple);
        }
        return featureTuples;
    }

    @NotNull
    List<NakshaFeature> generateFeatures(
            @NotNull GeneratingStorageConfigProperties configProperties,
            @NotNull List<? extends FeatureTuple> featureTuples
    ) {
        String idsPrefix = getIdsPrefixOrDefault(configProperties, DEFAULT_IDS_PREFIX);
        List<String> tileIds = requireTileIds(configProperties);
        NakshaFeature templateFeature = loadTemplateFeatureOrEmpty(configProperties.getFeatureTemplateFilePath());
        List<NakshaFeature> features = new ArrayList<>(featureTuples.size());
        Random random = ThreadLocalRandom.current();
        int tileIndex = 0;
        for (FeatureTuple featureTuple : featureTuples) {
            Int64 featureNumber = featureTuple.tupleNumber.featureNumber;
            String featureId = idsPrefix + featureNumber;
            String tileId = tileIds.get(tileIndex);
            NakshaFeature feature = generateFeature(templateFeature, featureId, tileId, random);
            features.add(feature);
            tileIndex = (tileIndex + 1) % tileIds.size();
        }
        return features;
    }

    private int requireCount(@Nullable Integer count) {
        if (count == null) {
            throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Provide count in the config properties.");
        }
        return count;
    }

    private String getIdsPrefixOrDefault(GeneratingStorageConfigProperties configProperties, String defaultPrefix) {
        String idsPrefix = configProperties.getIdsPrefix();
        if (idsPrefix == null) {
            return defaultPrefix;
        }
        return idsPrefix;
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

    private NakshaFeature generateFeature(NakshaFeature baseFeature, String id, String tileId, Random random) {
        NakshaFeature feature = baseFeature.copy(false);
        feature.setId(id);
        feature.setGeometry(randomLineInTile(tileId, random));
//         Some storage implementations (e.g., psql) may use [naksha.model.Metadata.calculateHereTile] method
//         to perform spatial queries. Therefore, randomly generated geometry and a reference point
//         from the template may introduce inconsistencies.
//         To avoid this, we have decided to remove the reference point.
        feature.setReferencePoint(null);
        return feature;
    }

    private SpLineString randomLineInTile(String tileId, Random random) {
        SpBoundingBox tileBbox = WebMercatorTile.forQuadkey(tileId).getBBox(false);
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

    private StringList requireTileIds(GeneratingStorageConfigProperties configProperties) {
        if (configProperties.getTileIds() == null && configProperties.getTileIdsCsvFilePath() == null) {
            throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Provide tileIds in the config properties.");
        }
        if (configProperties.getTileIds() != null && configProperties.getTileIdsCsvFilePath() != null) {
            throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Provide only one source of tileIds.");
        }

        StringList tileIds;
        if (configProperties.getTileIdsCsvFilePath() != null) {
            tileIds = StringList.fromList(loadTileIdsFromCsv(configProperties.getTileIdsCsvFilePath()));
        } else {
            tileIds = configProperties.getTileIds();
        }

        if (tileIds.isEmpty()) {
            throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Should be at least one tileId.");
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
