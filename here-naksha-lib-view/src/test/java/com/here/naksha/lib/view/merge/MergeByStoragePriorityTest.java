package com.here.naksha.lib.view.merge;

import com.here.naksha.lib.view.ViewLayerFeature;
import naksha.model.objects.NakshaFeature;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MergeByStoragePriorityTest {

  MergeByStoragePriority mergeStrategy = new MergeByStoragePriority();

  @Test
  void checkPriorityMerge() {
    // given
    List<ViewLayerFeature> singleRowFeatures = new ArrayList<>();

    NakshaFeature f1 = new NakshaFeature();
    NakshaFeature f2 = new NakshaFeature();
    NakshaFeature f3 = new NakshaFeature();

    singleRowFeatures.add(new ViewLayerFeature(f1, 1, null));
    singleRowFeatures.add(new ViewLayerFeature(f2, 0, null));
    singleRowFeatures.add(new ViewLayerFeature(f3, 2, null));

    // when
    NakshaFeature outputFeature = mergeStrategy.apply(singleRowFeatures);

    // then
    assertSame(f2,  outputFeature);
  }

  @Test
  void checkSamePriorityMerge() {
    // given
    List<ViewLayerFeature> singleRowFeatures = new ArrayList<>();

    NakshaFeature f1 = new NakshaFeature();
    NakshaFeature f2 = new NakshaFeature();
    NakshaFeature f3 = new NakshaFeature();

    singleRowFeatures.add(new ViewLayerFeature(f1, 0, null));
    singleRowFeatures.add(new ViewLayerFeature(f2, 0, null));
    singleRowFeatures.add(new ViewLayerFeature(f3, 2, null));

    // when
    NakshaFeature outputFeature = mergeStrategy.apply(singleRowFeatures);

    // then should pick first on list
    assertSame(f1,  outputFeature);
  }

  @Test
  void checkEmptyMerge() {
    // given
    List<ViewLayerFeature> singleRowFeatures = new ArrayList<>();

    // expect
    assertThrows(NoSuchElementException.class, () -> mergeStrategy.apply(singleRowFeatures));
  }

  @Test
  void checkNull() {
    // expect
    assertThrows(NullPointerException.class, () -> mergeStrategy.apply(null));
  }
}
