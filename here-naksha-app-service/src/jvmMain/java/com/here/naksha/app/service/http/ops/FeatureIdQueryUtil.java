package com.here.naksha.app.service.http.ops;

import static com.here.naksha.common.http.apis.ApiParamsConst.SHORT_FEATURE_ID;

import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import java.util.List;
import naksha.base.StringList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FeatureIdQueryUtil {

  private static final int IDS_LIMIT = 10_000;

  private FeatureIdQueryUtil() {
  }

  public static @NotNull StringList featureIdsFromParams(@Nullable QueryParameterList queryParameters) {
    if (queryParameters == null) {
      return new StringList();
    }
    List<String> idsInParams = queryParameters.collectAllOf(SHORT_FEATURE_ID, IDS_LIMIT, String.class);
    return StringList.fromList(idsInParams);
  }
}
