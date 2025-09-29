package com.here.naksha.cli.copy.service.factory;

import com.here.naksha.cli.copy.service.CopyService;
import com.here.naksha.cli.copy.service.StorageProvider;
import com.here.naksha.cli.copy.service.factory.CopyServiceFactory.WriteMode;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CopyServiceFactoryTest {
    private final CopyServiceFactory copyServiceFactory = new CopyServiceFactory();
    private final StorageProvider storageProvider = new StorageProvider();
    private SessionOptions sessionOptions;

    @BeforeEach
    void setUp() {
        NakshaContext.currentContext().withAppId("test");
        sessionOptions = SessionOptions.from(NakshaContext.currentContext());
    }

    @ParameterizedTest
    @EnumSource(WriteMode.class)
    void shouldCreate(WriteMode featuresWriteExecutor) {
        // When
        CopyService copyService = copyServiceFactory.create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutor
        );

        // Then
        assertNotNull(copyService);
    }

    @Test
    void shouldCreateWhenSettingThreadsForParallel() {
        // Given: parallel feature write executor
        WriteMode featuresWriteExecutor = WriteMode.PARALLEL;

        // When
        CopyService copyService = copyServiceFactory.create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutor,
                10,
                null,
                null
        );

        // Then
        assertNotNull(copyService);
    }

    @Test
    void shouldCreateWhenSettingQueueMultiForParallel() {
        // Given: parallel feature write executor
        WriteMode featuresWriteExecutor = WriteMode.PARALLEL;

        // When
        CopyService copyService = copyServiceFactory.create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutor,
                null,
                10,
                null
        );

        // Then
        assertNotNull(copyService);
    }

    @Test
    void shouldCreateWhenSettingMaxBatchSizeForParallel() {
        // Given: parallel feature write executor
        WriteMode featuresWriteExecutor = WriteMode.PARALLEL;

        // When
        CopyService copyService = copyServiceFactory.create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutor,
                null,
                null,
                10
        );

        // Then
        assertNotNull(copyService);
    }

    @Test
    void shouldThrowWhenSettingThreadsForOneShot() {
        // Given: one shot feature write executor
        WriteMode featuresWriteExecutor = WriteMode.ONE_SHOT;

        // When & Then
        assertThrows(CopyServiceFactoryException.class, () -> copyServiceFactory.create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutor,
                10,
                null,
                null
        ));
    }

    @Test
    void shouldThrowWhenSettingMaxBatchSizeForOneShot() {
        // Given: one shot feature write executor
        WriteMode featuresWriteExecutor = WriteMode.ONE_SHOT;

        // When & Then
        assertThrows(CopyServiceFactoryException.class, () -> copyServiceFactory.create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutor,
                null,
                null,
                10
        ));
    }

    @Test
    void shouldThrowWhenSettingQueueMultiForOneShot() {
        // Given: one shot feature write executor
        WriteMode featuresWriteExecutor = WriteMode.ONE_SHOT;

        // When & Then
        assertThrows(CopyServiceFactoryException.class, () -> copyServiceFactory.create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutor,
                null,
                10,
                null
        ));
    }
}