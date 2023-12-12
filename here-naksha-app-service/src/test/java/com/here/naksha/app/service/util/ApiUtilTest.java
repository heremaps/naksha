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
package com.here.naksha.app.service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.here.naksha.app.service.http.apis.ApiUtil;
import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import com.here.naksha.lib.core.models.storage.OpType;
import com.here.naksha.lib.core.models.storage.POp;
import com.here.naksha.lib.core.models.storage.POpType;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApiUtilTest {

  @Test
  void testBuildOperationForTagsQueryParam() {
    final QueryParameterList params = new QueryParameterList("tags=one"
        + "&tags=two,three"
        + "&tags=four+five"
        + "&tags=six,seven,eight+nine"
        + "&tags=ten+eleven,twelve,thirteen"
        + "&tags=fourteen");
    final POp op = ApiUtil.buildOperationForTagsQueryParam(params);
    assertThatOperation(op).hasType(OpType.OR);
    final List<POp> orList = op.children();

    // ensure there are total 8 operations
    assertNotNull(orList, "Expected multiple OR operations");
    assertEquals(8, orList.size(), "Expected total 8 OR operations");
    int innerOpsInd = 0;

    // validate 1st operation uses EXISTS
    assertThatOperation(orList.get(innerOpsInd++)).hasType(POpType.EXISTS).hasTagName("one");

    // validate 2nd operation uses OR
    assertThatOperation(orList.get(innerOpsInd++))
        .hasType(POpType.OR)
        .hasChildrenThat(second -> second.existsWithTagName("two"), third -> third.existsWithTagName("three"));

    // validate 3rd operation uses AND
    assertThatOperation(orList.get(innerOpsInd++))
        .hasType(OpType.AND)
        .hasChildrenThat(fourth -> fourth.existsWithTagName("four"), fifth -> fifth.existsWithTagName("five"));

    // validate 4th operation uses OR
    assertThatOperation(orList.get(innerOpsInd++))
        .hasType(OpType.OR)
        .hasChildrenThat(
            sixth -> sixth.existsWithTagName("six"), seventh -> seventh.existsWithTagName("seven"));

    // validate 5th operation uses AND
    assertThatOperation(orList.get(innerOpsInd++))
        .hasType(OpType.AND)
        .hasChildrenThat(eighth -> eighth.existsWithTagName("eight"), ninth -> ninth.existsWithTagName("nine"));

    // validate 6th operation uses AND
    assertThatOperation(orList.get(innerOpsInd++))
        .hasType(OpType.AND)
        .hasChildrenThat(
            tenth -> tenth.existsWithTagName("ten"), eleventh -> eleventh.existsWithTagName("eleven"));

    // validate 7th operation uses OR
    assertThatOperation(orList.get(innerOpsInd++))
        .hasType(OpType.OR)
        .hasChildrenThat(
            twelfth -> twelfth.existsWithTagName("twelve"),
            thirteenth -> thirteenth.existsWithTagName("thirteen"));

    // validate 8th operation uses EXISTS
    assertThatOperation(orList.get(innerOpsInd)).existsWithTagName("fourteen");
  }

  private static Stream<Arguments> simpleTagsSample() {
    return Stream.of(
        tagQuerySpec("tags=x", op -> op.existsWithTagName("x"), "only 'x'"),
        tagQuerySpec(
            "tags=this,that",
            op -> op.hasType(OpType.OR)
                .hasChildrenThat(
                    child -> child.existsWithTagName("this"),
                    child -> child.existsWithTagName("that")),
            "'this' or 'that'"),
        tagQuerySpec(
            "tags=foo+bar",
            op -> op.hasType(OpType.AND)
                .hasChildrenThat(
                    child -> child.existsWithTagName("foo"),
                    child -> child.existsWithTagName("bar")),
            "'foo' and 'bar'"),
        tagQuerySpec(
            "tags=,foo+bar",
            op -> op.hasType(OpType.AND)
                .hasChildrenThat(
                    child -> child.existsWithTagName("foo"),
                    child -> child.existsWithTagName("bar")),
            "'foo' and 'bar' without preceeding empty tag"),
        tagQuerySpec(
            "tags=,foo+bar,",
            op -> op.hasType(OpType.AND)
                .hasChildrenThat(
                    child -> child.existsWithTagName("foo"),
                    child -> child.existsWithTagName("bar")),
            "'foo' and 'bar' without tailing empty tag"));
  }

  @ParameterizedTest
  @MethodSource("simpleTagsSample")
  void shouldParseSimpleTags(String queryString, Consumer<POpAssertion> assertion) {
    QueryParameterList queryParameters = new QueryParameterList(queryString);
    POp op = ApiUtil.buildOperationForTagsQueryParam(queryParameters);
    assertion.accept(new POpAssertion(op));
  }

  private static Arguments tagQuerySpec(String query, Consumer<POpAssertion> assertion, String assertionDesc) {
    return arguments(query, named(assertionDesc, assertion));
  }

  private static POpAssertion assertThatOperation(POp subject) {
    assertNotNull(subject);
    return new POpAssertion(subject);
  }

  private record POpAssertion(POp subject) {

    POpAssertion existsWithTagName(String tagName) {
      return hasType(POpType.EXISTS).hasTagName(tagName);
    }

    POpAssertion hasType(OpType expectedOpType) {
      assertEquals(expectedOpType, subject.op());
      return this;
    }

    POpAssertion hasTagName(String expectedTagName) {
      assertNotNull(subject.getPropertyRef());
      assertEquals(expectedTagName, subject.getPropertyRef().getTagName());
      return this;
    }

    @SafeVarargs
    final POpAssertion hasChildrenThat(Consumer<POpAssertion>... childrenAssertions) {
      List<POp> subjects = subject.children();
      assertNotNull(subjects, "Expected multiple operations");
      assertEquals(subjects.size(), childrenAssertions.length, "Expecting single assertion per POp");
      for (int i = 0; i < subjects.size(); i++) {
        POpAssertion childAssertion = new POpAssertion(subjects.get(i));
        childrenAssertions[i].accept(childAssertion);
      }
      return this;
    }
  }
}
