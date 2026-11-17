package com.here.naksha.app.service.http.tasks;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import naksha.base.NakshaError;
import naksha.base.TupleNumber;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.Write;
import naksha.model.request.WriteOp;
import org.junit.jupiter.api.Test;

class PatchCopyOnWriteTest {
  @Test
  void inheritedFeaturesUseCreateWhetherOrNotTheBaseHasANakshaVersion() {
    NakshaFeature feature = new NakshaFeature("base");
    for (boolean versioned : new boolean[] {false, true}) {
      if (versioned) feature.setTupleNumber(new TupleNumber(1L, 2, 3, 4L, 100L));
      Write write = WriteFeatureApiTask.preparePatchedWrite("view", feature, true);
      assertEquals(WriteOp.CREATE, write.getOp());
      assertFalse(write.getAtomic());
      assertNull(write.getVersion());
      assertSame(feature, write.getFeature());
      assertEquals("view", write.getCollectionId().text());
    }
  }

  @Test
  void writableLayerUpdatesRetainTheirVersionExpectation() {
    NakshaFeature feature = new NakshaFeature("delta");
    feature.setTupleNumber(new TupleNumber(1L, 2, 3, 4L, 100L));
    Write write = WriteFeatureApiTask.preparePatchedWrite("view", feature, false);
    assertEquals(WriteOp.UPDATE, write.getOp());
    assertTrue(write.getAtomic());
    assertEquals(100L, write.getVersion());
    feature.setTupleNumber(null);
    write = WriteFeatureApiTask.preparePatchedWrite("view", feature, false);
    assertTrue(write.getAtomic(), "A versionless ordinary update must still fail storage validation");
    assertNull(write.getVersion());
  }

  @Test
  void aCompetingDeltaCreationIsRetryableButUnrelatedErrorsAreNot() {
    Set<String> creates = Set.of("base");
    NakshaError collision = new NakshaError(NakshaError.FEATURE_EXISTS, "competing delta", "base");
    assertTrue(WriteFeatureApiTask.shouldRetryPatch(collision, creates));
    assertEquals(NakshaError.FEATURE_EXISTS, collision.getCode());
    assertFalse(WriteFeatureApiTask.shouldRetryPatch(
        new NakshaError(NakshaError.FEATURE_EXISTS, "unrelated", "other"), creates));
    assertFalse(WriteFeatureApiTask.shouldRetryPatch(
        new NakshaError(NakshaError.ILLEGAL_ARGUMENT, "invalid", "base"), creates));
    assertTrue(WriteFeatureApiTask.shouldRetryPatch(
        new NakshaError(NakshaError.CONFLICT, "stale version", "delta"), creates));
  }
}
