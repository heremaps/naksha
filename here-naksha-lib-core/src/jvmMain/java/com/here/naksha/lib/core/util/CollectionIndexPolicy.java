package com.here.naksha.lib.core.util;

import naksha.model.objects.Index;
import naksha.model.objects.IndexList;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.Member;
import naksha.model.objects.MemberList;
import naksha.model.objects.XyzMembers;
import naksha.model.objects.XyzIndices;
import org.jetbrains.annotations.NotNull;

public final class CollectionIndexPolicy {

  private CollectionIndexPolicy() {}

  /**
   * The indices of collections created by the Hub without explicit indices, see {@link XyzIndices#HUB_SLIM_INDICES}.
   */
  public static @NotNull IndexList hubSlimIndices() {
    final IndexList indices = new IndexList();
    for (Index index : XyzIndices.HUB_SLIM_INDICES) indices.add(index.copy(true));
    return indices;
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
    collection.validateLayoutDeclarations();
    if (collection.getIndices() == null) {
      final MemberList members = new MemberList();
      for (Member member : XyzMembers.ALL) members.add(member.copy(true));
      members.verify();
      collection.setMembers(members);
      collection.setIndices(hubSlimIndices());
    }
    return collection;
  }
}
