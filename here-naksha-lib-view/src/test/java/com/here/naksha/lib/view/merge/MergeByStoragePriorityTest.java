package com.here.naksha.lib.view.merge;

import com.here.naksha.lib.view.ViewLayerFeature;
import naksha.base.JvmInt64;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ExecutedOp;
import naksha.model.request.ResultTuple;
import naksha.psql.PgUtil;
import org.apache.logging.log4j.core.config.plugins.convert.TypeConverters.ByteArrayConverter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

public class MergeByStoragePriorityTest {

  MergeByStoragePriority mergeStrategy = new MergeByStoragePriority();

  @Test
  void checkPriorityMerge() {
    // given
    List<ViewLayerFeature> singleRowFeatures = new ArrayList<>();
    IStorage storage = mock(IStorage.class);

    NakshaFeature f1 = new NakshaFeature();
    NakshaFeature f2 = new NakshaFeature();
    NakshaFeature f3 = new NakshaFeature();

    byte[] bytesF1 = PgUtil.encodeFeature(f1, 0, null);
    byte[] bytesF2 = PgUtil.encodeFeature(f2, 0, null);
    byte[] bytesF3 = PgUtil.encodeFeature(f3, 0, null);

    final TupleNumber tupleNum = new TupleNumber(new JvmInt64(0), Version.fromDouble(3.0),0);
    Metadata metadata = mock(Metadata.class);

    Tuple tu1 = new Tuple(storage, tupleNum, FetchMode.FETCH_ALL, metadata, metadata.getId(), metadata.getFlags(), bytesF1, null, null, null, null);
    Tuple tu2 = new Tuple(storage, tupleNum, FetchMode.FETCH_ALL, metadata, metadata.getId(), metadata.getFlags(), bytesF2, null, null, null, null);
    Tuple tu3 = new Tuple(storage, tupleNum, FetchMode.FETCH_ALL, metadata, metadata.getId(), metadata.getFlags(), bytesF3, null, null, null, null);

    ResultTuple t1 = new ResultTuple(storage, tupleNum, ExecutedOp.READ, tu1);
    ResultTuple t2 = new ResultTuple(storage, tupleNum, ExecutedOp.READ, tu2);
    ResultTuple t3 = new ResultTuple(storage, tupleNum, ExecutedOp.READ, tu3);

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
    IStorage storage = mock(IStorage.class);

    NakshaFeature f1 = new NakshaFeature();
    NakshaFeature f2 = new NakshaFeature();
    NakshaFeature f3 = new NakshaFeature();

    byte[] bytesF1 = PgUtil.encodeFeature(f1, 0, null);
    byte[] bytesF2 = PgUtil.encodeFeature(f2, 0, null);
    byte[] bytesF3 = PgUtil.encodeFeature(f3, 0, null);

    final TupleNumber tupleNum = new TupleNumber(new JvmInt64(0), Version.fromDouble(3.0),0);
    Metadata metadata = mock(Metadata.class);

    Tuple tu1 = new Tuple(storage, tupleNum, FetchMode.FETCH_ALL, metadata, metadata.getId(), metadata.getFlags(), bytesF1, null, null, null, null);
    Tuple tu2 = new Tuple(storage, tupleNum, FetchMode.FETCH_ALL, metadata, metadata.getId(), metadata.getFlags(), bytesF2, null, null, null, null);
    Tuple tu3 = new Tuple(storage, tupleNum, FetchMode.FETCH_ALL, metadata, metadata.getId(), metadata.getFlags(), bytesF3, null, null, null, null);

    ResultTuple t1 = new ResultTuple(storage, tupleNum, ExecutedOp.READ, tu1);
    ResultTuple t2 = new ResultTuple(storage, tupleNum, ExecutedOp.READ, tu2);
    ResultTuple t3 = new ResultTuple(storage, tupleNum, ExecutedOp.READ, tu3);

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
    assertThrows(NoSuchElementException.class, () -> mergeStrategy.apply(singleRowFeatures));
  }

  @Test
  void checkNull() {
    // expect
    assertThrows(NullPointerException.class, () -> mergeStrategy.apply(null));
  }
}
