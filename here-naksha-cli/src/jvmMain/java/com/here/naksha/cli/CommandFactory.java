package com.here.naksha.cli;

import com.here.naksha.cli.copy.CopyCommand;
import com.here.naksha.cli.copy.service.StorageProvider;
import com.here.naksha.cli.copy.service.factory.CopyServiceFactory;
import com.here.naksha.cli.stream.StreamCopyCommand;
import com.here.naksha.cli.stream.StreamProviders;
import picocli.CommandLine;

final class CommandFactory implements CommandLine.IFactory {
    private final CommandLine.IFactory fallback = CommandLine.defaultFactory();

    @Override
    public <K> K create(Class<K> cls) throws Exception {
        if (cls == CopyCommand.class) {
            return cls.cast(new CopyCommand(
                    new CopyServiceFactory(),
                    new StorageProvider()
            ));
        }
        if (cls == StreamCopyCommand.class) {
            return cls.cast(new StreamCopyCommand(StreamProviders.load()));
        }
        return fallback.create(cls);
    }
}
