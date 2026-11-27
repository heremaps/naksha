package com.here.naksha.lib.view;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.here.naksha.lib.view.concurrent.ParallelQueryExecutor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import naksha.base.Platform;
import naksha.base.ToJsonOptions;
import naksha.base.TupleNumber;
import naksha.model.IReadSession;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.FeatureTuple;
import naksha.model.request.ReadFeatures;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteOp;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class ViewCopyOnWriteTest {
  @Test
  void readSnapshotDistinguishesBaseOnlyFromWritableLayerFeatures() {
    IStorage storage = storage();
    ViewLayer delta = new ViewLayer(storage, "catalog", "delta");
    ViewLayer base = new ViewLayer(storage, "catalog", "base");
    View view = new View(new ViewLayerCollection("copy_on_write", delta, base));
    Map<String, List<ViewLayerFeature>> rows = new LinkedHashMap<>();
    rows.put("inherited", List.of(new ViewLayerFeature(tuple("inherited"), 1, base)));
    rows.put("delta", List.of(new ViewLayerFeature(tuple("delta"), 0, delta)));
    FeatureTuple winner = tuple("both");
    rows.put("both", List.of(new ViewLayerFeature(tuple("both"), 1, base), new ViewLayerFeature(winner, 0, delta)));
    try (MockedConstruction<ParallelQueryExecutor> ignored = mockConstruction(ParallelQueryExecutor.class,
        (executor, context) -> when(executor.queryInParallel(anyList())).thenReturn(rows));
        ViewReadSession session = view.newReadSession(new SessionOptions())) {
      ReadFeatures request = new ReadFeatures();
      request.getFeatureIds().add("inherited");
      request.getFeatureIds().add("delta");
      request.getFeatureIds().add("both");
      ViewSuccessResult result = assertInstanceOf(ViewSuccessResult.class, session.executeRead(request));
      assertEquals(Set.of("inherited"), result.getInheritedFeatureIds());
      assertSame(winner, result.getFeatureTupleList().get(2));
      assertThrows(UnsupportedOperationException.class, () -> result.getInheritedFeatureIds().add("other"));
      assertFalse(Platform.toJSON(result, ToJsonOptions.DEFAULT).contains("inheritedFeatureIds"));
    }
  }

  @Test
  void viewWritesNeverWeakenExplicitAtomicVersionValidation() {
    IStorage storage = storage();
    IWriteSession writer = mock(IWriteSession.class);
    when(storage.newWriteSession(any())).thenReturn(writer);
    when(writer.executeWrite(any())).thenReturn(new SuccessResponse());
    ViewLayer layer = new ViewLayer(storage, "catalog", "delta");
    View view = new View(new ViewLayerCollection("atomic_view", layer));
    NakshaCollection target = new NakshaCollection("delta", "catalog");
    Write missingVersion = new Write().updateFeature(target, new NakshaFeature("missing_version"), true);
    Write versioned = new Write().updateFeature(target, new NakshaFeature("versioned"), true);
    versioned.setVersion(100L);
    try (ViewWriteSession session = view.newWriteSession(new SessionOptions())) {
      session.executeWrite(new WriteRequest().add(missingVersion).add(versioned));
    }
    assertEquals(WriteOp.UPSERT, missingVersion.getOp());
    assertTrue(missingVersion.getAtomic());
    assertNull(missingVersion.getVersion());
    assertEquals(WriteOp.UPSERT, versioned.getOp());
    assertTrue(versioned.getAtomic());
    assertEquals(100L, versioned.getVersion());
    verify(writer, never()).commit();
    verify(writer, never()).rollback();
  }

  private static FeatureTuple tuple(String id) {
    FeatureTuple tuple = new FeatureTuple(new TupleNumber(1L, 2, 3, 4L, 100L), null);
    tuple.setFeature(new NakshaFeature(id));
    return tuple;
  }

  private static IStorage storage() {
    IStorage storage = mock(IStorage.class);
    when(storage.newReadSession(any())).thenAnswer(ignored -> mock(IReadSession.class));
    return storage;
  }
}
