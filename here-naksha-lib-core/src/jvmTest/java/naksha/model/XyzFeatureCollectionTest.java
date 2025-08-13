package naksha.model;

import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XyzFeatureCollectionTest {

    @Test
    void testNextPageTokenBehavior() {
        //given
        final XyzFeatureCollection collection = new XyzFeatureCollection();
        collection.setNextPageToken("test-token-123");
        //when //then
        assertEquals("test-token-123", collection.getNextPageToken(), "Getter should return the set token.");

        //given
        collection.setNextPageToken(null);
        //when //then
        assertNull(collection.getNextPageToken(), "Getter should return null after the token is set to null.");
    }

    @Test
    void testInitialState() {
        //given
        final XyzFeatureCollection collection = new XyzFeatureCollection();
        //when /then
        assertNotNull(collection.getFeatures(NakshaFeatureList.TYPE), "Features list should not be null on creation.");
        assertTrue(collection.getFeatures(NakshaFeatureList.TYPE).isEmpty(), "Features list should be empty on creation.");
    }

    @Test
    void testBasicProperties() {
        //given
        final XyzFeatureCollection collection = new XyzFeatureCollection();
        collection.setCount(123L);
        collection.setPartial(true);
        collection.setVersion(5);
        //when //then
        assertEquals(123L, collection.getCount());
        assertTrue(collection.isPartial());
        assertEquals(5, collection.getVersion());
    }

    @Test
    void testFeatureAndIdLists() {
        //given
        final NakshaFeature feature1 = newFeature("id_01");
        final NakshaFeature feature2 = newFeature("id_02");

        final XyzFeatureCollection collection = new XyzFeatureCollection();
        collection.setFeatures(Arrays.asList(feature1, feature2));
        collection.setInserted(Collections.singletonList("id_01"));
        collection.setUpdated(Collections.singletonList("id_02"));
        collection.setDeleted(Collections.singletonList("id_03"));

        //when //then
        assertEquals(2, collection.getFeatures(NakshaFeatureList.TYPE).size());
        assertEquals(feature1, collection.getFeatures(NakshaFeatureList.TYPE).getFirst());
        assertEquals("id_01", collection.getInserted().getFirst());
        assertEquals("id_02", collection.getUpdated().getFirst());
        assertEquals("id_03", collection.getDeleted().getFirst());
    }

    @Test
    void testAppendToIdLists() {
        //given
        final XyzFeatureCollection collection = new XyzFeatureCollection();

        collection.getInserted().append("new_insert_1");
        collection.getUpdated().append("new_update_1");
        collection.getDeleted().append("new_delete_1");

        //when //then
        assertEquals(1, collection.getInserted().size());
        assertEquals("new_insert_1", collection.getInserted().getFirst());

        assertEquals(1, collection.getUpdated().size());
        assertEquals("new_update_1", collection.getUpdated().getFirst());

        assertEquals(1, collection.getDeleted().size());
        assertEquals("new_delete_1", collection.getDeleted().getFirst());

        //given
        collection.getInserted().append("new_insert_2");
        //when //then
        assertEquals(2, collection.getInserted().size());
        assertEquals("new_insert_2", collection.getInserted().get(1));
    }

    @Test
    void testModificationFailures() {
        //given
        final ModificationFailure failure = new ModificationFailure();
        failure.setId("failed_id");
        failure.setMessage("Something went wrong");

        final XyzFeatureCollection collection = new XyzFeatureCollection();
        collection.setFailed(Collections.singletonList(failure));

        // when // then
        assertEquals(1, collection.getFailed().size());
        assertEquals("failed_id", collection.getFailed().getFirst().getId());
        assertEquals("Something went wrong", collection.getFailed().getFirst().getMessage());
    }



    /**
     * Helper method to create a simple NakshaFeature for testing purposes.
     */
    private @NotNull NakshaFeature newFeature(@NotNull String id) {
        return new NakshaFeature(id);
    }
}