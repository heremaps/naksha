package com.here.naksha.app.service.http.ops;

import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import java.util.List;
import naksha.base.StringList;

public final class FeatureIdQueryUtil {

  private static final int IDS_LIMIT = 10_000;

  private FeatureIdQueryUtil() {}

  public static StringList featureIdsFromParams(QueryParameterList queryParameters){
    List<String> idsInParams = queryParameters.collectAllOf(PropKeys.SHORT_FEATURE_ID, IDS_LIMIT, String.class);
    return StringList.fromList(idsInParams);
  }
}
