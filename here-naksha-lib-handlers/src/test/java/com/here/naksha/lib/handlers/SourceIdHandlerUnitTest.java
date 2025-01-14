package com.here.naksha.lib.handlers;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.storage.ContextWriteXyzFeatures;
import naksha.base.Platform;
import naksha.base.ToJsonOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import naksha.model.request.*;
import naksha.model.request.query.*;
import org.json.JSONException;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.stream.Stream;

import static com.here.naksha.test.common.FileUtil.loadFileOrFail;
import static com.here.naksha.test.common.FileUtil.parseJsonFileOrFail;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceIdHandlerUnitTest {

    @Test
    void tc2002_testMapEqToContainsTag() {
        //given
        final Property property = new Property(NakshaProperties.META_KEY, "sourceId");
        final PQuery given = new PQuery(property, StringOp.EQUALS,"task_1");
        final ReadFeatures readFeatures = new ReadFeatures();
        readFeatures.getQuery().setProperties(given);
        //when

        SourceIdHandler.mapIntoTagOperation(readFeatures);
        //then

        final ITagQuery tagQuery = readFeatures.getQuery().getTags();

        assertInstanceOf(TagExists.class,tagQuery);
        final TagExists exists = (TagExists) tagQuery;
        assertEquals("xyz_source_id_task_1", exists.getName());
    }

    @Test
    void tc2003_testMapNotEqToNotContainsTag() {
        //given
        final Property property = new Property(NakshaProperties.META_KEY, "sourceId");
        final IPropertyQuery given = new PNot(new PQuery(property, StringOp.EQUALS,"task_1"));
        final ReadFeatures readFeatures = new ReadFeatures();
        readFeatures.getQuery().setProperties(given);
        //when

        SourceIdHandler.mapIntoTagOperation(readFeatures);
        //then

        final ITagQuery tagQuery = readFeatures.getQuery().getTags();

        assertInstanceOf(TagNot.class,tagQuery);
        final TagNot tagNot = (TagNot) tagQuery;
        assertInstanceOf(TagExists.class,tagNot.getQuery());
        final TagExists exists = (TagExists) tagNot.getQuery();
        assertEquals("xyz_source_id_task_1", exists.getName());
    }

    @Test
    void tc2004_testMapContainsToContainsTag() {
        //given
        final Property property = new Property(NakshaProperties.META_KEY, "sourceId");
        final PQuery given = new PQuery(property, StringOp.CONTAINS,"task_1");
        final ReadFeatures readFeatures = new ReadFeatures();
        readFeatures.getQuery().setProperties(given);
        //when

        SourceIdHandler.mapIntoTagOperation(readFeatures);
        //then

        final ITagQuery tagQuery = readFeatures.getQuery().getTags();

        assertInstanceOf(TagExists.class,tagQuery);
        final TagExists exists = (TagExists) tagQuery;
        assertEquals("xyz_source_id_task_1", exists.getName());
    }

    @Test
    void tc2005_testMapOnlyCorrectPref() {
        //given
        final Property property = new Property(NakshaProperties.META_KEY, "WrongProperty");
        final PQuery given = new PQuery(property, StringOp.EQUALS,"task_1");
        final ReadFeatures readFeatures = new ReadFeatures();
        readFeatures.getQuery().setProperties(given);
        //when

        SourceIdHandler.mapIntoTagOperation(readFeatures);
        //then

        final ITagQuery tagQuery = readFeatures.getQuery().getTags();

        assertNull(tagQuery);
    }

    @Test
    void tc2006_testMapsCorrectlyCombinedOperation () {
        //given
        final Property property = new Property(NakshaProperties.META_KEY, "sourceId");
        final Property property2 = new Property(NakshaProperties.META_KEY, "funnyTag");
        final PAnd given = new PAnd();
        given.add(new PNot(new PQuery(property, StringOp.EQUALS,"task_1")));
        given.add(new PQuery(property2, StringOp.CONTAINS,"4"));
        final ReadFeatures readFeatures = new ReadFeatures();
        readFeatures.getQuery().setProperties(given);
        //when

        SourceIdHandler.mapIntoTagOperation(readFeatures);
        //then

        final ITagQuery tagQuery = readFeatures.getQuery().getTags();

        assertInstanceOf(TagNot.class,tagQuery);
        final TagNot nestedTagNot = (TagNot) tagQuery;
        assertInstanceOf(TagExists.class,nestedTagNot.getQuery());
        final TagExists nestedTagExist = (TagExists) nestedTagNot.getQuery();
        assertEquals("xyz_source_id_task_1", nestedTagExist.getName());
        assertEquals(1, given.size());
        assertInstanceOf(PQuery.class, given.get(0));
        assertEquals(StringOp.CONTAINS,((PQuery) given.get(0)).getOp());
    }
    @Test
    void tc2007_testMapEqToContainsTagWithoutNormalization() {
        //given
        final Property property = new Property(NakshaProperties.META_KEY, "sourceId");
        final PQuery given = new PQuery(property, StringOp.CONTAINS,"tAskK_1");
        final ReadFeatures readFeatures = new ReadFeatures();
        readFeatures.getQuery().setProperties(given);
        //when

        SourceIdHandler.mapIntoTagOperation(readFeatures);
        //then

        final ITagQuery tagQuery = readFeatures.getQuery().getTags();

        assertInstanceOf(TagExists.class,tagQuery);
        final TagExists exists = (TagExists) tagQuery;
        assertEquals("xyz_source_id_tAskK_1", exists.getName());
    }

    @ParameterizedTest
    @MethodSource("writeRequestTestParams")
    void testWriteRequestTagPopulation(final WriteRequest wf, final String expectedFeatureJson) throws JSONException {
        // Given: Mocking in place
        final INaksha naksha = mock(INaksha.class);
        final IEvent event = mock(IEvent.class);
        when(event.getRequest()).thenReturn(wf);
        when(event.sendUpstream(any())).thenReturn(new SuccessResponse());

        // Given: Handler initialization
        final EventHandler e = new EventHandler(SourceIdHandler.class, "some_id");
        final SourceIdHandler sourceIdHandler = new SourceIdHandler(naksha);

        // When: handler processing logic is invoked
        final Response result = sourceIdHandler.process(event);
        assertTrue(result instanceof SuccessResponse, "SuccessResult was expected");

        // Then: validate that the feature in the original request is modified as per expectation
        assertNotNull(wf.getWrites().get(0));
        assertNotNull(wf.getWrites().get(0).getFeature());
        final String featureString = Platform.toJSON(wf.getWrites().get(0).getFeature(), ToJsonOptions.DEFAULT);
        JSONAssert.assertEquals("Output Feature not as expected", expectedFeatureJson, featureString, JSONCompareMode.STRICT);
    }

    private static Stream<Arguments> writeRequestTestParams() {
        // Common parameters across tests
        final String commonFilePath = "SourceIdFilter/testWriteFeatureTagPopulation/input_feature.json";
        final String expectedFeatureJson = loadFileOrFail("SourceIdFilter/testWriteFeatureTagPopulation/output_feature.json");

        return Stream.of(
                Arguments.arguments(
                        Named.named(
                                "WriteXyzFeatures tag population",
                                createWriteXyzFeaturesFromFile(commonFilePath)
                        ),
                        expectedFeatureJson
                ),
                Arguments.arguments(
                        Named.named(
                                "ContextWriteXyzFeatures tag population",
                                createContextWriteXyzFeaturesFromFile(commonFilePath)
                        ),
                        expectedFeatureJson
                )
        );
    }

    private static WriteRequest createWriteXyzFeaturesFromFile(final String filePath) {
        final NakshaFeature feature = parseJsonFileOrFail(filePath, NakshaFeature.class);
        final WriteRequest writeRequest = new WriteRequest();
        writeRequest.add(new Write().createFeature(null, "some_collection", feature));
        return writeRequest;
    }

    private static ContextWriteXyzFeatures createContextWriteXyzFeaturesFromFile(final String filePath) {
        final NakshaFeature feature = parseJsonFileOrFail(filePath, NakshaFeature.class);
        final ContextWriteXyzFeatures writeXyzFeatures = new ContextWriteXyzFeatures();
        writeXyzFeatures.add(new Write().createFeature(null, "some_collection", feature));
        return writeXyzFeatures;
    }
}