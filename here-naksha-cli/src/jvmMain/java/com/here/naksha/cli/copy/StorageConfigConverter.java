package com.here.naksha.cli.copy;

import naksha.base.JvmBoxingUtil;
import naksha.base.Platform;
import naksha.model.objects.NakshaStorage;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

class StorageConfigConverter implements CommandLine.ITypeConverter<File> {
    public File convert(String value) throws CommandLine.TypeConversionException {
        File file = new File(value);
        Path path = file.toPath(); // if needed

        if(!file.exists()) {
            throw new CommandLine.TypeConversionException("File does not exist!");
        }

        if(!file.isFile()) {
            throw new CommandLine.TypeConversionException("It is not a file!");
        }

        String json;

        try {
            json = Files.readString(path);
        } catch (Exception e) {
            throw new CommandLine.TypeConversionException("Problem with reading!");
        }

        try {
            Object rawConfig = Platform.fromJSON(json);
            JvmBoxingUtil.box(rawConfig, NakshaStorage.class);
        } catch (Exception e) {
            throw new CommandLine.TypeConversionException("Problem with json parsing!");
        }

        return file;
    }
}
