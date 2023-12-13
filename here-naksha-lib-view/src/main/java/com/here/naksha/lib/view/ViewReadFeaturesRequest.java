package com.here.naksha.lib.view;

import com.here.naksha.lib.core.models.storage.ReadFeatures;

/**
 * TODO It would be better to extend something like ReadFeaturesWhere, or to composite it. In this class we don't need collectionIds from ReadFeatures - exposing them is confusing
 * but we still need same "where" operations
 */
public class ViewReadFeaturesRequest extends ReadFeatures {

  public ViewReadFeaturesRequest() {
  }

}
