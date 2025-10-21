/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.naksha.lib.core.models.naksha;

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static com.here.naksha.lib.core.models.PluginCache.getEventHandlerConstructor;

import com.here.naksha.lib.core.IEventHandler;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.lambdas.Fe3;
import com.here.naksha.lib.core.models.PluginCache;
import naksha.base.JvmAnyObjectUtil;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A configured event handler.
 */
@AvailableSince(NakshaVersion.v2_0_3)
public class EventHandlerConfig extends NakshaFeature {

  private static final @NotNull Logger logger = LoggerFactory.getLogger(EventHandlerConfig.class);

  @AvailableSince(NakshaVersion.v2_0_7)
  public static final String CLASS_NAME = "className";

  @AvailableSince(NakshaVersion.v2_0_7)
  public @NotNull String getClassName() {
    return JvmAnyObjectUtil.getProperty(this, CLASS_NAME, String.class);
  }

  @AvailableSince(NakshaVersion.v2_0_7)
  public void setClassName(@NotNull String className) {
    setRaw(CLASS_NAME, className);
  }


  @AvailableSince(NakshaVersion.v2_0_7)
  public static final String EXTENSION_ID = "extensionId";

  @AvailableSince(NakshaVersion.v2_0_3)
  public static final String ACTIVE = "active";

  /**
   * The unique identifier of the extension that hosts the handler, referred by the {@link #getClassName() className}.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public @Nullable String getExtensionId() {
    return JvmAnyObjectUtil.getProperty(this, EXTENSION_ID, String.class);
  }

  @AvailableSince(NakshaVersion.v2_0_7)
  public void setExtensionId(@Nullable String extensionId) {
    setRaw(EXTENSION_ID, extensionId);
  }

  /**
   * Whether this connector is active. If set to false, the handler will not be added into the event pipelines of spaces. So all spaces
   * using this connector will bypass this connector. If the connector configures the storage, all requests to spaces using the connector as
   * storage will fail.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public boolean isActive() {
    return JvmAnyObjectUtil.getOrSetProperty(this, ACTIVE, true);
  }

  @AvailableSince(NakshaVersion.v2_0_7)
  public void setActive(boolean active) {
    setRaw(ACTIVE, active);
  }

  /**
   * Do not use anymore, please call {@link PluginCache#getEventHandlerConstructor(String, Class, Class)} and create the instance yourself.
   */
  @Deprecated
  @AvailableSince(NakshaVersion.v2_0_7)
  // TODO (CASL-780): strongly consider removing this
  public @NotNull IEventHandler newInstance(@NotNull INaksha naksha) {
    return newInstance(naksha, null);
  }

  /**
   * Do not use anymore, please call {@link PluginCache#getEventHandlerConstructor(String, Class, Class)} and create the instance yourself.
   *
   * @param naksha      the reference to the Naksha-Hub that wants to have the instance.
   * @param eventTarget Type of EventTarget object. If null then Space(Space.class) type will be used as default value
   */
  @Deprecated
  @AvailableSince(NakshaVersion.v2_0_7)
  // TODO (CASL-780): strongly consider removing this
  public @NotNull IEventHandler newInstance(@NotNull INaksha naksha, @Nullable EventTarget<?> eventTarget) {
    Class<?> eventTargetClass = eventTarget == null ? Space.class : eventTarget.getClass();
    final Fe3<IEventHandler, INaksha, EventHandlerConfig, EventTarget<?>> constructor;
    final String extensionId = getExtensionId();
    try {
      if (extensionId == null || extensionId.isEmpty() || "null".equalsIgnoreCase(extensionId)) {
        //noinspection unchecked
        constructor = (Fe3<IEventHandler, INaksha, EventHandlerConfig, EventTarget<?>>)
            getEventHandlerConstructor(getClassName(), EventHandlerConfig.class, eventTargetClass);
      } else {
        ClassLoader extClassLoader = naksha.getClassLoader(extensionId);
        //noinspection unchecked
        constructor = (Fe3<IEventHandler, INaksha, EventHandlerConfig, EventTarget<?>>) getEventHandlerConstructor(
            getClassName(), EventHandlerConfig.class, eventTargetClass, extensionId, extClassLoader);
      }
      return constructor.call(naksha, this, eventTarget);
    } catch (Exception e) {
      logger.error(
          "Exception loading constructor for EventHandler id: {}, extensionId: {}.", getId(), extensionId, e);
      throw unchecked(e);
    }
  }

  @Override
  public @NotNull String toString() {
    return "EventHandler{" + "id='" + getId() + '\'' + "className='" + getClassName() + '\'' + '}';
  }
}
