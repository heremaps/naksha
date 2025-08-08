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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
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
    assertNotNull(subject);
    return new PropertyQueryAssertions(subject);
  }

  public PropertyQueryAssertions hasOp(AnyOp expectedOpType) {
    assertInstanceOf(PQuery.class, subject);
    assertEquals(expectedOpType, ((PQuery) subject).getOp());
    return this;
  }

  public PropertyQueryAssertions isPOr() {
    assertInstanceOf(POr.class, subject);
    return this;
  }

  public PropertyQueryAssertions isPNot() {
    assertInstanceOf(PNot.class, subject);
    return this;
  }

  public PropertyQueryAssertions isPQuery(){
    assertInstanceOf(PQuery.class, subject);
    return this;
  }

  public PropertyQueryAssertions hasProperty(Property expected) {
    return hasProperty(expected.getPath());
  }

  public PropertyQueryAssertions hasProperty(List<String> expected) {
    assertNotNull(expected);
    assertInstanceOf(PQuery.class, subject);
    assertEquals(expected, ((PQuery) subject).getProperty().getPath());
    return this;
  }

  public PropertyQueryAssertions hasPropertyWithPath(String... path) {
    assertInstanceOf(PQuery.class, subject);
    assertArrayEquals(path, ((PQuery) subject).getProperty().getPath().toArray());
    return this;
  }

  public PropertyQueryAssertions hasValue(Number value) {
    assertInstanceOf(PQuery.class, subject);
    assertEquals(value, ((PQuery) subject).getValue());
    return this;
  }

  public PropertyQueryAssertions hasValue(String value) {
    assertInstanceOf(PQuery.class, subject);
    assertEquals(value, ((PQuery) subject).getValue());
    return this;
  }

  @SafeVarargs
  public final PropertyQueryAssertions hasChildrenThat(Consumer<PropertyQueryAssertions>... childrenAssertions) {
    if(subject instanceof PNot pNotSubject) {
      assertEquals(1, childrenAssertions.length, "PNot can only have one child");
      PropertyQueryAssertions childAssertion = new PropertyQueryAssertions(pNotSubject.getQuery());
      childrenAssertions[0].accept(childAssertion);
    } else if(subject instanceof List subjects) {
      assertEquals(subjects.size(), childrenAssertions.length, "Expecting single assertion per property query");
      for (int i = 0; i < subjects.size(); i++) {
        assertInstanceOf(IPropertyQuery.class, subjects.get(i));
        PropertyQueryAssertions childAssertion = new PropertyQueryAssertions((IPropertyQuery) subjects.get(i));
        childrenAssertions[i].accept(childAssertion);
      }
    }
    return this;
  }
}
