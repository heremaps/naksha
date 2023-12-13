package com.here.naksha.lib.view;

import com.here.naksha.lib.core.storage.IStorage;

public class ViewLayer {

  private final IStorage storage;

  private final String collectionId;

  public ViewLayer(IStorage storage, String collectionId) {
    this.storage = storage;
    this.collectionId = collectionId;
  }

  public IStorage getStorage() {
    return storage;
  }

  public String getCollectionId() {
    return collectionId;
  }
}
