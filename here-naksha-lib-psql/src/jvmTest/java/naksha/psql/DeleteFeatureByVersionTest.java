package naksha.psql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import naksha.base.StringList;
import naksha.model.NakshaError;
import naksha.model.Version;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.api.Test;

class DeleteFeatureByVersionTest extends PgTestBase {

  private static final String COLLECTION_ID = "delete_feature_by_uuid_test_col";

  public DeleteFeatureByVersionTest() {
    super(new NakshaCollection(COLLECTION_ID), "");
  }

  @Test
  void shouldDeleteCreatedFeatureByVersion() {
    // Given: a feature stored in DB
    NakshaFeature initialFeature = createFeatureWithId("initial_feature_1");
    Version version = versionOf(initialFeature);

    // And: a delete request including version
    WriteRequest deleteByVersionReq = new WriteRequest().add(
        new Write().deleteFeatureById(getCollection(), initialFeature.getId(), version));

    // When: executing delete request
    Response deleteResp = storage.useWriteSession(newSessionOptions(), writer -> {
      Response resp = writer.execute(deleteByVersionReq);
      if (resp instanceof SuccessResponse) {
        writer.commit();
      } else {
        writer.rollback();
      }
      return resp;
    });

    // Then: deletion succeeded
    assertInstanceOf(SuccessResponse.class, deleteResp);
  }

  @Test
  void shouldDeleteUpdatedFeatureByVersion() {
    // Given: a feature stored in DB
    NakshaFeature initialFeature = createFeatureWithId("initial_feature_2");

    // And: this feature gest updated
    NakshaFeature updatedFeature = updateFeature(initialFeature);
    Version version = versionOf(updatedFeature);

    // And: a delete request including version
    WriteRequest deleteByVersionReq = new WriteRequest().add(
        new Write().deleteFeatureById(getCollection(), updatedFeature.getId(), version));

    // When: executing delete request
    Response deleteResp = storage.useWriteSession(newSessionOptions(), writer -> {
      Response resp = writer.execute(deleteByVersionReq);
      if (resp instanceof SuccessResponse) {
        writer.commit();
      } else {
        writer.rollback();
      }
      return resp;
    });

    // Then: deletion succeeded
    assertInstanceOf(SuccessResponse.class, deleteResp);
  }

  @Test
  void shouldFailDeletingFeatureWithOutdatedVersion() {
    // Given: a feature stored in DB
    NakshaFeature initialFeature = createFeatureWithId("initial_feature_3");
    Version initialVersion = versionOf(initialFeature);

    // And: this feature gets updated
    NakshaFeature updatedFeature = updateFeature(initialFeature);

    // And: a delete request including outdated version
    WriteRequest deleteByVersionReq = new WriteRequest().add(
        new Write().deleteFeatureById(getCollection(), updatedFeature.getId(), initialVersion));

    // When: executing delete request
    Response deleteResp = storage.useWriteSession(newSessionOptions(), writer -> {
      Response resp = writer.execute(deleteByVersionReq);
      if (resp instanceof SuccessResponse) {
        writer.commit();
      } else {
        writer.rollback();
      }
      return resp;
    });

    // Then: deletion failed
    assertInstanceOf(ErrorResponse.class, deleteResp);
    ErrorResponse errorDeleteResp = (ErrorResponse) deleteResp;
    assertEquals(NakshaError.CONFLICT, errorDeleteResp.getError().getCode());
  }

  @Test
  void shouldFailEntireRequestIfPartialDeletionFails() {
    // Given: a feature stored in DB
    NakshaFeature initialFeature = createFeatureWithId("initial_feature_4");
    Version initialVersion = versionOf(initialFeature);

    // And: this feature gets updated
    NakshaFeature updatedFeature = updateFeature(initialFeature);

    // And: a delete request including outdated version
    String firstAdditionalFeatureId = "additional_feature_1";
    String secondAdditionalFeatureId = "additional_feature_2";
    WriteRequest compositeWriteRequest = new WriteRequest()
        .add(new Write().createFeature(getCollection(), new NakshaFeature(firstAdditionalFeatureId)))
        .add(new Write().deleteFeatureById(getCollection(), updatedFeature.getId(), initialVersion)) // invalid delete Write operation
        .add(new Write().createFeature(getCollection(), new NakshaFeature(secondAdditionalFeatureId)));

    // When: executing composite request
    Response deleteResp = storage.useWriteSession(newSessionOptions(), writer -> {
      Response resp = writer.execute(compositeWriteRequest);
      if (resp instanceof SuccessResponse) {
        writer.commit();
      } else {
        writer.rollback();
      }
      return resp;
    });

    // Then: composite request failed
    assertInstanceOf(ErrorResponse.class, deleteResp);
    ErrorResponse errorDeleteResp = (ErrorResponse) deleteResp;
    assertEquals(NakshaError.CONFLICT, errorDeleteResp.getError().getCode());

    // And: there is only one (updated) feature in the collection
    NakshaFeatureList storedFeatures = getFeatureByIds(initialFeature.getId(), firstAdditionalFeatureId, secondAdditionalFeatureId);
    assertEquals(1, storedFeatures.size());
    NakshaFeature storedFeature = storedFeatures.get(0);
    assertEquals(updatedFeature.getId(), storedFeature.getId());
    assertEquals(versionOf(updatedFeature), versionOf(storedFeature));
  }

  private NakshaFeatureList getFeatureByIds(String... ids) {
    ReadFeatures readAll = new ReadFeatures()
        .withMapId(getCollection().getCatalogId())
        .addCollectionId(getCollection().getId());
    readAll.setFeatureIds(StringList.of(ids));
    return executeRead(readAll, newSessionOptions()).getFeatures();
  }

  private NakshaFeature createFeatureWithId(String id) {
    NakshaFeature initialFeature = new NakshaFeature(id);
    WriteRequest createFeatureReq = new WriteRequest().add(new Write().createFeature(getCollection(), initialFeature));
    SuccessResponse createResp = executeWrite(createFeatureReq);
    NakshaFeatureList createdFeatures = createResp.getFeatures();
    assertEquals(1, createdFeatures.size());
    return createdFeatures.get(0);
  }

  private NakshaFeature updateFeature(NakshaFeature feature) {
    feature.getProperties().put("foo", "bar");
    WriteRequest updateFeatureReq = new WriteRequest().add(new Write().updateFeature(getCollection(), feature, true));
    SuccessResponse updateResp = executeWrite(updateFeatureReq);
    NakshaFeatureList updatedFeatures = updateResp.getFeatures();
    assertEquals(1, updatedFeatures.size());
    return updatedFeatures.get(0);
  }

  private Version versionOf(NakshaFeature nakshaFeature) {
    return nakshaFeature.getTupleNumber().version;
  }
}
