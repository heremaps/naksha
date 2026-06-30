package com.here.naksha.lib.view.merge;

import com.here.naksha.lib.view.ViewLayerFeature;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.XyzMembers;
import naksha.model.request.FeatureTuple;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static naksha.base.LibBaseKt.Int64;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

public class MergeByStoragePriorityTest {

  MergeByStoragePriority mergeStrategy = new MergeByStoragePriority();

  @Test
  void checkPriorityMerge() {
    // given
    List<ViewLayerFeature> singleRowFeatures = new ArrayList<>();

    NakshaFeature f1 = new NakshaFeature();
    NakshaFeature f2 = new NakshaFeature();
    NakshaFeature f3 = new NakshaFeature();

    FeatureTuple t1 = new FeatureTuple(f1, XyzMembers.XyzTn);
    FeatureTuple t2 = new FeatureTuple(f2, XyzMembers.XyzTn);
    FeatureTuple t3 = new FeatureTuple(f3, XyzMembers.XyzTn);

    singleRowFeatures.add(new ViewLayerFeature(t1, 1, null));
    singleRowFeatures.add(new ViewLayerFeature(t2, 0, null));
    singleRowFeatures.add(new ViewLayerFeature(t3, 2, null));

    // when
    NakshaFeature outputFeature = mergeStrategy.apply(singleRowFeatures).getFeature();

    // then
    assertSame(t2.getFeature(),  outputFeature);
  }

  @Test
  void checkSamePriorityMerge() {
    // given
    List<ViewLayerFeature> singleRowFeatures = new ArrayList<>();

    NakshaFeature f1 = new NakshaFeature();
    NakshaFeature f2 = new NakshaFeature();
    NakshaFeature f3 = new NakshaFeature();

    FeatureTuple t1 = new FeatureTuple(f1, XyzMembers.XyzTn);
    FeatureTuple t2 = new FeatureTuple(f2, XyzMembers.XyzTn);
    FeatureTuple t3 = new FeatureTuple(f3, XyzMembers.XyzTn);

    singleRowFeatures.add(new ViewLayerFeature(t1, 0, null));
    singleRowFeatures.add(new ViewLayerFeature(t2, 0, null));
    singleRowFeatures.add(new ViewLayerFeature(t3, 2, null));

    // when
    NakshaFeature outputFeature = mergeStrategy.apply(singleRowFeatures).getFeature();

    // then should pick first on list
    assertSame(t1.getFeature(),  outputFeature);
  }

  @Test
  void checkEmptyMerge() {
    // given
    List<ViewLayerFeature> singleRowFeatures = new ArrayList<>();

    // expect
    assertThrows(IndexOutOfBoundsException.class, () -> mergeStrategy.apply(singleRowFeatures));
  }

  @Test
  void checkNull() {
    // expect
    assertThrows(NullPointerException.class, () -> mergeStrategy.apply(null));
  }
}
