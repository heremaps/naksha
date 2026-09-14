package com.here.naksha.lib.handlers.internal;

import com.here.naksha.lib.core.INaksha;
import naksha.base.Id;
import naksha.model.IStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.when;
import static org.mockito.kotlin.OngoingStubbingKt.whenever;

public abstract class AbstractIntHandlerTest {

    protected static final String TEST_MAP_ID = "test_map_id";
    private final Id adminStorageId = new Id();
    private final Id spaceStorageId = new Id();

    @Mock
    IStorage adminStorage;

    @Mock
    IStorage spaceStorage;

    @Mock
    INaksha naksha;

    private AutoCloseable mock;

    @BeforeEach
    void setup() {
        mock = MockitoAnnotations.openMocks(this);
        whenever(adminStorage.getId()).thenReturn(adminStorageId);
        whenever(naksha.getAdminStorage()).thenReturn(adminStorage);

        whenever(spaceStorage.getId()).thenReturn(spaceStorageId);
        whenever(naksha.getSpaceStorage()).thenReturn(spaceStorage);
        whenever(naksha.getAdminMapId()).thenReturn(TEST_MAP_ID);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mock != null) mock.close();
        mock = null;
    }
}
