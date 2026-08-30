package com.allsimon.intellij.processes;

import org.jetbrains.annotations.NotNull;

/**
 * One process of one devenv root: what the Services tool window shows as a service.
 * <p>
 * The root travels with the process name because a project can declare the same name in several
 * devenv.nix files, and because the platform hands a service back to the contributor - to group it,
 * to describe it, to update it in place - with nothing else to identify it by.
 * <p>
 * The name rather than the {@link DevenvProcess} it stands for: the tree keeps this value as a node's
 * identity and only finds the node again if it still compares equal, so it has to survive the status
 * changing. {@link DevenvProcessManager} is compared by identity, which is what one root is.
 */
record DevenvProcessNode(@NotNull DevenvProcessManager manager, @NotNull String name) {
}
