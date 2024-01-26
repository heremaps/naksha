package com.here.naksha.app.service.util;

import com.here.naksha.app.service.http.ops.PropertySelectionUtil;
import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class PropertySelectionUtilTest {


  /*
  Test cases:
  multiple selection params (including null,number)
  single selection param
  selection param with invalid delimiter
  mixed params (selection and tags)
   */

    private static Stream<Arguments> propSelectionPathTestData() {
        return Stream.of(
                // invalid delimiter
                selectionTestSpec(
                        "Positive test with multiple params",
                        "selection=p.color"
                        ,
                        List.of(
                                "properties.color"
                        )
                )
        );
    }

    @ParameterizedTest
    @MethodSource("propSelectionPathTestData")
    void testPropSelectionPathListFromQueryParam(
            final @NotNull String queryString,
            final @Nullable List<String> expectedPropPathList) {
        // Given: input query param string
        final QueryParameterList params = new QueryParameterList(queryString);

        // When: test function is invoked with query params
        final List<String> actualPropPathList = PropertySelectionUtil.buildPropPathListFromQueryParams(params);

        // Then: validate list of property path strings are as expected
        if (expectedPropPathList == null) {
            assertNull(actualPropPathList, "Expected null list");
        } else {
            assertArrayEquals(expectedPropPathList.toArray(), actualPropPathList.toArray());
        }
    }

    private static Arguments selectionTestSpec(String testDesc, String query, List<String> expectedPropPathList) {
        return arguments(query, named(testDesc, expectedPropPathList));
    }

}
