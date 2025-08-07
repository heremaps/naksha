package com.here.naksha.cli.copy;

import com.here.naksha.cli.copy.service.*;
import com.here.naksha.cli.parsers.JsonFileParser;
import com.here.naksha.cli.parsers.JsonFileParserException;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.nio.file.Path;
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
public final class CopyCommand implements Callable<Integer> {
    private final CopyServiceFactory copyServiceFactory;
    private final JsonFileParser jsonFileParser;
    private final StorageProvider storageProvider;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec commandSpec;

    public CopyCommand(
            CopyServiceFactory copyServiceFactory,
            StorageProvider storageProvider
    ) {
        this.copyServiceFactory = copyServiceFactory;
        this.jsonFileParser = new JsonFileParser();
        this.storageProvider = storageProvider;
    }

    @CommandLine.Option(
            names = {"--srcStorageConfig"},
            description = "Path to file with source storage config.",
            required = true
    )
    private Path srcStorageConfig;

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
    private Path targetStorageConfig;

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
    public Integer call() throws JsonFileParserException, CopyServiceException {
        NakshaStorage srcNakshaStorage = jsonFileParser.parse(srcStorageConfig, NakshaStorage.class);
        NakshaStorage targetNakshaStorage = jsonFileParser.parse(targetStorageConfig, NakshaStorage.class);

        CopyElement srcCopyElement = new CopyElement.Builder(srcNakshaStorage, srcCollectionId)
                .setMapId(srcMapId)
                .build();
        CopyElement targetCopyElement = new CopyElement.Builder(targetNakshaStorage, targetCollectionId)
                .setMapId(targetMapId)
                .build();

        NakshaContext.currentContext().withAppId("nakshacli");
        SessionOptions sessionOptions = SessionOptions.from(NakshaContext.currentContext());

        CopyService copyService = copyServiceFactory.create(
                storageProvider,
                sessionOptions
        );

        copyService.copy(
                srcCopyElement,
                targetCopyElement
        );

        PrintWriter printWriter = commandSpec.commandLine().getOut();
        printWriter.println("success!");

        return CommandLine.ExitCode.OK;
    }
}
