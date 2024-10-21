package com.here.naksha.storage.http.connector.integration;

import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.IntStream;

import static com.here.naksha.storage.http.connector.integration.Commons.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IterateTest {
  @BeforeEach
  void rmFeatures() {
    rmAllFeatures();
  }

  @Test
  void test() {
    IntStream.rangeClosed(1,5).forEach( i ->
            createFromJsonFileFormatted("iterate/feature_template.json",String.valueOf(i) )
    );

    String path = "iterate";
    Response dhResponse = dataHub().get(path);
    Response nResponse = naksha().get(path);
    assertTrue(responseHasExactShortIds(List.of("1","2","3","4","5"), dhResponse));
    assertTrue(responseHasExactShortIds(List.of("1","2","3","4","5"), nResponse));
  }
}
