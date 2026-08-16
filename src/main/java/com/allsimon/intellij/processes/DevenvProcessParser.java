package com.allsimon.intellij.processes;

import com.allsimon.intellij.core.DevenvCli;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns devenv CLI output into {@link DevenvProcess} values.
 * <p>
 * Kept free of any process spawning - as the other CLI-output readers in this plugin are - so the
 * output shapes below can be pinned down by unit tests. That matters most for
 * {@link #parseList(String)}: {@code devenv processes list} has no {@code --json} mode, so the plugin
 * reads a human-facing format that carries no compatibility promise. Anything unrecognised degrades to
 * {@link DevenvProcess.Status#UNKNOWN} rather than failing the refresh.
 */
final class DevenvProcessParser {
    private static final String PROCESSES_KEY = "processes";
    private static final String EXEC_KEY = "exec";
    private static final String RESTARTS_SEPARATOR = "restarts:";
    private static final String NO_PROCESSES = "No processes found.";

    private DevenvProcessParser() {
    }

    /**
     * Reads the {@code {"processes": {"<name>": {"exec": "..."}}}} document printed by
     * {@code devenv eval processes}. Values are only inspected for {@code exec}; anything else about
     * them (process-compose settings and such) is ignored, so an unexpected shape costs at most the
     * command tooltip.
     */
    static @NotNull List<DevenvProcess> parseDeclared(@NotNull String evalOutput) {
        JsonElement root = JsonParser.parseString(DevenvCli.stripAnsi(evalOutput));
        if (!root.isJsonObject()) {
            return List.of();
        }
        JsonElement processes = root.getAsJsonObject().get(PROCESSES_KEY);
        if (processes == null || !processes.isJsonObject()) {
            return List.of();
        }

        List<DevenvProcess> declared = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : processes.getAsJsonObject().entrySet()) {
            declared.add(DevenvProcess.declared(entry.getKey(), exec(entry.getValue())));
        }
        return declared;
    }

    private static String exec(@NotNull JsonElement process) {
        if (!process.isJsonObject()) {
            return null;
        }
        JsonObject object = process.getAsJsonObject();
        JsonElement exec = object.get(EXEC_KEY);
        return exec != null && exec.isJsonPrimitive() ? exec.getAsString() : null;
    }

    /**
     * Reads the table printed by {@code devenv processes list}, whose rows look like
     * {@code "ticker                         ready restarts: 0"} - a padded name, the phase, and an
     * optional restart count.
     */
    static @NotNull Map<String, DevenvProcess> parseList(@NotNull String listOutput) {
        Map<String, DevenvProcess> reported = new LinkedHashMap<>();
        for (String rawLine : DevenvCli.stripAnsi(listOutput).split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith(NO_PROCESSES)) {
                continue;
            }

            String[] nameAndRest = line.split("\\s+", 2);
            if (nameAndRest.length < 2) {
                continue;
            }
            String name = nameAndRest[0];
            String rest = nameAndRest[1];

            Integer restarts = null;
            int restartsAt = rest.indexOf(RESTARTS_SEPARATOR);
            if (restartsAt >= 0) {
                restarts = parseRestarts(rest.substring(restartsAt + RESTARTS_SEPARATOR.length()));
                rest = rest.substring(0, restartsAt);
            }

            reported.put(name, new DevenvProcess(name, null, parseStatus(rest.strip()), restarts));
        }
        return reported;
    }

    private static Integer parseRestarts(@NotNull String text) {
        try {
            return Integer.valueOf(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static DevenvProcess.@NotNull Status parseStatus(@NotNull String phase) {
        // devenv reports snake_case phases ('not_started', 'gave_up'); the enum names match once
        // separators are normalised, so new phases only need a constant here.
        String normalized = phase.replace(' ', '_').replace('-', '_').toUpperCase(Locale.ROOT);
        for (DevenvProcess.Status status : DevenvProcess.Status.values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return DevenvProcess.Status.UNKNOWN;
    }
}
