package com.here.naksha.cli;

import com.here.naksha.cli.copy.CopyCommand;
import com.here.naksha.cli.copy.service.StorageProvider;
import com.here.naksha.cli.copy.service.factory.CopyServiceFactory;
import com.here.naksha.cli.stream.CliContext;
import com.here.naksha.cli.stream.ConfigCommand;
import com.here.naksha.cli.stream.ProviderCommand;
import com.here.naksha.cli.stream.StreamCommand;
import com.here.naksha.cli.stream.SyncCommand;
import picocli.CommandLine;

import java.util.function.Supplier;

final class CommandFactory implements CommandLine.IFactory {
    private final CommandLine.IFactory fallback = CommandLine.defaultFactory();
    private final Supplier<CliContext> contextSupplier;
    private CliContext context;

    CommandFactory() {
        this(CliContext::load);
    }

    CommandFactory(Supplier<CliContext> contextSupplier) {
        this.contextSupplier = contextSupplier;
    }

    private CliContext context() {
        if (context == null) context = contextSupplier.get();
        return context;
    }

    @Override
    public <K> K create(Class<K> cls) throws Exception {
        if (cls == CopyCommand.class) {
            return cls.cast(new CopyCommand(
                    new CopyServiceFactory(),
                    new StorageProvider()
            ));
        }
        if (cls == ProviderCommand.class) return cls.cast(new ProviderCommand(context()));
        if (cls == ConfigCommand.class) return cls.cast(new ConfigCommand(context()));
        if (cls == StreamCommand.class) return cls.cast(new StreamCommand(context()));
        if (cls == SyncCommand.class) return cls.cast(new SyncCommand(context()));
        return fallback.create(cls);
    }
}
