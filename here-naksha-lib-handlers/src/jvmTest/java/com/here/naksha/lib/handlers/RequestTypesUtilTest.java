package com.here.naksha.lib.handlers;

import com.here.naksha.lib.handlers.util.RequestTypesUtil;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.Write;
import naksha.model.request.WriteOp;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static naksha.model.request.WriteOp.UPSERT;

public class RequestTypesUtilTest extends AbstractTest {

  @Test
  public void testIsCollectionsRequestType() {
    //Given: WriteRequest for only NakshaCollection
    final WriteRequest writeRequest = new WriteRequest();
    writeRequest.add(new Write().createCollection(new NakshaCollection().withId("test_collection")));
    Assertions.assertTrue(RequestTypesUtil.isOnlyWriteCollections(writeRequest));
    Assertions.assertFalse(RequestTypesUtil.isOnlyWriteFeatures(writeRequest));
    writeRequest.add(new Write().createCollection(new NakshaCollection().withId("test_collection2")));
    Assertions.assertTrue(RequestTypesUtil.isOnlyWriteCollections(writeRequest));
    Assertions.assertFalse(RequestTypesUtil.isOnlyWriteFeatures(writeRequest));
  }

  @Test
  public void testIsFeaturesRequestType() {
    //Given: WriteRequest for only NakshaFeature
    final WriteRequest writeRequest = new WriteRequest();
    writeRequest.add(new Write().withOp(UPSERT).withCollectionId("coll").withFeature(new NakshaFeature("feature1")));
    Assertions.assertTrue(RequestTypesUtil.isOnlyWriteFeatures(writeRequest));
    Assertions.assertFalse(RequestTypesUtil.isOnlyWriteCollections(writeRequest));
    writeRequest.add(new Write().withOp(UPSERT).withCollectionId("coll").withFeature(new NakshaFeature("feature2")));
    Assertions.assertTrue(RequestTypesUtil.isOnlyWriteFeatures(writeRequest));
    Assertions.assertFalse(RequestTypesUtil.isOnlyWriteCollections(writeRequest));
  }

  @Test
  public void testIsNotRequestForJustOneType() {
    //Given: WriteRequest containing both types
    final WriteRequest writeRequest = new WriteRequest();
    writeRequest.add(new Write().withOp(UPSERT).withCollectionId("coll").withFeature(new NakshaFeature("feature1")));
    writeRequest.add(new Write().upsertCollection(new NakshaCollection().withId("test_collection")));
    Assertions.assertFalse(RequestTypesUtil.isOnlyWriteFeatures(writeRequest));
    Assertions.assertFalse(RequestTypesUtil.isOnlyWriteCollections(writeRequest));
  }

  @Test
  public void testIsFeaturesRequestTypeForEmptyRequest() {
    //Given: Empty WriteRequest
    final WriteRequest writeRequest = new WriteRequest();
    //Then
    Assertions.assertFalse(RequestTypesUtil.isOnlyWriteCollections(writeRequest));
  }
}
