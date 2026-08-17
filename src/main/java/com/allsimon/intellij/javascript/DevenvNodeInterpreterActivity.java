package com.allsimon.intellij.javascript;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Hands the freshly opened project to {@link DevenvNodeInterpreter}.
 * <p>
 * Nothing is evaluated here: the service pushes the work onto a background task of its own, so the
 * startup activity never blocks on devenv.
 */
public final class DevenvNodeInterpreterActivity implements ProjectActivity, DumbAware {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        DevenvNodeInterpreter.getInstance(project).start();
        return Unit.INSTANCE;
    }
}
