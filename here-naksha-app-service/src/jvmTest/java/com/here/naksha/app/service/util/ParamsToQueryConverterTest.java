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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.here.naksha.app.service.http.ops.TagQueryUtil;
import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import java.util.function.Consumer;
import java.util.stream.Stream;
import naksha.model.NakshaException;
import naksha.model.request.query.ITagQuery;
import naksha.model.request.query.TagAnd;
import naksha.model.request.query.TagExists;
import naksha.model.request.query.TagOr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ParamsToQueryConverterTest {

  @Test
  void testBuildOperationForTagsQueryParam() {
    final QueryParameterList params = new QueryParameterList("tags=one"
                                                             + "&tags=two,three"
                                                             + "&tags=four+five"
                                                             + "&tags=six,seven,eight+nine"
                                                             + "&tags=ten+eleven,twelve,thirteen"
                                                             + "&tags=fourteen");
    final ITagQuery tagQuery = TagQueryUtil.tagQueryFromParams(params);
    assertInstanceOf(TagOr.class, tagQuery);
    final TagOr rootOrQuery = (TagOr) tagQuery;
    assertNotNull(rootOrQuery);

    // ensure there are total 8 operations
    assertEquals(8, rootOrQuery.size(), "Expected total 8 OR operations");

    // validate 1st operation uses EXISTS
    assertTagExistsWithName(rootOrQuery.get(0), "one");

    // validate 2nd operation uses OR
    TagOr second = assertTagOr(rootOrQuery.get(1));
    assertTagExistsWithName(second.get(0), "two");
    assertTagExistsWithName(second.get(1), "three");

    // validate 3rd operation uses AND
    TagAnd third = assertTagAnd(rootOrQuery.get(2));
    assertTagExistsWithName(third.get(0), "four");
    assertTagExistsWithName(third.get(1), "five");

    // validate 4th operation uses OR
    TagOr fourth = assertTagOr(rootOrQuery.get(3));
    assertTagExistsWithName(fourth.get(0), "six");
    assertTagExistsWithName(fourth.get(1), "seven");

    // validate 5th operation uses AND
    TagAnd fifth = assertTagAnd(rootOrQuery.get(4));
    assertTagExistsWithName(fifth.get(0), "eight");
    assertTagExistsWithName(fifth.get(1), "nine");

    // validate 6th operation uses AND
    TagAnd sixth = assertTagAnd(rootOrQuery.get(5));
    assertTagExistsWithName(sixth.get(0), "ten");
    assertTagExistsWithName(sixth.get(1), "eleven");

    // validate 7th operation uses OR
    TagOr seventh = assertTagOr(rootOrQuery.get(6));
    assertTagExistsWithName(seventh.get(0), "twelve");
    assertTagExistsWithName(seventh.get(1), "thirteen");

    // validate 8th operation uses EXISTS
    assertTagExistsWithName(rootOrQuery.get(7), "fourteen");
  }

  @ParameterizedTest
  @MethodSource("simpleTagsSample")
  void shouldParseSimpleTags(String queryString, Consumer<ITagQuery> assertion) {
    QueryParameterList queryParameters = new QueryParameterList(queryString);
    ITagQuery tagQuery = TagQueryUtil.tagQueryFromParams(queryParameters);
    assertion.accept(tagQuery);
  }

  @Test
  void assertionFailWhenTryingToBuildOperationWithSurroundingDelimiters() {
    // Given
    String queryWithSurroundingDelimiters = "tags=,foo,";
    final QueryParameterList params = new QueryParameterList(queryWithSurroundingDelimiters);

    // Then
    assertThrows(NakshaException.class, () -> TagQueryUtil.tagQueryFromParams(params));
  }

  private static Stream<Arguments> simpleTagsSample() {
    return Stream.of(
        tagQuerySpec("tags=x", tq -> assertTagExistsWithName(tq, "x"), "only 'x'"),
        tagQuerySpec(
            "tags=this,that",
            tq -> {
              TagOr tagOr = assertTagOr(tq);
              assertTagExistsWithName(tagOr.get(0), "this");
              assertTagExistsWithName(tagOr.get(1), "that");
            },
            "'this' or 'that'"),
        tagQuerySpec(
            "tags=foo+bar",
            tq -> {
              TagAnd tagAnd = assertTagAnd(tq);
              assertTagExistsWithName(tagAnd.get(0), "foo");
              assertTagExistsWithName(tagAnd.get(1), "bar");
            },
            "'foo' and 'bar'"),
        tagQuerySpec("tags=,foo", tq -> assertTagExistsWithName(tq, "foo"), "just delimiter and 'foo'"),
        tagQuerySpec(
            "tags=,foo+bar",
            tq -> {
              TagAnd tagAnd = assertTagAnd(tq);
              assertTagExistsWithName(tagAnd.get(0), "foo");
              assertTagExistsWithName(tagAnd.get(1), "bar");
            },
            "delimiter followed by 'foo' and 'bar'"),
        tagQuerySpec(
            "tags=,foo,bar",
            tq -> {
              TagOr tagOr = assertTagOr(tq);
              assertTagExistsWithName(tagOr.get(0), "foo");
              assertTagExistsWithName(tagOr.get(1), "bar");
            },
            "delimiter followed by 'foo' or 'bar'")
    );
  }

  private static Arguments tagQuerySpec(String query, Consumer<ITagQuery> rootQueryConsumer, String assertionDesc) {
    return arguments(query, named(assertionDesc, rootQueryConsumer));
  }

  private static void assertTagExistsWithName(ITagQuery tagQuery, String name) {
    assertNotNull(tagQuery);
    assertInstanceOf(TagExists.class, tagQuery);
    assertEquals(name, ((TagExists) tagQuery).getName());
  }

  private static TagOr assertTagOr(ITagQuery tagQuery) {
    assertNotNull(tagQuery);
    assertInstanceOf(TagOr.class, tagQuery);
    return (TagOr) tagQuery;
  }

  private static TagAnd assertTagAnd(ITagQuery tagQuery) {
    assertNotNull(tagQuery);
    assertInstanceOf(TagAnd.class, tagQuery);
    return (TagAnd) tagQuery;
  }
}
