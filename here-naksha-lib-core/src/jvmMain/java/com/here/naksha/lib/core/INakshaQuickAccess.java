package com.here.naksha.lib.core;

import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfigList;
import com.here.naksha.lib.core.models.naksha.Space;
import naksha.base.NakshaError;
import naksha.base.StringList;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import org.jetbrains.annotations.NotNull;

/**
 * An interface to simplify access to Naksha-Hub.
 *
 * <p>All authorizations are done using the current {@link NakshaContext#currentContext()}, the {@link SessionOptions SessionOptions} are as well created using the {@link NakshaContext} via {@link SessionOptions#from()}.
 * @since 3.0
 */
public interface INakshaQuickAccess {
  /**
   * Query the current <code>HEAD</code> state of a space.
   * @param spaceId The <code>id</code> of the space to query.
   * @return the {@link Space}.
   * @since 3.0
   */
  @NotNull ValueOrErr<Space> getSpaceById(@NotNull String spaceId);

  /**
   * Query the current HEAD state of the {@link EventHandlerConfig}'s with the given identifiers.
   * @param eventHandlerIds The <code>ids</code> of the {@link EventHandlerConfig}'s to query.
   * @param returnEmptyList If an empty list a valid response, otherwise {@link NakshaError#NOT_FOUND} is returned.
   * @return the list of {@link EventHandlerConfig}.
   * @since 3.0
   */
  @NotNull ValueOrErr<EventHandlerConfigList> getEventHandlerConfigsById(@NotNull StringList eventHandlerIds, boolean returnEmptyList);
}