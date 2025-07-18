package com.here.naksha.cli.copy;

import com.here.naksha.cli.copy.service.CopyService;
import com.here.naksha.cli.copy.service.CopyServiceFactory;
import com.here.naksha.cli.copy.service.StorageProvider;
import naksha.model.SessionOptions;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestCopyCommand {
    private final CopyService copyService = mock();
    private final CopyServiceFactory copyServiceFactory = mock();
    private final CopyCommand copyCommand;
    private final SessionOptions sessionOptions = mock();
    private final NakshaStorageProvider nakshaStorageProvider = mock();
    private final StorageProvider storageProvider = mock();

    public TestCopyCommand() {
        copyCommand = new CopyCommand(
                copyServiceFactory,
                nakshaStorageProvider,
                storageProvider,
                sessionOptions
        );
        when(copyServiceFactory.create(eq(storageProvider), eq(sessionOptions))).thenReturn(copyService);
    }

    public SessionOptions getSessionOptions() {
        return sessionOptions;
    }

    public CopyCommand getCopyCommand() {
        return copyCommand;
    }

    public CopyServiceFactory getCopyServiceFactory() {
        return copyServiceFactory;
    }

    public CopyService getCopyService() {
        return copyService;
    }

    public NakshaStorageProvider getNakshaStorageProvider() {
        return nakshaStorageProvider;
    }

    public StorageProvider getNakshaProvider() {
        return storageProvider;
    }
}
