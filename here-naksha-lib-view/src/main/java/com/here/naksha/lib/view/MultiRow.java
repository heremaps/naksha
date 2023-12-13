package com.here.naksha.lib.view;

import com.here.naksha.lib.core.models.storage.FeatureCodec;

import java.util.List;

public class MultiRow<FEATURE, CODEC extends FeatureCodec<FEATURE, CODEC>> {

  private final List<SingleStorageRow<FEATURE, CODEC>> multiRows;

  public MultiRow(List<SingleStorageRow<FEATURE, CODEC>> multiRows) {
    this.multiRows = multiRows;
  }
}
