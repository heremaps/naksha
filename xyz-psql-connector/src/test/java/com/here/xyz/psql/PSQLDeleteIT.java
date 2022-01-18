/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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
package com.here.xyz.psql;

import com.amazonaws.util.IOUtils;
import com.here.xyz.XyzSerializable;
import com.here.xyz.events.*;
import com.here.xyz.models.geojson.implementation.Feature;
import com.here.xyz.models.geojson.implementation.FeatureCollection;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class PSQLDeleteIT extends PSQLAbstractIT {

    @BeforeClass
    public static void init() throws Exception { initEnv(null); }

    @Before
    public void removeTestSpaces() throws Exception { deleteTestSpace(null); }

    @After
    public void shutdown() throws Exception { shutdownEnv(null); }

    @Test
    public void testDeleteFeatures() throws Exception {
        // =========== INSERT ==========
        String insertJsonFile = "/events/InsertFeaturesEvent.json";
        String insertResponse = invokeLambdaFromFile(insertJsonFile);
        logger.info("RAW RESPONSE: " + insertResponse);

        FeatureCollection fc = XyzSerializable.deserialize(insertResponse);
        List<String> ids = fc.getFeatures().stream().map(Feature::getId).filter(Objects::nonNull).toList();

        // =========== DELETE ==========
        Map<String, String> idsMap = new HashMap<>();
        ids.forEach(id -> idsMap.put(id, null));

        ModifyFeaturesEvent mfe = new ModifyFeaturesEvent().withSpace("foo").withDeleteFeatures(idsMap).withConnectorParams(defaultTestConnectorParams);
        String deleteResponse = invokeLambda(mfe.serialize());
        fc = XyzSerializable.deserialize(deleteResponse);

        assertEquals(2,fc.getDeleted().size());
        assertTrue(fc.getDeleted().containsAll(ids));

        logger.info("Modify features tested successfully");
    }

    @Test
    public void testDeleteFeaturesByTagWithOldStates() throws Exception {
        testDeleteFeaturesByTag(true);
    }

    @Test
    public void testDeleteFeaturesByTagDefault() throws Exception {
        testDeleteFeaturesByTag(false);
    }

    private void testDeleteFeaturesByTag(boolean includeOldStates) throws Exception {
        // =========== INSERT ==========
        String insertJsonFile = "/events/InsertFeaturesEvent.json";
        String insertResponse = invokeLambdaFromFile(insertJsonFile);
        logger.info("RAW RESPONSE: " + insertResponse);
        String insertRequest = IOUtils.toString(GSContext.class.getResourceAsStream(insertJsonFile));

        FeatureCollection fc = assertRead(insertRequest, insertResponse, false);
        logger.info("Preparation: Insert features");

        // =========== COUNT ==========
        int originalCount = fc.getFeatures().size();
        logger.info("Preparation: feature count = {}", originalCount);

        // =========== DELETE SOME TAGGED FEATURES ==========
        logger.info("Delete tagged features");
        Map<String, Object> params = new HashMap<>() {{put("includeOldStates", includeOldStates);}};

        DeleteFeaturesByTagEvent dfbt = new DeleteFeaturesByTagEvent()
                .withSpace("foo")
                .withConnectorParams(defaultTestConnectorParams)
                .withParams(params)
                .withTags(TagsQuery.fromQueryParameter(new String[]{"yellow"}));
        String deleteByTagResponse = invokeLambda(dfbt.serialize());
        fc = XyzSerializable.deserialize(deleteByTagResponse);

        if (includeOldStates) {
            assertNotNull("'features' element in DeleteByTagResponse is missing", fc.getFeatures());
            assertTrue("'features' element in DeleteByTagResponse is empty", fc.getFeatures().size() > 0);
        } else{
            assertEquals(Long.valueOf(1), fc.getCount());
            assertEquals("unexpected features in DeleteByTagResponse", 0, fc.getFeatures().size());
        }

        Event sff = new SearchForFeaturesEvent().withSpace("foo").withConnectorParams(defaultTestConnectorParams);
        String sffResponse = invokeLambda(sff.serialize());
        fc = XyzSerializable.deserialize(sffResponse);

        assertTrue(originalCount > fc.getFeatures().size());
        logger.info("Delete tagged features tested successfully");

        // =========== DELETE ALL FEATURES ==========
        dfbt.setTags(null);
        invokeLambda(dfbt.serialize());

        sffResponse = invokeLambda(sff.serialize());
        fc = XyzSerializable.deserialize(sffResponse);

        assertEquals(0, fc.getFeatures().size());
        logger.info("Delete all features tested successfully");
    }
}
