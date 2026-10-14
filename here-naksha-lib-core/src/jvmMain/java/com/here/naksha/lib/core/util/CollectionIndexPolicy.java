package com.here.naksha.lib.core.util;

import naksha.model.objects.Index;
import naksha.model.objects.IndexList;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.StandardIndices;
import naksha.model.objects.StandardMembers;
import naksha.model.objects.XyzIndices;
import org.jetbrains.annotations.NotNull;

public final class CollectionIndexPolicy {

  private CollectionIndexPolicy() {}

  /**
   * The indices of collections created by the Hub without explicit indices. Existing collections are verified against
   * this set when a Space or Handler is saved, so changing it requires a migration of existing collections.
   */
  public static @NotNull IndexList hubSlimIndices() {
    return IndexList.of(
        XyzIndices.XyzTags,
        StandardIndices.Geometry,
        new Index("fn_nv", StandardMembers.FeatureNumber.getName(), StandardMembers.NextVersion.getName()));
  }

  public static @NotNull NakshaCollection normalizeForHubCreation(
      final @NotNull String collectionId,
      final @NotNull String catalogId) {
    final NakshaCollection collection = new NakshaCollection();
    collection.setId(collectionId);
    collection.setCatalogId(catalogId);
    return normalizeForHubCreation(collection);
  }

  public static @NotNull NakshaCollection normalizeForHubCreation(
      final @NotNull NakshaCollection collection) {
    if (collection.getIndices() == null) {
      collection.setIndices(hubSlimIndices());
    }
    return collection;
  }

  /**
   * Prepares a Space or Handler collection definition for the storage. Without custom index mappings, the given
   * definition is normalized in place, as before. With mappings, a copy is returned that holds the generated members
   * and indices, but not the mapping itself, so the configuration stays unchanged.
   */
  public static @NotNull NakshaCollection toNativeCollection(
      final @NotNull NakshaCollection definition,
      final @NotNull String collectionId,
      final @NotNull String catalogId) {
    final boolean mapped = CustomIndexMappingCompiler.hasMappings(definition);
    final NakshaCollection collection = mapped ? definition.copy(true) : definition;
    collection.setId(collectionId);
    collection.setCatalogId(catalogId);
    normalizeForHubCreation(collection);
    if (mapped) {
      CustomIndexMappingCompiler.expand(collection);
    }
    collection.removeRaw("customIndexMapping");
    return collection;
  }
}
