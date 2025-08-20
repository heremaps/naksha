package com.here.naksha.lib.core;

import com.here.naksha.lib.core.models.naksha.EventHandlerConfigList;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.naksha.SpaceList;
import naksha.base.StringList;
import naksha.model.SessionOptions;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.SuccessResponse;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;
import static naksha.base.NakshaError.ILLEGAL_STATE;
import static naksha.base.NakshaError.NOT_FOUND;

/**
 * The default implementation of the {@link INakshaQuickAccess} interface.
 * @since 3.0
 */
public class NakshaQuickAccess implements INakshaQuickAccess {

  /**
   * Create a new default quick access implementation.
   * @param naksha The Naksha-Hub API to use.
   * @since 3.0
   */
  public NakshaQuickAccess(@NotNull INaksha naksha) {
    this.naksha = requireNonNull(naksha);
  }

  private final @NotNull INaksha naksha;

  @Override
  public @NotNull ValueOrErr<Space> getSpaceById(@NotNull String spaceId) {
    final var sessionOptions = SessionOptions.from();
    final var adminStorage = naksha.getAdminStorage();
    return adminStorage.useReadSession(sessionOptions, (session) -> {
      final var req = new ReadFeatures();
      req.setMapId(naksha.getAdminMapId());
      req.addCollectionId(HubInternalIdentifiers.SPACES);
      req.setFeatureIds(new StringList(spaceId));
      final var resp = session.execute(req);
      if (resp instanceof ErrorResponse errorResponse) {
        return new ValueOrErr<>(errorResponse);
      }
      if (resp instanceof SuccessResponse successResponse) {
        final var spaces = successResponse.getFeatures(SpaceList.TYPE);
        if (!spaces.isEmpty()) {
          final var space = spaces.getFirst();
          if (space != null) return new ValueOrErr<>(spaces.getFirst());
        }
        return new ValueOrErr<>(new ErrorResponse(NOT_FOUND, "Space not found: " + spaceId));
      }
      return new ValueOrErr<>(new ErrorResponse(ILLEGAL_STATE, "Unexpected response type: " + resp.getClass().getName()));
    });
  }

  @Override
  public @NotNull ValueOrErr<EventHandlerConfigList> getEventHandlerConfigsById(@NotNull StringList eventHandlerIds, boolean returnEmptyList) {
    final var sessionOptions = SessionOptions.from();
    final var adminStorage = naksha.getAdminStorage();
    return adminStorage.useReadSession(sessionOptions, (session) -> {
      final var req = new ReadFeatures();
      req.setMapId(naksha.getAdminMapId());
      req.addCollectionId(HubInternalIdentifiers.EVENT_HANDLERS);
      req.setFeatureIds(eventHandlerIds);
      final var resp = session.execute(req);
      if (resp instanceof ErrorResponse errorResponse) {
        return new ValueOrErr<>(errorResponse);
      }
      if (resp instanceof SuccessResponse successResponse) {
        final var handlers = successResponse.getFeatures(EventHandlerConfigList.TYPE);
        if (!handlers.isEmpty() || returnEmptyList) {
          return new ValueOrErr<>(handlers);
        }
        return new ValueOrErr<>(new ErrorResponse(NOT_FOUND, "No event-handlers found"));
      }
      return new ValueOrErr<>(new ErrorResponse(ILLEGAL_STATE, "Unexpected response type: " + resp.getClass().getName()));
    });
  }
}
