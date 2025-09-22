package com.here.naksha.cli.copy.service.executors;

import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;

class ParallelFeaturesWriteExecutorTest extends FeaturesWriteExecutorsCommonTest {
    @Override
    protected FeaturesWriteExecutor createFeaturesWriteExecutor() {
        return new ParallelFeaturesWriteExecutor();
    }
}