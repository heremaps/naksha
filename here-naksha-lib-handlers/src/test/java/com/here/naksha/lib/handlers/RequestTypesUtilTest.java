package com.here.naksha.lib.handlers;

import com.here.naksha.lib.handlers.util.RequestTypesUtil;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RequestTypesUtilTest {

  @Test
  public void testIsCollectionsRequestType() {
    //Given: WriteRequest for only NakshaCollection
    final WriteRequest writeRequest = new WriteRequest();
    writeRequest.add(new Write().createCollection(new NakshaCollection("test_collection")));
    Assertions.assertTrue(RequestTypesUtil.isOnlyWriteCollections(writeRequest));
    Assertions.assertFalse(RequestTypesUtil.isOnlyWriteFeatures(writeRequest));
    writeRequest.add(new Write().createCollection(new NakshaCollection("test_collection2")));
    Assertions.assertTrue(RequestTypesUtil.isOnlyWriteCollections(writeRequest));
    Assertions.assertFalse(RequestTypesUtil.isOnlyWriteFeatures(writeRequest));
  }

  @Test
  public void testIsFeaturesRequestType() {
    //Given: WriteRequest for only NakshaFeature
    final WriteRequest writeRequest = new WriteRequest();
    writeRequest.add(new Write().upsertFeature(null, "coll", new NakshaFeature("feature1")));
    Assertions.assertTrue(RequestTypesUtil.isOnlyWriteFeatures(writeRequest));
    Assertions.assertFalse(RequestTypesUtil.isOnlyWriteCollections(writeRequest));
    writeRequest.add(new Write().upsertFeature(null, "coll", new NakshaFeature("feature2")));
    Assertions.assertTrue(RequestTypesUtil.isOnlyWriteFeatures(writeRequest));
    Assertions.assertFalse(RequestTypesUtil.isOnlyWriteCollections(writeRequest));
  }

  @Test
  public void testIsNotRequestForJustOneType() {
    //Given: WriteRequest containing both types
    final WriteRequest writeRequest = new WriteRequest();
    writeRequest.add(new Write().upsertFeature(null, "coll", new NakshaFeature("feature1")));
    writeRequest.add(new Write().upsertCollection(new NakshaCollection("test_collection")));
    Assertions.assertFalse(RequestTypesUtil.isOnlyWriteFeatures(writeRequest));
    Assertions.assertFalse(RequestTypesUtil.isOnlyWriteCollections(writeRequest));
  }
}
