package com.here.naksha.lib.handlers;

import naksha.model.NakshaContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;

abstract public class AbstractTest {
    protected AbstractTest() {}
    protected AbstractTest(String appId) {
        AbstractTest.appId = appId;
    }
    protected AbstractTest(String appId, String mapId) {
        AbstractTest.appId = appId;
        AbstractTest.mapId = mapId;
    }

    private static String mapId;
    protected static @NotNull String getMapId() {
        var mapId = AbstractTest.mapId;
        if (mapId == null) {
            mapId = "test_map";
            AbstractTest.mapId = mapId;
        }
        return mapId;
    }
    private static String appId;
    protected static @NotNull String getAppId() {
        var appId = AbstractTest.appId;
        if (appId == null) {
            appId = "test_app";
            AbstractTest.appId = appId;
        }
        return appId;
    }
    private static NakshaContext nakshaContext;

    protected static @NotNull NakshaContext useNakshaContext(@Nullable String author, boolean su) {
        var ctx = nakshaContext;
        if (ctx != null) {
            return ctx.attachToCurrentThread().withAuthor(author).withSu(su);
        }
        ctx = NakshaContext.newInstance(getAppId(), author, null, su);
        nakshaContext = ctx;
        return ctx.attachToCurrentThread();
    }

    @BeforeEach
    protected void setupContext() {
        useNakshaContext(null, false);
    }

}
