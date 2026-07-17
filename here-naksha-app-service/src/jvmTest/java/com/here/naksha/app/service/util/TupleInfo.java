package com.here.naksha.app.service.util;

import com.here.naksha.app.common.ApiTest;
import naksha.base.Int64;
import naksha.base.Platform;
import naksha.model.Action;
import naksha.model.Naksha;
import naksha.model.TupleNumber;
import naksha.model.Version;
import org.jetbrains.annotations.NotNull;

import static naksha.base.Platform.longToInt64;

public final class TupleInfo {
  /**
   * Create a new collection test info from the given collection identifier, using {@link ApiTest} static values for database and catalog.
   * @param collectionId the collection identifier for which to create info.
   * @throws IllegalStateException if the {@link ApiTest#databaseId} or {@link ApiTest#catalogId} are null.
   * @throws IllegalArgumentException if the given {@code collectionId} is {@code null}.
   */
  public TupleInfo(String collectionId) {
    final var databaseId = ApiTest.databaseId;
    if (databaseId == null) throw new IllegalStateException("databaseId is null");
    final var catalogId = ApiTest.catalogId;
    if (catalogId == null) throw new IllegalStateException("catalogId is null");
    if (collectionId == null) throw new IllegalArgumentException("collectionId is null");
    this.databaseId = databaseId;
    this.catalogId = catalogId;
    this.collectionId = collectionId;
    databaseNumber = Naksha.databaseNumber(databaseId);
    catalogNumber = Naksha.catalogNumber(catalogId);
    collectionNumber = Naksha.collectionNumber(collectionId);
  }

  public TupleInfo(@NotNull String databaseId, @NotNull String catalogId, @NotNull String collectionId) {
    this.databaseId = databaseId;
    this.catalogId = catalogId;
    this.collectionId = collectionId;
    databaseNumber = Naksha.databaseNumber(databaseId);
    catalogNumber = Naksha.catalogNumber(catalogId);
    collectionNumber = Naksha.collectionNumber(collectionId);
  }

  public final @NotNull String databaseId;
  public final @NotNull String catalogId;
  public final @NotNull String collectionId;
  public final @NotNull Int64 databaseNumber;
  public final int catalogNumber;
  public final int collectionNumber;

  public int seq = 1;

  /**
   * Creates a new virtual tuple-number for tests.
   * @param featureId the feature identifier for which to generate a new tuple number.
   * @param action the action.
   * @return the new unique virtual tuple-number.
   */
  public @NotNull TupleNumber newTupleNumber(@NotNull String featureId, @NotNull Action action) {
    final Version version = Version.now(longToInt64(seq++), action);
    final Int64 featureNumber = Naksha.featureNumber(featureId);
    return new TupleNumber(databaseNumber, catalogNumber, collectionNumber, featureNumber, version.number);
  }
}