package com.here.naksha.cli.storages;

import com.here.naksha.lib.core.models.geojson.WebMercatorTile;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import naksha.base.Int64;
import naksha.base.Platform;
import naksha.base.TupleNumber;
import naksha.base.Version;
import naksha.geo.LineStringCoord;
import naksha.geo.PointCoord;
import naksha.geo.SpBoundingBox;
import naksha.geo.SpLineString;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.FeatureTuple;
import naksha.model.request.FeatureTupleList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

final class GeneratingStorageService {
    private final AtomicInteger tileIndex = new AtomicInteger(0);

    @NotNull
    FeatureTupleList generateDummyFeatureTuples(Int64 storageNumber, int numOfTuplesToGenerate) {
        FeatureTupleList dummyFeatureTuples = new FeatureTupleList();
        dummyFeatureTuples.setCapacity(numOfTuplesToGenerate);
        for (int i = 0; i < numOfTuplesToGenerate; ++i) {
            TupleNumber dummyTupleNumber = new TupleNumber(
                storageNumber, 0, 0, Platform.intToInt64(0), Version.HEAD.number
            );
            FeatureTuple dummyFeatureTuple = new FeatureTuple(dummyTupleNumber, null);
            dummyFeatureTuples.add(dummyFeatureTuple);
        }
        return dummyFeatureTuples;
    }

    @NotNull
    List<NakshaFeature> generateFeatures(
        int numOfFeaturesToGenerate,
        List<String> tileIds,
        String idsPrefix,
        NakshaFeature templateFeature
    ) {
        List<NakshaFeature> features = new ArrayList<>(numOfFeaturesToGenerate);
        Random random = ThreadLocalRandom.current();
        int index = tileIndex.getAndUpdate(i -> (i + numOfFeaturesToGenerate) % tileIds.size());
        for (int i = 0; i < numOfFeaturesToGenerate; ++i) {
            String featureId = idsPrefix + UUID.randomUUID();
            String tileId = tileIds.get(index);
            NakshaFeature feature = generateFeature(templateFeature, featureId, tileId, random);
            features.add(feature);
            index = (index + 1) % tileIds.size();
        }
        return features;
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
}
