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
package com.here.naksha.lib.handlers.util;

import naksha.model.request.query.*;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class PropertyOperationUtil {

  private PropertyOperationUtil() {}

  public static void transformPropertyInPropertyOperationTree(
      IPropertyQuery rootPropertyOperation, Function<PQuery, Optional<PQuery>> transformingFunction) {
    replacePropertyInPropertyOperationTree(rootPropertyOperation, transformingFunction);
  }

  private static void replacePropertyInPropertyOperationTree(
      IPropertyQuery propertyOperation, Function<PQuery, Optional<PQuery>> transformingFunction) {

    if (propertyOperation instanceof PAnd pAnd) {
      pAnd.forEach(
          iPropertyQuery -> replacePropertyInPropertyOperationTree(iPropertyQuery, transformingFunction));
    } else if (propertyOperation instanceof POr pOr) {
      pOr.forEach(iPropertyQuery -> replacePropertyInPropertyOperationTree(iPropertyQuery, transformingFunction));
    } else if (propertyOperation instanceof PNot pNot) {
      replacePropertyInPropertyOperationTree(pNot.getQuery(), transformingFunction);
    } else if (propertyOperation instanceof PQuery pQuery) {
      AtomicReference<PQuery> transformed = new AtomicReference<>();
      transformingFunction.apply(pQuery).ifPresent(transformed::set);
      pQuery.setProperty(transformed.get().getProperty());
      pQuery.setOp(transformed.get().getOp());
      pQuery.setValue(transformed.get().getValue());
    } // TODO do we throw unsupported of unknown query here?
  }
}
