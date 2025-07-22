package com.here.naksha.cli;

import com.here.naksha.cli.copy.CopyCommand;
import com.here.naksha.cli.copy.service.CopyServiceFactory;
import com.here.naksha.cli.copy.service.StorageProvider;
import picocli.CommandLine;

final class CommandFactory implements CommandLine.IFactory {
    private final CommandLine.IFactory fallback = CommandLine.defaultFactory();

    @Override
    public <K> K create(Class<K> cls) throws Exception {
        if (cls == CopyCommand.class) {
            return (K) new CopyCommand(
                    new CopyServiceFactory(),
                    new StorageProvider()
            );
        }
        return fallback.create(cls);
    }
}
