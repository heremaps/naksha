package com.here.naksha.cli.copy.service.executors;

import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;

class OneShotFeaturesWriteExecutorTest extends FeaturesWriteExecutorsCommonTest {
    @Override
    protected FeaturesWriteExecutor createFeaturesWriteExecutor() {
        return new OneShotFeaturesWriteExecutor.Builder().build();
    }
}