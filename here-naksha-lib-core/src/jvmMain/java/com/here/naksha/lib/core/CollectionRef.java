package com.here.naksha.lib.core;

import com.here.naksha.lib.core.models.naksha.Space;
import naksha.base.AnyObject;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.base.PlatformType;
import naksha.model.Naksha;
import naksha.model.objects.NakshaCollection;
import naksha.model.urn.HereUrn;
import naksha.model.urn.NakshaCollectionUrn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static naksha.base.NakshaBaseKt.String_TYPE;
import static naksha.base.NakshaError.STORAGE_NOT_FOUND;
import static naksha.base.Platform.forClass;

/**
 * A reference to a collection, using individual properties for storage-id, map-id, and collection-id, as declared within storage handlers, {@link Space}, or potentially other objects.
 *
 * <p>This is generally the same as a {@link NakshaCollectionUrn}, but it allows to be stored alternatively as JSON object to simplify the usage for consumers.
 * @since 3.0
 */
public class CollectionRef extends AnyObject {

  /**
   * Creates an empty collection reference.
   * @since 3.0
   */
  public CollectionRef() {}

  /**
   * Create an initialized collection reference.
   *
   * <p>As these references are normally used by storage handlers, only values that are not fixed by the storage-handler need to be present, leave others at <code>null</code>, because they will be ignored anyway.
   * @param storageId The storage-id.
   * @param mapId The map-id.
   * @param collectionId The collection-id.
   * @since 3.0
   */
  public CollectionRef(@Nullable String storageId, @Nullable String mapId, @Nullable String collectionId) {
    setStorageId(storageId);
    setMapId(mapId);
    setId(collectionId);
  }

  /**
   * Create a collection reference for the given {@link NakshaCollection}.
   *
   * @param collection the Naksha collection for which to generate a collection reference.
   * @throws NakshaException with {@link NakshaError#STORAGE_NOT_FOUND}, if the storage of the given collection can't be found.
   */
  public CollectionRef(@NotNull NakshaCollection collection) {
    final var storageNumber = collection.getStorageNumber();
    if (storageNumber == null) {
      throw new NakshaException(STORAGE_NOT_FOUND, "Storage number is null");
    }
    final var storage = Naksha.useStorageByNumber(storageNumber); // throws STORAGE_NOT_FOUND
    setStorageId(storage.getId());
    setMapId(collection.getMapId());
    setId(collection.getId());
  }

  public static final PlatformType<CollectionRef> TYPE = forClass(CollectionRef.class);

  public @Nullable String getId() {
    return getAs("id", String_TYPE);
  }

  public void setId(@Nullable String id) {
    set("id", id);
  }

  public @Nullable String getMapId() {
    return getAs("mapId", String_TYPE);
  }

  public void setMapId(@Nullable String mapId) {
    set("mapId", mapId);
  }

  public @Nullable String getStorageId() {
    return getAs("storageId", String_TYPE);
  }

  public void setStorageId(@Nullable String storageId) {
    set("storageId", storageId);
  }

  /**
   * Restore a {@link CollectionRef} from a string, which must be a URN.
   * @param urn The string in URN format.
   * @return The collection reference as {@link CollectionRef}.
   */
  public static @NotNull CollectionRef fromString(@NotNull String urn) {
    return fromUrn(new NakshaCollectionUrn( new HereUrn(urn)));
  }

  public static @NotNull CollectionRef fromUrn(@NotNull NakshaCollectionUrn urn) {
    final var colRef = new CollectionRef();
    colRef.setId(urn.getCollectionId());
    colRef.setMapId(urn.getMapId());
    colRef.setStorageId(urn.getStorageId());
    return colRef;
  }

  public @NotNull String toUrn() {
    final var sb = new StringBuilder();
    final var storageId = getStorageId();
    final var mapId = getMapId();
    final var collectionId = getId();
    //         urn:here:{branch}:{domain}:{feature-type}:{content-id}
    // Syntax: urn:here:{storage}.{map}:naksha:Collection:{id}
    sb.append("urn:here:");
    sb.append(storageId != null ? storageId : "");
    sb.append('.');
    sb.append(mapId != null ? mapId : "");
    sb.append(":naksha:");
    sb.append(NakshaCollection.TYPE_STRING);
    sb.append(':');
    sb.append(collectionId);
    return sb.toString();
  }

  /**
   * Stringify the collection reference into a URN, see {@link naksha.model.urn.NakshaCollectionUrn}.
   * @return the collection reference as URN.
   */
  public @NotNull String toString() {
    return toUrn();
  }

}