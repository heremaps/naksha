package com.here.naksha.app.service.http.apis;

import static org.junit.jupiter.api.Assertions.*;

import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import org.junit.jupiter.api.Test;

class ApiParamsTest {

  @Test
  void shouldCorrectlyExtractInt(){
    // Given:
    QueryParameterList queryParameterList = new QueryParameterList("limit=5");

    // When
    int result = ApiParams.extractQueryParamAsInt(queryParameterList, "limit", false, 0);

    // Then
    assertEquals(5, result);
  }
}