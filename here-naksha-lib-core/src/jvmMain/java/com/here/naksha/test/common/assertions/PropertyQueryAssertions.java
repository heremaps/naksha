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
package com.here.naksha.test.common.assertions;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import naksha.model.request.query.*;

public class PropertyQueryAssertions {
  private final IPropertyQuery subject;

  public PropertyQueryAssertions(IPropertyQuery subject) {
    this.subject = subject;
  }

  public IPropertyQuery getQuery() {
    return this.subject;
  }

  public static PropertyQueryAssertions assertThatPropertyQuery(IPropertyQuery subject) {
    if (subject == null) throw new AssertionError("subject is null");
    return new PropertyQueryAssertions(subject);
  }

  public PropertyQueryAssertions hasOp(AnyOp expectedOpType) {
    if (!(subject instanceof PQuery)) throw new AssertionError("subject is not of type PQuery");
    if (!Objects.equals(expectedOpType, ((PQuery) subject).getOp())) throw new AssertionError("op is not of type PQuery");
    return this;
  }

  public PropertyQueryAssertions isPOr() {
    if (!(subject instanceof POr)) throw new AssertionError("subject is not of type POr");
    return this;
  }

  public PropertyQueryAssertions isPNot() {
    if (!(subject instanceof PNot)) throw new AssertionError("subject is not of type PNot");
    return this;
  }

  public PropertyQueryAssertions isPQuery(){
    if (!(subject instanceof PQuery)) throw new AssertionError("subject is not of type PQuery");
    return this;
  }

  public PropertyQueryAssertions hasProperty(Property expected) {
    java.util.List<String> path = new java.util.ArrayList<>();
    for (Object segment : expected.getPath().asList()) {
      path.add(segment.toString());
    }
    return hasProperty(path);
  }

  public PropertyQueryAssertions hasProperty(List<String> expected) {
    if (expected == null) throw new AssertionError("expected is null");
    if (!(subject instanceof PQuery)) throw new AssertionError("subject is not of type PQuery");
    if (!Objects.equals(expected, ((PQuery) subject).getProperty().getPath())) throw new AssertionError("expected is not of type PQuery");
    return this;
  }

  private <T> void assertArrayEquals(T[] a, T[] b) {
    if (a == b) return;
    if (a.length != b.length) throw new AssertionError("array lengths are not equal");
    for (int i = 0; i < a.length; i++) {
      if (!Objects.equals(a[i], b[i])) throw new AssertionError("array elements are not equal");
    }
  }

  public PropertyQueryAssertions hasPropertyWithPath(String... path) {
    if (!(subject instanceof PQuery)) throw new AssertionError("subject is not of type PQuery");
    assertArrayEquals(path, ((PQuery) subject).getProperty().getPath().toArray());
    return this;
  }

  public PropertyQueryAssertions hasValue(Number value) {
    if (!(subject instanceof PQuery)) throw new AssertionError("subject is not of type PQuery");
    if (!Objects.equals(value, ((PQuery) subject).getValue())) throw new AssertionError("value is not equal to subject.getValue()");
    return this;
  }

  public PropertyQueryAssertions hasValue(String value) {
    if (!(subject instanceof PQuery)) throw new AssertionError("subject is not of type PQuery");
    if (!Objects.equals(value, ((PQuery) subject).getValue())) throw new AssertionError("value is not equal to subject.getValue()");
    return this;
  }

  @SafeVarargs
  public final PropertyQueryAssertions hasChildrenThat(Consumer<PropertyQueryAssertions>... childrenAssertions) {
    if(subject instanceof PNot) {
      PNot pNotSubject = (PNot) subject;
      if (1 != childrenAssertions.length) throw new AssertionError("PNot can only have one child");
      PropertyQueryAssertions childAssertion = new PropertyQueryAssertions(pNotSubject.getQuery());
      childrenAssertions[0].accept(childAssertion);
    } else if(subject instanceof List) {
      List subjects = (List) subject;
      if (subjects.size() != childrenAssertions.length) throw new AssertionError("Expecting single assertion per property query");
      for (int i = 0; i < subjects.size(); i++) {
        try {
          final var subject = (IPropertyQuery) subjects.get(i);
          PropertyQueryAssertions childAssertion = new PropertyQueryAssertions(subject);
          childrenAssertions[i].accept(childAssertion);
        } catch (ClassCastException|NullPointerException e) {
          throw new AssertionError("subject["+i+"] is not of type IPropertyQuery");
        }
      }
    }
    return this;
  }
}
