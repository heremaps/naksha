package com.here.naksha.lib.view.missing;

import naksha.base.Int64;
import naksha.base.Platform;
import naksha.model.*;
import com.here.naksha.lib.view.MissingIdResolver;
import com.here.naksha.lib.view.ViewLayer;
import com.here.naksha.lib.view.ViewLayerFeature;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.FeatureTuple;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static naksha.base.Platform.intToInt64;
import static naksha.model.FetchModeKt.withFeature;
import static naksha.model.FlagsKt.withAction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class ObligatoryLayersResolverTest {

  private @NotNull Metadata mockMetadata(String id) {
    final Int64 storageNumber = intToInt64(1);
    final TupleNumber tupleNumber = new TupleNumber(
        storageNumber,
        0,
        0,
        Naksha.featureNumber(id),
        Version.of(2024,1,1, intToInt64(0)),
        0
    );
    final Int64 updatedAt = Platform.currentMillis();
    return new Metadata(
        tupleNumber,
        withAction(0, Action.CREATED),
        null,
        updatedAt, null, null,
        null,
        null,
        1,
        0,
        0,
        id,
        "test",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null
    );
  }

  private @NotNull FeatureTuple mockFeatureTuple(@NotNull NakshaFeature feature) {
    final Metadata metadata = mockMetadata(feature.getId());
    final byte[] bytesFeature = Naksha.encodeFeature(feature, metadata.getFlags(), null);
    final Tuple tuple = new Tuple(metadata, bytesFeature, null, null, null, null, false);
    final FeatureTuple featureTuple = new FeatureTuple(metadata.getTupleNumber(), tuple);
    return featureTuple;
  }

  @Test
  void shouldPrepareLayerIdToFetchWhenMissing() {
    // given
    IStorage storage = mock(IStorage.class);
    ViewLayer obligatoryLayer = new ViewLayer(storage, "collection1");
    ViewLayer otherLayer = new ViewLayer(storage, "collection1");
    final NakshaFeature feature = new NakshaFeature();
    final FeatureTuple featureTuple = mockFeatureTuple(feature);

    List<ViewLayerFeature> singleRowFeatures = new ArrayList<>();
    singleRowFeatures.add(new ViewLayerFeature(featureTuple, 0, otherLayer));

    MissingIdResolver missingIdsResolver = new ObligatoryLayersResolver(Set.of(obligatoryLayer));

    // when
    List<Pair<ViewLayer, String>> resolvedIds = missingIdsResolver.layersToSearch(singleRowFeatures);

    // then
    assertNotNull(resolvedIds);
    assertEquals(obligatoryLayer, resolvedIds.get(0).getKey());
    assertEquals(feature.getId(), resolvedIds.get(0).getValue());
  }

  @Test
  void shouldPrepareLayerIdToFetchWhenLayerIsNotObligatory() {
    // given
    IStorage storage = mock(IStorage.class);
    ViewLayer obligatoryLayer = new ViewLayer(storage, "collection1");
    final NakshaFeature feature = new NakshaFeature();
    final FeatureTuple featureTuple = mockFeatureTuple(feature);

    List<ViewLayerFeature> singleRowFeatures = new ArrayList<>();
    singleRowFeatures.add(new ViewLayerFeature(featureTuple, 0, obligatoryLayer));

    MissingIdResolver missingIdsResolver = new ObligatoryLayersResolver(Set.of(obligatoryLayer));

    // when
    List<Pair<ViewLayer, String>> resolvedIds = missingIdsResolver.layersToSearch(singleRowFeatures);

    // then
    assertNotNull(resolvedIds);
    assertTrue(resolvedIds.isEmpty());
  }

  @Test
  void testEmptyInput() {
    // given
    IStorage storage = mock(IStorage.class);
    ViewLayer obligatoryLayer = new ViewLayer(storage, "collection1");
    MissingIdResolver missingIdsResolver = new ObligatoryLayersResolver(Set.of(obligatoryLayer));

    // expect
    assertNull(missingIdsResolver.layersToSearch(new ArrayList<>()));
    assertThrows(NullPointerException.class, () -> missingIdsResolver.layersToSearch(null));
  }
}
