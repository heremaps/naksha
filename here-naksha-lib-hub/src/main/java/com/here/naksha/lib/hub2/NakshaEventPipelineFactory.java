/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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
package com.here.naksha.lib.hub2;

import com.here.naksha.lib.core.EventPipeline;
import com.here.naksha.lib.core.INaksha;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class NakshaEventPipelineFactory implements EventPipelineFactory {

  private final @NotNull INaksha naksha;

  public NakshaEventPipelineFactory(@NotNull INaksha naksha) {
    this.naksha = naksha;
  }

  @Override
  public EventPipeline eventPipeline() {
    return new EventPipeline(naksha);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NakshaEventPipelineFactory that = (NakshaEventPipelineFactory) o;
    return Objects.equals(naksha, that.naksha);
  }

  @Override
  public int hashCode() {
    return Objects.hash(naksha);
  }
}
