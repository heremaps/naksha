package com.here.naksha.cli.stream;

import naksha.base.NakshaException;
import naksha.model.IStorageConfigBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;

/** Asks questions on the console, similar to {@code rclone config}. */
final class Questions {
    private final BufferedReader in;
    private final PrintWriter out;
    private final @Nullable Console console;

    Questions(@NotNull BufferedReader in, @NotNull PrintWriter out, @Nullable Console console) {
        this.in = in;
        this.out = out;
        this.console = console;
    }

    @NotNull String ask(@NotNull String question, @Nullable List<String> options, @Nullable String defaultAnswer, boolean secret) {
        while (true) {
            StringBuilder prompt = new StringBuilder(question);
            if (options != null) prompt.append(", one of: ").append(String.join(", ", options));
            if (defaultAnswer != null && !secret) prompt.append(" [").append(defaultAnswer).append(']');
            prompt.append("> ");
            String answer = read(prompt.toString(), secret);
            if (answer.isEmpty()) {
                if (defaultAnswer != null) return defaultAnswer;
                out.println("An answer is required.");
                continue;
            }
            if (options != null && !options.contains(answer)) {
                out.println("Please answer one of: " + String.join(", ", options));
                continue;
            }
            return answer;
        }
    }

    @NotNull String ask(@NotNull String question, @Nullable String defaultAnswer) {
        return ask(question, null, defaultAnswer, false);
    }

    boolean askYesNo(@NotNull String question, boolean defaultAnswer) {
        return ask(question + " (y/n)", List.of("y", "n"), defaultAnswer ? "y" : "n", false).equals("y");
    }

    int askInt(@NotNull String question, int defaultAnswer) {
        while (true) {
            String answer = ask(question, String.valueOf(defaultAnswer));
            try {
                int value = Integer.parseInt(answer);
                if (value > 0) return value;
            } catch (NumberFormatException ignored) {
                // asked again
            }
            out.println("Please enter a number greater than 0.");
        }
    }

    /** Asks the questions of the builder until it is done, and returns the configuration. */
    @NotNull String run(@NotNull IStorageConfigBuilder builder) {
        while (!builder.isDone()) {
            if (!builder.getDescription().isEmpty()) out.println(builder.getDescription());
            String[] options = builder.getOptions();
            String answer = ask(builder.getQuestion(), options == null ? null : Arrays.asList(options), builder.getDefaultAnswer(), builder.isSecret());
            try {
                builder.answer(answer);
            } catch (NakshaException e) {
                out.println(e.getMessage());
            }
        }
        return builder.build();
    }

    private String read(String prompt, boolean secret) {
        if (secret && console != null) {
            out.flush();
            char[] password = console.readPassword("%s", prompt);
            if (password == null) throw new IllegalStateException("No more input");
            return new String(password).trim();
        }
        out.print(prompt);
        out.flush();
        try {
            String line = in.readLine();
            if (line == null) throw new IllegalStateException("No more input");
            return line.trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
