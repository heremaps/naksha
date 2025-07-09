package naksha.model;

import naksha.model.objects.NakshaFeature;
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
        final XyzFeatureCollection collection = new XyzFeatureCollection().withNextPageToken("test-token-123");
        //when //then
        assertEquals("test-token-123", collection.getNextPageToken(), "Getter should return the set token.");

        //given
        collection.withNextPageToken(null);
        //when //then
        assertNull(collection.getNextPageToken(), "Getter should return null after the token is set to null.");
    }

    @Test
    void testInitialState() {
        //given
        final XyzFeatureCollection collection = new XyzFeatureCollection();
        //when /then
        assertNotNull(collection.getFeatures(), "Features list should not be null on creation.");
        assertTrue(collection.getFeatures().isEmpty(), "Features list should be empty on creation.");
    }

    @Test
    void testBasicProperties() {
        //given
        final XyzFeatureCollection collection = new XyzFeatureCollection()
                .withCount(123L)
                .withPartial(true)
                .withVersion(5);
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

        final XyzFeatureCollection collection = new XyzFeatureCollection()
                .withFeatures(Arrays.asList(feature1, feature2))
                .withInserted(Collections.singletonList("id_01"))
                .withUpdated(Collections.singletonList("id_02"))
                .withDeleted(Collections.singletonList("id_03"));
        //when //then
        assertEquals(2, collection.getFeatures().size());
        assertEquals(feature1, collection.getFeatures().getFirst());
        assertEquals("id_01", collection.getInserted().getFirst());
        assertEquals("id_02", collection.getUpdated().getFirst());
        assertEquals("id_03", collection.getDeleted().getFirst());
    }

    @Test
    void testAppendToIdLists() {
        //given
        final XyzFeatureCollection collection = new XyzFeatureCollection();

        collection.appendInsertId("new_insert_1");
        collection.appendUpdateId("new_update_1");
        collection.appendDeleteId("new_delete_1");

        //when //then
        assertEquals(1, collection.getInserted().size());
        assertEquals("new_insert_1", collection.getInserted().getFirst());

        assertEquals(1, collection.getUpdated().size());
        assertEquals("new_update_1", collection.getUpdated().getFirst());

        assertEquals(1, collection.getDeleted().size());
        assertEquals("new_delete_1", collection.getDeleted().getFirst());

        //given
        collection.appendInsertId("new_insert_2");
        //when //then
        assertEquals(2, collection.getInserted().size());
        assertEquals("new_insert_2", collection.getInserted().get(1));
    }

    @Test
    void testModificationFailures() {
        //given
        final XyzFeatureCollection.ModificationFailure failure = new XyzFeatureCollection.ModificationFailure()
                .withId("failed_id")
                .withMessage("Something went wrong");

        final XyzFeatureCollection collection = new XyzFeatureCollection()
                .withFailed(Collections.singletonList(failure));

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