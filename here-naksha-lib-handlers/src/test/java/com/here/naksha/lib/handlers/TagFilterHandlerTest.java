package com.here.naksha.lib.handlers;

import com.here.naksha.lib.core.util.storage.RequestHelper;
import com.here.naksha.test.common.FileUtil;
import com.here.naksha.test.common.JsonUtil;
import java.util.Map;
import naksha.base.AnyList;
import naksha.base.AnyObject;
import naksha.base.FromJsonOptions;
import naksha.base.JvmProxyUtil;
import naksha.base.MapProxy;
import naksha.base.Platform;
import naksha.base.ToJsonOptions;
import naksha.model.XyzFeatureCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import naksha.model.request.query.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TagFilterHandlerTest {

    @Test
    void testReadWithoutPOpAndSingleTagFilter() {
        // Given: ReadFeatures request without any property operation
        final ReadFeatures request = new ReadFeatures();
        // Given: single tag filter
        final List<String> tagFilter = List.of("violated_ftype_topology");
        // When: a function is called with request and tag filter configuration
        TagFilterHandler.applyFilterConditionOnRequest(request, tagFilter);
        // Then: Validate that Tags "exists" condition is added to request
        final ITagQuery tagQuery = request.getQuery().getTags();
        Assertions.assertInstanceOf(TagExists.class, tagQuery);
        assertEquals("violated_ftype_topology", ((TagExists) tagQuery).getName());
    }

    @Test
    void testReadWithoutPOpAndMultipleTagFilters() {
        // Given: ReadFeatures request without any property operation
        final ReadFeatures request = new ReadFeatures();
        // Given: multiple tag filters
        final List<String> tagFilter = List.of("violated_ftype_topology","some_other_tag");
        // When: a function is called with request and tag filter configuration
        TagFilterHandler.applyFilterConditionOnRequest(request, tagFilter);
        // Then: Validate that Tags "and" condition is added to request with multiple tag filters
        final ITagQuery tagQuery = request.getQuery().getTags();
        Assertions.assertInstanceOf(TagAnd.class, tagQuery);
        List<String> tagNameList = ((TagAnd) tagQuery).stream()
                .map(tagOp -> (TagExists) tagOp)
                .map(TagExists::getName).toList();
        assertEquals(tagFilter.size(), tagNameList.size());
        Assertions.assertTrue(tagNameList.contains("violated_ftype_topology"));
        Assertions.assertTrue(tagNameList.contains("some_other_tag"));
    }

    @Test
        //TODO might be redundant now that property query and tag query are separate
    void testReadWithPOpAndSingleTagFilter() {
        // Given: ReadFeatures request with atleast one property operation
        final PQuery pQuery = new PQuery(new Property("foo"), StringOp.EQUALS, "value");
        final ReadFeatures request = new ReadFeatures();
        request.getQuery().setProperties(pQuery);
        // Given: single tag filter
        final List<String> tagFilter = List.of("violated_ftype_topology");
        // When: a function is called with request and tag filter configuration
        TagFilterHandler.applyFilterConditionOnRequest(request, tagFilter);
        // Then: Validate that tag query and property query are both added unaffected by each other
        final ITagQuery tagQuery = request.getQuery().getTags();
        Assertions.assertInstanceOf(TagExists.class, tagQuery);
        assertEquals("violated_ftype_topology", ((TagExists) tagQuery).getName());
        assertEquals(pQuery, request.getQuery().getProperties());
    }

    @Test
        //TODO might be redundant now that property query and tag query are separate
    void testReadWithPOpAndMultipleTagFilters() {
        // Given: ReadFeatures request with atleast one property operation
        final PQuery pQuery = new PQuery(new Property("foo"), StringOp.EQUALS, "value");
        final ReadFeatures request = new ReadFeatures();
        request.getQuery().setProperties(pQuery);
        // Given: multiple tag filters
        final List<String> tagFilter = List.of("violated_ftype_topology","some_other_tag");
        // When: a function is called with request and tag filter configuration
        TagFilterHandler.applyFilterConditionOnRequest(request, tagFilter);
        // Then: Validate that Tags "and" condition is added to request
        // with nested "and" condition between multiple Tags
        // and property query unaffected
        final ITagQuery tagQuery = request.getQuery().getTags();
        Assertions.assertInstanceOf(TagAnd.class, tagQuery);
        List<String> tagNameList = ((TagAnd) tagQuery).stream()
                .map(tagOp -> (TagExists) tagOp)
                .map(TagExists::getName).toList();
        assertEquals(tagFilter.size(), tagNameList.size());
        Assertions.assertTrue(tagNameList.contains("violated_ftype_topology"));
        Assertions.assertTrue(tagNameList.contains("some_other_tag"));
        assertEquals(pQuery, request.getQuery().getProperties());
    }

    @Test
    void testReadWithPOpButWithoutTagFilter() {
        // Given: ReadFeatures request with atleast one property operation
        final PQuery pQuery = new PQuery(new Property("foo"), StringOp.EQUALS, "value");
        final ReadFeatures request = new ReadFeatures();
        request.getQuery().setProperties(pQuery);
        // When: a function is called with request and no tag filter configuration (i.e. null)
        TagFilterHandler.applyFilterConditionOnRequest(request, null);
        // Then: Validate that existing request operation remains unchanged
        assertEquals(pQuery, request.getQuery().getProperties(), "Expected request operation same as input operation");
        assertNull(request.getQuery().getTags(), "Expected request having no tag query but there is");
        // When: a function is called with request and empty tag filter configuration
        TagFilterHandler.applyFilterConditionOnRequest(request, List.of());
        // Then: Validate that existing request operation remains unchanged
        assertEquals(pQuery, request.getQuery().getProperties(), "Expected request operation same as input operation");
        assertNull(request.getQuery().getTags(), "Expected request having no tag query but there is");
    }

    private static Stream<Arguments> writeTestData() {
        return Stream.of(
                // without any tag filter
                writeTestSpec(
                        "testWriteWithoutTagFilter",
                        "TagFilter/input_features.json",
                        null,
                        null,
                        "TagFilter/testWriteWithoutTagFilter/features.json"
                ),
                // with addTags having one tag which is already present
                writeTestSpec(
                        "testWriteWithAddTags",
                        "TagFilter/input_features.json",
                        List.of("nine", "one"),
                        null,
                        "TagFilter/testWriteWithAddTags/features.json"
                ),
                // with removeTags having one tag which is not present
                writeTestSpec(
                        "testWriteWithRemoveTags",
                        "TagFilter/input_features.json",
                        null,
                        List.of("nine", "on"),
                        "TagFilter/testWriteWithRemoveTags/features.json"
                ),
                // with both addTags and removeTags, by replacing existing tag "one" with "once"
                writeTestSpec(
                        "testWriteWithAddAndRemoveTags",
                        "TagFilter/input_features.json",
                        List.of("two", "once"),
                        List.of("two", "nine", "on"),
                        "TagFilter/testWriteWithAddAndRemoveTags/features.json"
                )
        );
    }

    private static Arguments writeTestSpec(final String testDesc,
                                           final @NotNull String inputFilePath,
                                           final @Nullable List<String> addTags,
                                           final @Nullable List<String> removeTags,
                                           final @NotNull String outputFilePath) {
        return Arguments.arguments(inputFilePath, addTags, removeTags, Named.named(testDesc, outputFilePath));
    }

    @ParameterizedTest
    @MethodSource("writeTestData")
    void commonWriteTest(final @NotNull String inputFilePath,
                               final @Nullable List<String> addTags,
                               final @Nullable List<String> removeTags,
                               final @NotNull String outputFilePath) throws JSONException {
        // Given: WriteXyzFeatures request with some tags already part of features
        final String featuresJson = FileUtil.loadFileOrFail(inputFilePath);

//        final XyzFeatureCollection inputCollection = JsonUtil.parseJson(featuresJson, XyzFeatureCollection.class);
        final AnyObject rawInputCollection = JvmProxyUtil.box(Platform.fromJSON(featuresJson, FromJsonOptions.DEFAULT), AnyObject.class);
        final AnyList rawFeatures = (AnyList) rawInputCollection.get("features");
        final XyzFeatureCollection inputCollection = new XyzFeatureCollection();
        final List<NakshaFeature> features = new ArrayList<>();
        for(Object rawF: rawFeatures){
            features.add(JvmProxyUtil.box(rawF, NakshaFeature.class));
        }
        inputCollection.setFeatures(features);
        final WriteRequest wf = RequestHelper.upsertFeaturesRequest("some_space", inputCollection.getFeatures());
        // Given: Expected feature collection JSON
        final String expectedJson = FileUtil.loadFileOrFail(outputFilePath);

        // When: a function is called with request and given tag filter configuration
        TagFilterHandler.applyTagChangesOnRequest(wf, addTags, removeTags);
        // Then: Validate that the output features in the request is as expected
        final String actualJson = covertWriteFeaturesToCollectionJson(wf.getWrites());
        JSONAssert.assertEquals("List of output features don't match", expectedJson, actualJson, JSONCompareMode.STRICT_ORDER);
    }

    private String covertWriteFeaturesToCollectionJson(final @NotNull List<Write> writes) {
        final List<NakshaFeature> features = new ArrayList<>();
        for (final @NotNull Write write : writes) {
            features.add(write.getFeature());
        }
        final XyzFeatureCollection outputCollection = new XyzFeatureCollection().withFeatures(features);
        return Platform.toJSON(outputCollection, ToJsonOptions.DEFAULT);
    }

}