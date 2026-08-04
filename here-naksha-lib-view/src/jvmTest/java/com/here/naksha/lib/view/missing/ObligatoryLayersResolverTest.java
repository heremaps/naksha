package com.here.naksha.lib.view.missing;

import naksha.model.*;
import com.here.naksha.lib.view.MissingIdResolver;
import com.here.naksha.lib.view.ViewLayer;
import com.here.naksha.lib.view.ViewLayerFeature;
import naksha.model.objects.NakshaFeature;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static naksha.psql.PgTest.TEST_MAP_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class ObligatoryLayersResolverTest {

  @Test
  void shouldPrepareLayerIdToFetchWhenMissing() {
    // given
    IStorage storage = mock(IStorage.class);
    ViewLayer obligatoryLayer = new ViewLayer(storage, TEST_MAP_ID, "collection1");
    ViewLayer otherLayer = new ViewLayer(storage, TEST_MAP_ID, "collection1");
    final NakshaFeature feature = new NakshaFeature();

    List<ViewLayerFeature> singleRowFeatures = new ArrayList<>();
    singleRowFeatures.add(new ViewLayerFeature(feature, 0, otherLayer));

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
    ViewLayer obligatoryLayer = new ViewLayer(storage, TEST_MAP_ID, "collection1");
    final NakshaFeature feature = new NakshaFeature();

    List<ViewLayerFeature> singleRowFeatures = new ArrayList<>();
    singleRowFeatures.add(new ViewLayerFeature(feature, 0, obligatoryLayer));

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
    ViewLayer obligatoryLayer = new ViewLayer(storage, TEST_MAP_ID, "collection1");
    MissingIdResolver missingIdsResolver = new ObligatoryLayersResolver(Set.of(obligatoryLayer));

    // expect
    assertNull(missingIdsResolver.layersToSearch(new ArrayList<>()));
    assertThrows(NullPointerException.class, () -> missingIdsResolver.layersToSearch(null));
  }
}
