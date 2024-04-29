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
package com.here.naksha.app.service.http.auth.actions;

import com.here.naksha.app.service.http.auth.JWTPayload;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.RoutingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JwtUtil {

  public static final String JWT = "jwt";

  public static @Nullable JWTPayload getOrCreateJWT(final @NotNull RoutingContext context) {
    JWTPayload payload = context.get(JWT);
    if (payload == null && context.user() != null) {
      payload = DatabindCodec.mapper().convertValue(context.user().principal(), JWTPayload.class);
      context.put(JWT, payload);
    }
    return payload;
  }
}
