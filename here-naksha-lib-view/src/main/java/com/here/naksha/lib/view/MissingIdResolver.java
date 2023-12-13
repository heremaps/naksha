package com.here.naksha.lib.view;

import com.here.naksha.lib.core.models.storage.FeatureCodec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Defines the strategy for missing feature in one of the layers.
 *
 * Consider this example:
 * Result from Storage A: [F_1, F_2, F_3, F_4]
 * Result from Storage B: [F_2, F_4]
 * Result from Storage C: [F_3, F_5]
 *
 * Here are couple examples of possible implementations:
 * 1. Ignore fetching more - perfect when your basic query is by ID (it doesn't make sense to search it again).
 * 2. Calculate missing features' IDs and try to fetch them by ID - good when your basic query is i.e. by Bbox
 * 3. Fetch only if feature is missing in specific Storage and from specific Storage.
 */
public interface MissingIdResolver<FEATURE, CODEC extends FeatureCodec<FEATURE, CODEC>> {

  /**
   * True - turns off fetching missing features by ID.
   *
   * @return
   */
  boolean skip();

  /**
   * Returns Pair<Layer,FEATURE_ID> that will be fetched in second query.
   * As an input you get feature in all layers, you can decide whether you want to query again (by ID) only
   * in specific layer or in all with missing feature. <br><br>
   *
   * You can also implement your own way of ID calculation (for fetching) - maybe your json contains information about previous id:
   * { "id": "2345", "old_id": "11223" }
   * in this case you can try fetching by "old_id"
   *
   * @param multipleResults
   * @return
   */
  Pair<ViewLayer,String> idsToSearch(ViewLayerRow<FEATURE, CODEC>... multipleResults);
}
