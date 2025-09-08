package com.here.naksha.cli;

import picocli.CommandLine;

public final class VersionInfo implements CommandLine.IVersionProvider {
    public static final String VERSION = VersionInfo.class.getPackage().getImplementationVersion();

    @Override
    public String[] getVersion() {
        return new String[]{"naksha-cli %s".formatted(VERSION)};
    }

    private VersionInfo() {
    }
}