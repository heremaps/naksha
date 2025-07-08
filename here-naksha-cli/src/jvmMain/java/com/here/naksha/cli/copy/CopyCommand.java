package com.here.naksha.cli.copy;

import picocli.CommandLine;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "copy",
        mixinStandardHelpOptions = true,
        description = "Copy data between storages.",
        exitCodeListHeading = "Exit Codes:%n",
        exitCodeList = {
                " 0:Successful program execution",
                " 1:Invalid input",
                " 2:Execution exception"
        },
        sortSynopsis = false,
        sortOptions = false
)
public class CopyCommand implements Callable<Integer> {

    @CommandLine.Option(
            names = { "--srcStorageConfig" },
            description = "Path to file with source storage config.",
            required = true,
            converter = StorageConfigConverter.class
    )
    private File srcStorageConfig;

    @CommandLine.Option(
            names = { "--srcMapId" },
            description = "Id of source map."
    )
    private Optional<Integer> srcMapId;

    @CommandLine.Option(
            names = { "--srcCollectionId" },
            description = "Id of source collection."
    )
    private Optional<Integer> srcCollectionId;

    @CommandLine.Option(
            names = { "--targetStorageConfig" },
            description = "Path to file with target storage config.",
            required = true,
            converter = StorageConfigConverter.class
    )
    private File targetStorageConfig;

    @CommandLine.Option(
            names = { "--targetMapId" },
            description = "Id of target map."
    )
    private Optional<Integer> targetMapId;

    @CommandLine.Option(
            names = { "--targetCollectionId" },
            description = "Id of target collection."
    )
    private Optional<Integer> targetCollectionId;

    @Override
    public Integer call() throws Exception {
        return CommandLine.ExitCode.OK;
    }
}
