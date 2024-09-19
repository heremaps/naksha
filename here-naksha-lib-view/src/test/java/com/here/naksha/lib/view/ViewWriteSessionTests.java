package com.here.naksha.lib.view;

import naksha.base.StringList;
import naksha.geo.PointCoord;
import naksha.geo.SpPoint;
import naksha.model.Action;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ViewWriteSessionTests extends PsqlTests {

  final boolean enabled() {
    return true;
  }

  static final String COLLECTION_0 = "test_view_write_session_0";
  static final String COLLECTION_1 = "test_view_write_session_1";


  @Test
  @Order(14)
  @EnabledIf("runTest")
  void createCollection() {
    assertNotNull(storage);
    assertNotNull(session);
    final WriteRequest request = new WriteRequest();
    request.add(new Write().createCollection(null, new NakshaCollection(COLLECTION_0, 1, null, false, true, null)));
    request.add(new Write().createCollection(null, new NakshaCollection(COLLECTION_1, 1, null, false, true, null)));
    SuccessResponse response = (SuccessResponse) session.execute(request);
    assertNotNull(response.getTuples());
    session.commit();
  }

  @Test
  @Order(15)
  @EnabledIf("runTest")
  void addFeatures() {
    assertNotNull(storage);
    assertNotNull(session);
    final WriteRequest requestTest0 = new WriteRequest();

    final NakshaFeature feature = new NakshaFeature();
    feature.setGeometry(new SpPoint(new PointCoord(0d,0d)));
    feature.setId("feature_id_view0");
    requestTest0.add(new Write().createFeature(null,COLLECTION_0,feature));

      session.execute(requestTest0);
      session.commit();

  }

  @Test
  @Order(16)
  @EnabledIf("runTest")
  void readAndWrite_UsingViewWriteSession() {
    assertNotNull(storage);

    ViewLayer layer0 = new ViewLayer(storage, COLLECTION_0);
    ViewLayer layer1 = new ViewLayer(storage, COLLECTION_1);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("Layers", layer0, layer1);
    View view = new View(viewLayerCollection);

    ViewWriteSession writeSession = view.newWriteSession(new SessionOptions()).init();
      ReadFeatures readRequest = new ReadFeatures();
      StringList featureIds = new StringList();
      featureIds.add("feature_id_view0");
    readRequest.setFeatureIds(featureIds);
    Response response = writeSession.execute(readRequest);
    assertInstanceOf(SuccessResponse.class,response);
    SuccessResponse successResponse = (SuccessResponse) response;
      List<NakshaFeature> features = successResponse.getFeatures();

      assertEquals(1, features.size());
    PointCoord coordinates = (PointCoord) features.get(0).getGeometry().getCoordinates();
    assertEquals(0d, coordinates.getLongitude());

      //Update fetched feature using viewwritesession
      final WriteRequest writeRequest = new WriteRequest();
      features.stream().forEach(feature -> {
        feature.setGeometry(new SpPoint(new PointCoord(1d,1d)));
        feature.getProperties().put("testProperty", "test");
        writeRequest.add(new Write().updateFeature(null, viewLayerCollection.getTopPriorityLayer().getCollectionId(), feature, false));
      });
    SuccessResponse response1 = (SuccessResponse) writeSession.execute(writeRequest);
    assertNotNull(response1.getTuples().get(0));
        NakshaFeature feature = response1.getFeatures().get(0);
        assertEquals(1d, ((PointCoord) feature.getGeometry().getCoordinates()).getLongitude());
        assertTrue(feature.getProperties().containsKey("testProperty"));
        assertEquals("test", feature.getProperties().get("testProperty").toString());
        assertSame(Action.UPDATED, response1.getTuples().get(0).tuple.meta.action());

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

      session.commit();

  }

  @Test
  @Order(17)
  @EnabledIf("runTest")
  void featureMissingInCollection1() {
    assertNotNull(storage);

    ViewLayer layer1 = new ViewLayer(storage, COLLECTION_1);

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
  @Order(18)
  @EnabledIf("runTest")
  void writeFeatureOnSelectedLayer() {
    assertNotNull(storage);
    final String FEATURE_ID = "feature_id_view1";

    ViewLayer layer0 = new ViewLayer(storage, COLLECTION_0);
    ViewLayer layer1 = new ViewLayer(storage, COLLECTION_1);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("Layers", layer0, layer1);
    View view = new View(viewLayerCollection);

    ViewWriteSession writeSession = view.newWriteSession(null).withWriteLayer(layer1).init();
      WriteRequest writeRequest = new WriteRequest();
      final NakshaFeature feature = new NakshaFeature(FEATURE_ID);
      feature.setGeometry(new SpPoint(new PointCoord(0d, 0d)));
      writeRequest.add(new Write().createFeature(null, COLLECTION_1, feature));

    SuccessResponse response = (SuccessResponse) writeSession.execute(writeRequest);
    assertNotNull(response.getTuples().get(0));
    assertSame(Action.CREATED, response.getTuples().get(0).tuple.meta.action());
      writeSession.commit();

      //check if the newly added feature found on layer
      ReadFeatures readRequest = new ReadFeatures();
    StringList featureIds = new StringList();
    featureIds.add(FEATURE_ID);
    readRequest.setFeatureIds(featureIds);

      List<NakshaFeature> list = queryView(view, readRequest);
      assertEquals(1, list.size());

    session.commit();
  }

  @Test
  @Order(19)
  @EnabledIf("runTest")
  void deleteFeatureFromTopLayer() {
    assertNotNull(storage);
    final String FEATURE_ID = "feature_id_view1";
    ViewLayer layer0 = new ViewLayer(storage, COLLECTION_0);
    ViewLayer layer1 = new ViewLayer(storage, COLLECTION_1);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("Layers", layer1, layer0);
    View view = new View(viewLayerCollection);
    ViewWriteSession writeSession = view.newWriteSession(new SessionOptions()).init();
    WriteRequest writeRequest = new WriteRequest();
    writeRequest.add(new Write().deleteFeatureById(null, viewLayerCollection.getTopPriorityLayer().getCollectionId() , FEATURE_ID,null));

    SuccessResponse response = (SuccessResponse) writeSession.execute(writeRequest);

    assertNotNull(response.getTuples().get(0));
    assertSame(Action.DELETED, Objects.requireNonNull(response.getTuples().get(0).tuple).meta.action());
        assertEquals(FEATURE_ID, response.getFeatures().get(0).getId());

      writeSession.commit();

      //check if the newly added feature found on layer
      ReadFeatures readRequest = new ReadFeatures();
    StringList list = new StringList();
    list.add(FEATURE_ID);
    readRequest.setFeatureIds(list);
      List<NakshaFeature> response1 = queryView(view, readRequest);
      assertEquals(0, response1.size());
    session.commit();
  }

  private List<NakshaFeature> queryView(View view, ReadFeatures request) {
    Response response = view.newReadSession(null).execute(request);
    assertInstanceOf(SuccessResponse.class,response);
    SuccessResponse successResponse = (SuccessResponse) response;
    return successResponse.getFeatures();
  }

}
