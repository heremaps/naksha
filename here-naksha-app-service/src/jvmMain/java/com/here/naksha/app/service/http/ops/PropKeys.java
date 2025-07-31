package com.here.naksha.app.service.http.ops;

import static naksha.model.request.query.Property.PROPERTIES;

import naksha.model.objects.NakshaProperties;

/**
 * Features' properties (short & full version) that are supported by Hub API
 */
class PropKeys {

  static final String NULL_PROP_VALUE = ".null";
  static final String SHORT_PROP_PREFIX = "p.";
  static final String FULL_PROP_PREFIX = PROPERTIES + ".";
  static final String SHORT_XYZ_PROP_PREFIX = "f.";
  static final String FULL_XYZ_PROP_PREFIX = FULL_PROP_PREFIX + NakshaProperties.XYZ_KEY + ".";
  static final String SHORT_FEATURE_ID = "f.id";

  private PropKeys() {}
}
