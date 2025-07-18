package com.here.naksha.cli;

import com.here.naksha.cli.copy.CopyCommand;
import com.here.naksha.cli.copy.NakshaStorageProvider;
import com.here.naksha.cli.copy.service.CopyServiceFactory;
import com.here.naksha.cli.copy.service.StorageProvider;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import picocli.CommandLine;

class CommandFactory implements CommandLine.IFactory {
    private final CommandLine.IFactory fallback = CommandLine.defaultFactory();

    @Override
    public <K> K create(Class<K> cls) throws Exception {
        NakshaContext ctx = NakshaContext.currentContext().withAppId("appId");
        if (cls == CopyCommand.class) {
            return (K) new CopyCommand(
                    new CopyServiceFactory(),
                    new NakshaStorageProvider(),
                    new StorageProvider(),
                    SessionOptions.from(ctx)
            );
        }
        return fallback.create(cls);
    }
}
