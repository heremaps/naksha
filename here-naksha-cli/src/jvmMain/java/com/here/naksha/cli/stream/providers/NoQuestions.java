package com.here.naksha.cli.stream.providers;

import naksha.model.IStorageConfigBuilder;
import org.jetbrains.annotations.NotNull;

import static naksha.base.NakshaExceptionKt.illegalState;

/** A config builder for providers without configuration, done from the start. */
final class NoQuestions implements IStorageConfigBuilder {
    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public @NotNull String getDescription() {
        throw illegalState("No questions");
    }

    @Override
    public @NotNull String getQuestion() {
        throw illegalState("No questions");
    }

    @Override
    public String[] getOptions() {
        throw illegalState("No questions");
    }

    @Override
    public String getDefaultAnswer() {
        throw illegalState("No questions");
    }

    @Override
    public boolean isSecret() {
        throw illegalState("No questions");
    }

    @Override
    public void answer(@NotNull String answer) {
        throw illegalState("No questions");
    }

    @Override
    public @NotNull String build() {
        return "{}";
    }
}
