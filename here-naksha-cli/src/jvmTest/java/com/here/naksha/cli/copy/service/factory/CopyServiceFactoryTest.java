package com.here.naksha.cli.copy.service.factory;

import com.here.naksha.cli.copy.service.CopyService;
import com.here.naksha.cli.copy.service.StorageProvider;
import com.here.naksha.cli.copy.service.executors.model.BatchableExecutor;
import com.here.naksha.cli.copy.service.executors.model.ThreadableExecutor;
import com.here.naksha.cli.copy.service.factory.CopyServiceFactory.FeaturesWriteExecutors;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
    @EnumSource(FeaturesWriteExecutors.class)
    void shouldCreate(FeaturesWriteExecutors featuresWriteExecutor) {
        // When
        CopyService copyService = copyServiceFactory.create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutor,
                null,
                null
        );

        // Then
        assertNotNull(copyService);
    }

    @Test
    void shouldSetThreadsForThreadable() {
        // Given: threadable feature write executor
        FeaturesWriteExecutors featuresWriteExecutor = FeaturesWriteExecutors.PARALLEL;
        assumeTrue(featuresWriteExecutor.createInstance() instanceof ThreadableExecutor);

        // When
        CopyService copyService = copyServiceFactory.create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutor,
                10,
                null
        );

        // Then
        assertNotNull(copyService);
    }

    @Test
    void shouldSetMaxBatchSizeForBatchable() {
        // Given: batchable feature write executor
        FeaturesWriteExecutors featuresWriteExecutor = FeaturesWriteExecutors.PARALLEL;
        assumeTrue(featuresWriteExecutor.createInstance() instanceof BatchableExecutor);

        // When
        CopyService copyService = copyServiceFactory.create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutor,
                null,
                10
        );

        // Then
        assertNotNull(copyService);
    }

    @Test
    void shouldThrowWhenSettingThreadsForNonThreadable() {
        // Given: non-threadable feature write executor
        FeaturesWriteExecutors featuresWriteExecutor = FeaturesWriteExecutors.ONE_SHOT;
        assumeFalse(featuresWriteExecutor.createInstance() instanceof ThreadableExecutor);

        // When & Then
        assertThrows(CopyServiceFactoryException.class, () -> copyServiceFactory.create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutor,
                10,
                null
        ));
    }

    @Test
    void shouldThrowWhenSettingMaxBatchSizeForNonBatchable() {
        // Given: non-batchable feature write executor
        FeaturesWriteExecutors featuresWriteExecutor = FeaturesWriteExecutors.ONE_SHOT;
        assumeFalse(featuresWriteExecutor.createInstance() instanceof BatchableExecutor);

        // When & Then
        assertThrows(CopyServiceFactoryException.class, () -> copyServiceFactory.create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutor,
                null,
                10
        ));
    }
}