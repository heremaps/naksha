package com.here.naksha.cli.copy;

import com.here.naksha.cli.copy.service.*;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "copy",
        mixinStandardHelpOptions = true,
        description = "Copy data between storages.",
        exitCodeListHeading = "Exit Codes:%n",
        exitCodeList = {
                " 0:Successful program execution",
                " 1:Execution exception",
                " 2:Invalid input"
        },
        sortSynopsis = false,
        sortOptions = false
)
public class CopyCommand implements Callable<Integer> {
    private final CopyServiceFactory copyServiceFactory;
    private final SessionOptions sessionOptions;
    private final NakshaStorageProvider nakshaStorageProvider;

    public CopyCommand(
            CopyServiceFactory copyServiceFactory,
            NakshaStorageProvider nakshaStorageProvider,
            SessionOptions sessionOptions
    ) {
        this.copyServiceFactory = copyServiceFactory;
        this.sessionOptions = sessionOptions;
        this.nakshaStorageProvider = nakshaStorageProvider;
    }

    @CommandLine.Option(
            names = {"--srcStorageConfig"},
            description = "Path to file with source storage config.",
            required = true
    )
    private File srcStorageConfig;

    @CommandLine.Option(
            names = {"--srcMapId"},
            description = "Id of source map."
    )
    private @Nullable String srcMapId;

    @CommandLine.Option(
            names = {"--srcCollectionId"},
            description = "Id of source collection.",
            defaultValue = "" // TODO
    )
    private String srcCollectionId;

    @CommandLine.Option(
            names = {"--targetStorageConfig"},
            description = "Path to file with target storage config.",
            required = true
    )
    private File targetStorageConfig;

    @CommandLine.Option(
            names = {"--targetMapId"},
            description = "Id of target map."
    )
    private @Nullable String targetMapId;

    @CommandLine.Option(
            names = {"--targetCollectionId"},
            description = "Id of target collection.",
            defaultValue = "" // TODO
    )
    private String targetCollectionId;

    @Override
    public Integer call() throws NakshaStorageProviderException, CopyServiceException {
        NakshaStorage srcNakshaStorage = nakshaStorageProvider.get(srcStorageConfig);
        NakshaStorage targetNakshaStorage = nakshaStorageProvider.get(targetStorageConfig);

        CopyElement srcCopyElement = new CopyElement.Builder(srcNakshaStorage, srcCollectionId)
                .setMapId(srcMapId)
                .build();
        CopyElement targetCopyElement = new CopyElement.Builder(targetNakshaStorage, targetCollectionId)
                .setMapId(targetMapId)
                .build();

        NakshaProvider nakshaProvider = new NakshaProvider();

        CopyService copyService = copyServiceFactory.create(
                nakshaProvider,
                sessionOptions
        );

        copyService.copy(
                srcCopyElement,
                targetCopyElement
        );

        return CommandLine.ExitCode.OK;
    }
}
