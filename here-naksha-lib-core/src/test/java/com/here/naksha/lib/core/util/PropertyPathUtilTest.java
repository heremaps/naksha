package com.here.naksha.lib.core.util;

import com.here.naksha.lib.core.common.TestUtil;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PropertyPathUtilTest {

    private static final String TEST_DATA_FOLDER = "src/test/resources/prop_path_data/";

    @Test
    void runTest() throws JSONException {
        logic();
    }

    void logic() throws JSONException {
        String jsonData = TestUtil.loadFileOrFail(TEST_DATA_FOLDER, "JsonTest/Input.json");
        String expectedJsonData = TestUtil.loadFileOrFail(TEST_DATA_FOLDER, "JsonTest/Expected.json");
        XyzFeature f = TestUtil.parseJson(jsonData, XyzFeature.class);

        List<String[]> paths = new ArrayList<>();
        paths.add(new String[]{"id"});
        paths.add(new String[]{"type"});
        paths.add(new String[]{"properties","status"});
        paths.add(new String[]{"properties","priority","unknown"});
        paths.add(new String[]{"properties","references","0"});
        paths.add(new String[]{"properties","@ns:com:here:utm","tags","0","unknown"});
        paths.add(new String[]{"properties","isoCountryCode"});
        paths.add(new String[]{"properties","@ns:com:here:utm","priorityParams","backup","dueBy"});
        paths.add(new String[]{"properties","@ns:com:here:utm","priorityParams","backup","priority"});
        paths.add(new String[]{"properties","options","1","key"});
        paths.add(new String[]{"properties","options","1","value"});
        paths.add(new String[]{"properties","@ns:com:here:xyz","unknown"});
        paths.add(new String[]{"properties","@ns:com:here:xyz","uuid"});
        paths.add(new String[]{"properties","@ns:com:here:xyz","tags","0"});
        paths.add(new String[]{"properties","@ns:com:here:xyz","tags","2"});
        paths.add(new String[]{"properties","@ns:com:here:xyz","tags","50"});


        Map<String, Object> newF = new HashMap<>();
        newF.put("id", f.getId());
        newF.put("type", "Feature");
        PropertyPathUtil.extractPropertyPathsFromMap(f, newF, paths);
        assertNotNull(newF);
        //newF.setProperties(newProp);
        final String actualJsonData = TestUtil.toJson(newF);
        JSONAssert.assertEquals("Selection object doesn't match", expectedJsonData, actualJsonData, JSONCompareMode.STRICT);
    }
}
