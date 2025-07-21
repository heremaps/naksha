package com.here.naksha.cli.copy;

import naksha.model.SessionOptions;

public final class SessionOptionsProvider {
    public static SessionOptions get() {
        return SessionOptions.from(null);
    }

    private SessionOptionsProvider() {
    }
}
