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

public class AssertionIPropertyQuery {
  private final IPropertyQuery subject;

  public AssertionIPropertyQuery(IPropertyQuery subject) {
    this.subject = subject;
  }

  public IPropertyQuery getQuery() {
    return this.subject;
  }

  public static AssertionIPropertyQuery assertThatOperation(IPropertyQuery subject) {
    assertNotNull(subject);
    return new AssertionIPropertyQuery(subject);
  }

  public AssertionIPropertyQuery hasType(AnyOp expectedOpType) {
    assertInstanceOf(PQuery.class, subject);
    assertEquals(expectedOpType, ((PQuery) subject).getOp());
    return this;
  }

  public AssertionIPropertyQuery isPOr() {
    assertInstanceOf(POr.class, subject);
    return this;
  }

  public AssertionIPropertyQuery hasProperty(Property expected) {
    return hasProperty(expected.getPath());
  }

  public AssertionIPropertyQuery hasProperty(List<String> expected) {
    assertNotNull(expected);
    assertInstanceOf(PQuery.class, subject);
    assertEquals(expected, ((PQuery) subject).getProperty().getPath());
    return this;
  }

  public AssertionIPropertyQuery hasPRefWithPath(String[] path) {
    assertInstanceOf(PQuery.class, subject);
    assertArrayEquals(path, ((PQuery) subject).getProperty().getPath().toArray());
    return this;
  }

  public AssertionIPropertyQuery hasValue(Number value) {
    assertInstanceOf(PQuery.class, subject);
    assertEquals(value, ((PQuery) subject).getValue());
    return this;
  }

  public AssertionIPropertyQuery hasValue(String value) {
    assertInstanceOf(PQuery.class, subject);
    assertEquals(value, ((PQuery) subject).getValue());
    return this;
  }

  @SafeVarargs
  public final AssertionIPropertyQuery hasChildrenThat(Consumer<AssertionIPropertyQuery>... childrenAssertions) {
    assertInstanceOf(List.class, subject, "Expected multiple operations");
    List<IPropertyQuery> subjects = (List) subject;
    assertEquals(subjects.size(), childrenAssertions.length, "Expecting single assertion per property query");
    for (int i = 0; i < subjects.size(); i++) {
      AssertionIPropertyQuery childAssertion = new AssertionIPropertyQuery(subjects.get(i));
      childrenAssertions[i].accept(childAssertion);
    }
    return this;
  }
}
