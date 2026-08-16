package com.allsimon.intellij.processes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One entry of the {@code processes} attribute set of a devenv.nix, together with whatever the
 * running process manager reports about it.
 */
record DevenvProcess(@NotNull String name, @Nullable String exec, @NotNull Status status, @Nullable Integer restarts) {

    /**
     * The phases devenv reports in {@code devenv processes list}. {@link #NOT_STARTED} doubles as the
     * state of a declared process while no process manager is running at all.
     */
    enum Status {
        NOT_STARTED("not started"),
        WAITING("waiting"),
        RUNNING("running"),
        READY("ready"),
        COMPLETED("completed"),
        STOPPED("stopped"),
        EXITED("exited"),
        GAVE_UP("gave up"),
        UNKNOWN("unknown");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        @NotNull String displayName() {
            return displayName;
        }

        /** Whether the process manager currently has this process alive, i.e. stopping it makes sense. */
        boolean isAlive() {
            return this == WAITING || this == RUNNING || this == READY;
        }
    }

    static @NotNull DevenvProcess declared(@NotNull String name, @Nullable String exec) {
        return new DevenvProcess(name, exec, Status.NOT_STARTED, null);
    }

    /** This process as reported by the manager, keeping the {@code exec} we only learn from evaluation. */
    @NotNull DevenvProcess withRuntimeState(@NotNull DevenvProcess reported) {
        return new DevenvProcess(name, exec, reported.status(), reported.restarts());
    }
}
