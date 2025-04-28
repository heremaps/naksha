package com.here.naksha.app.service.util;

import static com.here.naksha.lib.core.HubInternalIdentifiers.EVENT_HANDLERS;
import static com.here.naksha.lib.core.HubInternalIdentifiers.SPACES;
import static com.here.naksha.lib.core.HubInternalIdentifiers.STORAGES;

import com.here.naksha.lib.core.INaksha;
import naksha.base.StringList;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;

/**
 * Util class responsible for creating requests to Naksha Admin collections.
 * It relies on INaksha and its admin map.
 */
public class NakshaAdminRequestUtil {
  private NakshaAdminRequestUtil() {}

  // STORAGES:
  public static ReadFeatures getStorageRequest(INaksha naksha, String storageId) {
    return getAdminResourceRequest(naksha, STORAGES, storageId);
  }

  public static ReadFeatures getStoragesRequest(INaksha naksha) {
    return getAdminResourcesRequest(naksha, STORAGES);
  }

  public static WriteRequest createStorageRequest(INaksha naksha, NakshaStorage nakshaStorage) {
    return createAdminResourceRequest(naksha, STORAGES, nakshaStorage);
  }

  public static WriteRequest updateStoragesRequest(INaksha naksha, NakshaStorage nakshaStorage) {
    return updateAdminResourceRequest(naksha, STORAGES, nakshaStorage);
  }

  // SPACES:
  public static WriteRequest deleteSpaceRequest(INaksha naksha, String spaceId) {
    return deleteAdminResourceRequest(naksha, SPACES, spaceId);
  }

  // EVENT HANDLERS:
  public static WriteRequest deleteEventHandlerRequest(INaksha naksha, String handlerId) {
    return deleteAdminResourceRequest(naksha, EVENT_HANDLERS, handlerId);
  }

  private static ReadFeatures getAdminResourcesRequest(INaksha naksha, String resourceCollection) {
    ReadFeatures readFeatures = new ReadFeatures();
    readFeatures.setMapId(naksha.getAdminMapId());
    readFeatures.addCollectionId(resourceCollection);
    return readFeatures;
  }

  private static ReadFeatures getAdminResourceRequest(INaksha naksha, String resourceCollection, String resourceId) {
    ReadFeatures resourcesReq = getAdminResourcesRequest(naksha, resourceCollection);
    resourcesReq.setFeatureIds(new StringList(resourceId));
    return resourcesReq;
  }

  private static WriteRequest createAdminResourceRequest(INaksha naksha, String resourceCollection, NakshaFeature resource){
    WriteRequest writeRequest = new WriteRequest();
    writeRequest.add(new Write().createFeature(naksha.getAdminMapId(), resourceCollection, resource));
    return writeRequest;
  }

  private static WriteRequest updateAdminResourceRequest(INaksha naksha, String resourceCollection, NakshaFeature resource){
    WriteRequest writeRequest = new WriteRequest();
    writeRequest.add(new Write().updateFeature(naksha.getAdminMapId(), resourceCollection, resource, false));
    return writeRequest;
  }

  private static WriteRequest deleteAdminResourceRequest(INaksha naksha, String resourceCollection, String resourceId){
    WriteRequest writeRequest = new WriteRequest();
    writeRequest.add(new Write().deleteFeatureById(naksha.getAdminMapId(), resourceCollection, resourceId));
    return writeRequest;
  }
}
