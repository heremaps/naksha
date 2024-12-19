package com.here.naksha.lib.handlers;

import com.here.naksha.lib.handlers.util.PropertyOperationUtil;
import naksha.model.objects.NakshaProperties;
import naksha.model.request.RequestQuery;
import naksha.model.request.query.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SourceIdHandlerUnitTest {

    @Test
    void tc2002_testMapEqToContainsTag() {
        //given
        final Property property = new Property(NakshaProperties.META_KEY, "sourceId");
        final PQuery given = new PQuery(property, StringOp.EQUALS,"task_1");
        //when

        Optional<ITagQuery> tagQuery = SourceIdHandler.mapIntoTagOperation(given);
        //then

        assertTrue(tagQuery.isPresent());
        assertInstanceOf(TagExists.class,tagQuery.get());
        final TagExists exists = (TagExists) tagQuery.get();
        assertEquals("xyz_source_id_task_1", exists.getName());
    }

    @Test
    void tc2003_testMapNotEqToNotContainsTag() {
        //given
        final Property property = new Property(NakshaProperties.META_KEY, "sourceId");
        final IPropertyQuery given = new PNot(new PQuery(property, StringOp.EQUALS,"task_1"));
        //when

        Optional<ITagQuery> tagQuery = SourceIdHandler.mapIntoTagOperation(given);
        //then

        assertTrue(tagQuery.isPresent());
        assertInstanceOf(TagNot.class,tagQuery.get());
        final TagNot tagNot = (TagNot) tagQuery.get();
        assertInstanceOf(TagExists.class,tagNot.getQuery());
        final TagExists exists = (TagExists) tagNot.getQuery();
        assertEquals("xyz_source_id_task_1", exists.getName());
    }

    @Test
    void tc2004_testMapContainsToContainsTag() {
        //given
        final Property property = new Property(NakshaProperties.META_KEY, "sourceId");
        final PQuery given = new PQuery(property, StringOp.CONTAINS,"task_1");
        //when

        final Optional<ITagQuery> tagQuery = SourceIdHandler.mapIntoTagOperation(given);
        //then

        assertTrue(tagQuery.isPresent());
        assertInstanceOf(TagExists.class,tagQuery.get());
        final TagExists exists = (TagExists) tagQuery.get();
        assertEquals("xyz_source_id_task_1", exists.getName());
    }

    @Test
    void tc2005_testMapOnlyCorrectPref() {
        //given
        final Property property = new Property(NakshaProperties.META_KEY, "WrongProperty");
        final PQuery given = new PQuery(property, StringOp.EQUALS,"task_1");
        //when

        final Optional<ITagQuery> tagQuery = SourceIdHandler.mapIntoTagOperation(given);
        //then

        assertFalse(tagQuery.isPresent());
    }

    @Test
    void tc2006_testMapsCorrectlyCombinedOperation () {
        //given
        final Property property = new Property(NakshaProperties.META_KEY, "sourceId");
        final Property property2 = new Property(NakshaProperties.META_KEY, "funnyTag");
        final PAnd given = new PAnd();
        given.add(new PNot(new PQuery(property, StringOp.EQUALS,"task_1")));
        given.add(new PQuery(property2, StringOp.CONTAINS,"4"));

        //when

        final Optional<ITagQuery> tagQuery = SourceIdHandler.mapIntoTagOperation(given);
        //then

        assertTrue(tagQuery.isPresent());
        assertInstanceOf(TagAnd.class,tagQuery.get());
        final TagAnd tagAnd = (TagAnd) tagQuery.get();
        assertEquals(1, tagAnd.size());
        assertInstanceOf(TagNot.class,tagAnd.get(0));
        final TagNot nestedTagNot = (TagNot) tagAnd.get(0);
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
        NonIndexedPRef pRef = new NonIndexedPRef(XyzFeature.PROPERTIES, XyzProperties.HERE_META_NS, "sourceId");
        POp given = POp.eq(pRef, "tAskK_1");

        //when
        Optional<POp> result = SourceIdHandler.mapIntoTagOperation(given);

        //then
        assertTrue(result.isPresent());
        assertEquals(result.get().getPropertyRef().getTagName(), "xyz_source_id_tAskK_1");
        assertEquals(result.get().op(), POpType.EXISTS);
    }

    @ParameterizedTest
    @MethodSource("writeRequestTestParams")
    void testWriteRequestTagPopulation(final WriteFeatures<XyzFeature,?,?> wf, final String expectedFeatureJson) throws JSONException {
        // Given: Mocking in place
        final INaksha naksha = mock(INaksha.class);
        final IEvent event = mock(IEvent.class);
        when(event.getRequest()).thenReturn((Request)wf);
        when(event.sendUpstream(any())).thenReturn(new SuccessResult());

        // Given: Handler initialization
        final EventHandler e = new EventHandler(SourceIdHandler.class, "some_id");
        final SourceIdHandler sourceIdHandler = new SourceIdHandler(naksha);

        // When: handler processing logic is invoked
        try (final Result result = sourceIdHandler.process(event)) {
            assertTrue(result instanceof SuccessResult, "SuccessResult was expected");
        }
        // Then: validate that the feature in the original request is modified as per expectation
        assertNotNull(wf.features.get(0));
        assertNotNull(wf.features.get(0).getFeature());
        JSONAssert.assertEquals("Output Feature not as expected", expectedFeatureJson, wf.features.get(0).getFeature().serialize(), JSONCompareMode.STRICT);
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

    private static WriteFeatures<?,?,?> createWriteXyzFeaturesFromFile(final String filePath) {
        final XyzFeature feature = parseJsonFileOrFail(filePath, XyzFeature.class);
        return new WriteXyzFeatures("some_collection").add(EWriteOp.CREATE, feature);
    }

    private static WriteFeatures<?,?,?> createContextWriteXyzFeaturesFromFile(final String filePath) {
        final XyzFeature feature = parseJsonFileOrFail(filePath, XyzFeature.class);
        return new ContextWriteXyzFeatures("some_collection").add(EWriteOp.CREATE, feature);
    }

}