package com.allsimon.intellij.maven;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Hands the freshly opened project to {@link DevenvMavenHome}.
 * <p>
 * Nothing is evaluated here: the service pushes the work onto a background task of its own, so the
 * startup activity never blocks on devenv.
 */
public final class DevenvMavenHomeActivity implements ProjectActivity, DumbAware {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        DevenvMavenHome.getInstance(project).start();
        return Unit.INSTANCE;
    }
}
