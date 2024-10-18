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
package com.here.naksha.lib.psql;

import com.here.naksha.lib.core.models.storage.ForwardCursor;
import com.here.naksha.lib.core.models.storage.SuccessResult;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Signals a PostgresQL success.
 */
public class PsqlSuccess extends SuccessResult {

  PsqlSuccess(@Nullable ForwardCursor<?, ?> cursor) {
    this(cursor, null);
  }

  PsqlSuccess(@Nullable ForwardCursor<?, ?> cursor, @Nullable Map<String, Integer> originalFeaturesOrder) {
    this.cursor = cursor;
    this.originalFeaturesOrder = originalFeaturesOrder;
  }
}