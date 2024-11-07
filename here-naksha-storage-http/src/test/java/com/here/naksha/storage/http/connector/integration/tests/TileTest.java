package com.here.naksha.storage.http.connector.integration.tests;

import com.here.naksha.storage.http.connector.integration.utils.DataHub;
import com.here.naksha.storage.http.connector.integration.utils.Naksha;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.here.naksha.storage.http.connector.integration.utils.Commons.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TileTest {
  @BeforeEach
  void rmFeatures() {
    rmAllFeatures();
  }

  @Test
  void testTranslationToBBox() {

    DataHub.createFeatureFromJsonTemplateFile( // clearly inside
            "tile/feature_template.json",
            "1",
            "[3, -3, 0], [0, -3, 0], [0, 0, 0], [3, 0, 0], [3, -3, 0]"
    );

    DataHub.createFeatureFromJsonTemplateFile( // clearly outside
            "tile/feature_template.json",
            "2",
            "[-103, -3, 0], [-100, -3, 0], [-100, 0, 0], [-103, 0, 0], [-103, -3, 0]"
    );


    DataHub.createFeatureFromJsonTemplateFile( // inside, east edge (11.25E)
            "tile/feature_template.json",
            "3",
            "[11.25, -3, 0], [12, -3, 0], [12, 0, 0], [11.25, 0, 0], [11.25, -3, 0]"
    );

    DataHub.createFeatureFromJsonTemplateFile( // outside, east edge (11.25E)
            "tile/feature_template.json",
            "4",
            "[11.251, -3, 0], [12, -3, 0], [12, 0, 0], [11.251, 0, 0], [11.251, -3, 0]"
    );

    DataHub.createFeatureFromJsonTemplateFile( // inside, west edge (0W)
            "tile/feature_template.json",
            "5",
            "[-3, -3, 0], [0, -3, 0], [0, 0, 0], [-3, 0, 0], [-3, -3, 0]"
    );


    DataHub.createFeatureFromJsonTemplateFile( // outside, west edge (0W)
            "tile/feature_template.json",
            "6",
            "[-3, -3, 0], [-0.01, -3, 0], [-0.01, 0, 0], [-3, 0, 0], [-3, -3, 0]"
    );

    DataHub.createFeatureFromJsonTemplateFile( // inside, north edge (0N)
            "tile/feature_template.json",
            "7",
            "[0, 0, 0], [3, 0, 0], [3, 3, 0], [0, 3, 0], [0, 0, 0]"
    );


    DataHub.createFeatureFromJsonTemplateFile( // outside, north edge (0N)
            "tile/feature_template.json",
            "8",
            "[0, 0.01, 0], [3, 0.01, 0], [3, 3, 0], [0, 3, 0], [0, 0.01, 0]"
    );

    String path = "tile/quadkey/30000"; // ~11.1784... S - 0 N; 11.250 W - 0E
    Response dhResponse = DataHub.request().get(path);
    Response nResponse = Naksha.request().get(path);
    assertTrue(responseHasExactShortIds(List.of("1","3","5","7"), dhResponse));
    assertTrue(responseHasExactShortIds(List.of("1","3","5","7"), nResponse));
  }

  @ParameterizedTest
  @ValueSource(strings = {"here","tms"})
  void testUnsupportedTileType(String unsupportedTileType) {

    DataHub.createFeatureFromJsonTemplateFile( // exemplary feature
            "tile/feature_template.json",
            "1",
            "[3, -3, 0], [0, -3, 0], [0, 0, 0], [3, 0, 0], [3, -3, 0]"
    );

    String pathToUnsupportedTile = "tile/" + unsupportedTileType + "/30000";

    Response response = Naksha.request().get(pathToUnsupportedTile);
    assertEquals("ErrorResponse", response.jsonPath().getString("type"));
    assertEquals("IllegalArgument", response.jsonPath().getString("error"));
  }
}
