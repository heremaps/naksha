package com.here.naksha.lib.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import naksha.base.StringList;
import naksha.geo.PointCoord;
import naksha.geo.SpPoint;
import naksha.model.Action;
import naksha.model.Naksha;
import naksha.model.SessionOptions;
import naksha.model.Tuple;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.StoreMode;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ViewWriteSessionTests extends PsqlTests {

  final boolean enabled() {
    return true;
  }

  static final String COLLECTION_0 = "test_view_write_session_0";
  static final String COLLECTION_1 = "test_view_write_session_1";
  static final NakshaCollection COLLECTION_0_FEATURE = new NakshaCollection(
          COLLECTION_0, TEST_MAP_ID, 1, null, StoreMode.ON, StoreMode.ON, StoreMode.ON);
  static final NakshaCollection COLLECTION_1_FEATURE = new NakshaCollection(
          COLLECTION_1, TEST_MAP_ID, 1, null, StoreMode.ON, StoreMode.ON, StoreMode.ON);


  @Test
  @Order(14)
  @EnabledIf("runTest")
  void createCollection() {
    assertNotNull(storage);
    final WriteRequest request = new WriteRequest();
    request.add(new Write().createCollection(COLLECTION_0_FEATURE));
    request.add(new Write().createCollection(COLLECTION_1_FEATURE));
    SuccessResponse response = executeWrite(request);
    assertNotNull(response.getFeatureTupleList());
  }

  @Test
  @Order(15)
  @EnabledIf("runTest")
  void addFeatures() {
    assertNotNull(storage);
    final WriteRequest requestTest0 = new WriteRequest();

    final NakshaFeature feature = new NakshaFeature();
    feature.setGeometry(new SpPoint(new PointCoord(0d, 0d)));
    feature.setId("feature_id_view0");
    requestTest0.add(new Write().createFeature(COLLECTION_0_FEATURE, feature));

    executeWrite(requestTest0);
  }

  @Test
  @Order(16)
  @EnabledIf("runTest")
  void readAndWrite_UsingViewWriteSession() {
    assertNotNull(storage);

    ViewLayer layer0 = new ViewLayer(storage, TEST_MAP_ID, COLLECTION_0);
    ViewLayer layer1 = new ViewLayer(storage, TEST_MAP_ID, COLLECTION_1);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("Layers", layer0, layer1);
    View view = new View(viewLayerCollection);

    ViewWriteSession writeSession = view.newWriteSession(new SessionOptions());
    ReadFeatures readRequest = new ReadFeatures();
    readRequest.getFeatureIds().add("feature_id_view0");
    Response response = writeSession.execute(readRequest);
    assertInstanceOf(SuccessResponse.class, response);
    SuccessResponse successResponse = (SuccessResponse) response;
    List<NakshaFeature> features = successResponse.getFeatures();

    assertEquals(1, features.size());
    PointCoord coordinates = (PointCoord) features.get(0).getGeometry().getCoordinates();
    assertEquals(0d, coordinates.getLongitude());

    //Update fetched feature using viewwritesession
    final WriteRequest writeRequest = new WriteRequest();
    features.stream().forEach(feature -> {
      feature.setGeometry(new SpPoint(new PointCoord(1d, 1d)));
      feature.getProperties().put("testProperty", "test");
      writeRequest.add(new Write().updateFeature(COLLECTION_0_FEATURE, feature, false));
    });
    SuccessResponse response1 = (SuccessResponse) writeSession.execute(writeRequest);
    assertNotNull(response1.getFeatureTupleList().get(0));
    NakshaFeature feature = response1.getFeatures().get(0);
    assertEquals(1d, ((PointCoord) feature.getGeometry().getCoordinates()).getLongitude());
    assertTrue(feature.getProperties().containsKey("testProperty"));
    assertEquals("test", feature.getProperties().get("testProperty").toString());
    assertSame(Action.UPDATE, response1.getFeatureTupleList().get(0).tuple.meta.action());

    writeSession.commit();

    //Check if the feature updated in expected storage collection
    ViewLayerCollection readViewCollection = new ViewLayerCollection("ReadLayer", layer0);
    view = new View(readViewCollection);

    List<NakshaFeature> list = queryView(view, readRequest);
    assertEquals(1, list.size());
    NakshaFeature updatedFeature = list.get(0);
    assertEquals(1d, ((PointCoord) updatedFeature.getGeometry().getCoordinates()).getLongitude());
    assertTrue(updatedFeature.getProperties().containsKey("testProperty"));
    assertEquals("test", updatedFeature.getProperties().get("testProperty").toString());
  }

  @Test
  @Order(17)
  @EnabledIf("runTest")
  void updateNonExistentFeatureCreatesFeature() {
    assertNotNull(storage);
    final String FEATURE_ID = "non_existent_feature";
    //GIVEN
    ViewLayer layer0 = new ViewLayer(storage, TEST_MAP_ID, COLLECTION_0);
    ViewLayer layer1 = new ViewLayer(storage, TEST_MAP_ID, COLLECTION_1);

    //AND view will write to layer0 (top priority) by default
    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("Layers", layer0, layer1);
    View view = new View(viewLayerCollection);

    ViewWriteSession writeSession = view.newWriteSession(new SessionOptions());

    //AND Try to "update" a feature that does not exist
    final WriteRequest writeRequest = new WriteRequest();
    final NakshaFeature feature = new NakshaFeature(FEATURE_ID);
    feature.setGeometry(new SpPoint(new PointCoord(10d, 10d)));
    writeRequest.add(new Write().updateFeature(COLLECTION_0_FEATURE, feature, false));

    //WHEN Because UPDATE is changed to UPSERT, this should create the feature
    SuccessResponse response = (SuccessResponse) writeSession.execute(writeRequest);
    //THEN
    assertNotNull(response.getFeatureTupleList().get(0));
    assertSame(Action.CREATED, response.getFeatureTupleList().get(0).tuple.meta.action());
    writeSession.commit();

    //GIVEN Verify the feature was actually created in the top layer (collection_0)
    ReadFeatures readRequest = new ReadFeatures();
    readRequest.getFeatureIds().add(FEATURE_ID);
    //WHEN
    List<NakshaFeature> createdFeatures = queryView(view, readRequest);
    //THEN
    assertEquals(1, createdFeatures.size());
    assertEquals(FEATURE_ID, createdFeatures.get(0).getId());
    assertEquals(10d, ((PointCoord) createdFeatures.get(0).getGeometry().getCoordinates()).getLongitude());
  }

  @Test
  @Order(18)
  @EnabledIf("runTest")
  void featureMissingInCollection1() {
    assertNotNull(storage);

    ViewLayer layer1 = new ViewLayer(storage, TEST_MAP_ID, COLLECTION_1);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("Layers", layer1);
    View view = new View(viewLayerCollection);

    ReadFeatures readRequest = new ReadFeatures();
    StringList featureIds = new StringList();
    featureIds.add("feature_id_view0");
    readRequest.setFeatureIds(featureIds);

    List<NakshaFeature> list = queryView(view, readRequest);
    assertTrue(list.isEmpty());
  }

  @Test
  @Order(19)
  @EnabledIf("runTest")
  void writeFeatureOnSelectedLayer() {
    assertNotNull(storage);
    final String FEATURE_ID = "feature_id_view1";

    ViewLayer layer0 = new ViewLayer(storage, TEST_MAP_ID, COLLECTION_0);
    ViewLayer layer1 = new ViewLayer(storage, TEST_MAP_ID, COLLECTION_1);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("Layers", layer0, layer1);
    View view = new View(viewLayerCollection);

    ViewWriteSession writeSession = view.newWriteSession(null).withWriteLayer(layer1);
    WriteRequest writeRequest = new WriteRequest();
    final NakshaFeature feature = new NakshaFeature(FEATURE_ID);
    feature.setGeometry(new SpPoint(new PointCoord(0d, 0d)));
    writeRequest.add(new Write().createFeature(COLLECTION_1_FEATURE, feature));

    SuccessResponse response = (SuccessResponse) writeSession.execute(writeRequest);
    assertNotNull(response.getFeatureTupleList().get(0));
    assertSame(Action.CREATE, response.getFeatureTupleList().get(0).tuple.meta.action());
    writeSession.commit();

    //check if the newly added feature found on layer
    ReadFeatures readRequest = new ReadFeatures();
    StringList featureIds = new StringList();
    featureIds.add(FEATURE_ID);
    readRequest.setFeatureIds(featureIds);

    List<NakshaFeature> list = queryView(view, readRequest);
    assertEquals(1, list.size());
  }

  @Test
  @Order(20)
  @EnabledIf("runTest")
  void deleteFeatureFromTopLayer() {
    assertNotNull(storage);
    ViewLayer layer0 = new ViewLayer(storage, TEST_MAP_ID, COLLECTION_0);
    ViewLayer layer1 = new ViewLayer(storage, TEST_MAP_ID, COLLECTION_1);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("Layers", layer1, layer0);
    View view = new View(viewLayerCollection);
    ViewWriteSession writeSession = view.newWriteSession(new SessionOptions());
    WriteRequest writeRequest = new WriteRequest();
    writeRequest.add(new Write().deleteFeatureById(COLLECTION_0_FEATURE, "feature_id_view1"));

    @NotNull Response response = writeSession.execute(writeRequest);
    @NotNull SuccessResponse ok = assertInstanceOf(SuccessResponse.class, response);
    assertEquals(1, ok.getLength());

    @NotNull FeatureTupleList featureTupleList = ok.getFeatureTupleList();
    assertEquals(1, featureTupleList.size());
    // TODO: We need replace this code with: writeSession.loadTuples(featureTupleList);
    writeSession.commit();
    Naksha.cache.load(featureTupleList);
    // TODO: End of code to replace
    FeatureTuple featureTuple = featureTupleList.get(0);
    assertNotNull(featureTuple);
    Tuple tuple = featureTuple.tuple;
    assertNotNull(tuple);

    assertSame(Action.DELETE, tuple.meta.action());
    assertEquals("feature_id_view1", featureTuple.getId());

    // TODO: Ones we have the loadTuples available, do:
    // writeSession.commit();

    //check if the newly added feature found on layer
    ReadFeatures readRequest = new ReadFeatures();
    StringList list = new StringList();
    list.add("feature_id_view1");
    readRequest.setFeatureIds(list);
    List<NakshaFeature> response1 = queryView(view, readRequest);
    assertEquals(0, response1.size());
  }

  private List<NakshaFeature> queryView(View view, ReadFeatures request) {
    Response response = view.newReadSession(null).execute(request);
    assertInstanceOf(SuccessResponse.class, response);
    SuccessResponse successResponse = (SuccessResponse) response;
    return successResponse.getFeatures();
  }

}
