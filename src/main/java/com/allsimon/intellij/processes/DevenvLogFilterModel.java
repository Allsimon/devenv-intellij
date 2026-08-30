package com.allsimon.intellij.processes;

import com.intellij.diagnostic.logging.LogFilter;
import com.intellij.diagnostic.logging.LogFilterListener;
import com.intellij.diagnostic.logging.LogFilterModel;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the log console's filter row acts on: which stream a line came from, and whether the text
 * filter and the selected stream let it through.
 * <p>
 * The console re-runs {@link #processLine} over every line it has kept whenever a filter changes, so
 * the answer has to come from the line itself rather than from what the reader happened to be doing
 * at the time - hence {@link DevenvLogConsole#STDERR_MARK}, which
 * {@link DevenvLogConsole.StripMarkFormatter} takes off again before anything is printed.
 */
final class DevenvLogFilterModel extends LogFilterModel {
    private static final LogFilter ALL = new LogFilter("All output");
    private static final LogFilter STDERR_ONLY = new LogFilter("Errors only");

    private final List<LogFilterListener> listeners = new CopyOnWriteArrayList<>();

    private volatile LogFilter selected = ALL;
    private volatile String customFilter = "";

    @Override
    public @NotNull String getCustomFilter() {
        return customFilter;
    }

    @Override
    public void updateCustomFilter(String filter) {
        super.updateCustomFilter(filter);
        customFilter = filter == null ? "" : filter;
        for (LogFilterListener listener : listeners) {
            listener.onTextFilterChange();
        }
    }

    @Override
    public void addFilterListener(LogFilterListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeFilterListener(LogFilterListener listener) {
        listeners.remove(listener);
    }

    @Override
    public @NotNull List<? extends LogFilter> getLogFilters() {
        return List.of(ALL, STDERR_ONLY);
    }

    @Override
    public boolean isFilterSelected(LogFilter filter) {
        return selected == filter;
    }

    @Override
    public void selectFilter(LogFilter filter) {
        if (selected == filter) {
            return;
        }
        selected = filter;
        for (LogFilterListener listener : listeners) {
            listener.onFilterStateChange(filter);
        }
    }

    @Override
    public @NotNull MyProcessingResult processLine(@Nullable String line) {
        boolean stderr = line != null && line.startsWith(DevenvLogConsole.STDERR_MARK);
        Key<?> outputType = stderr ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT;
        boolean applicable = line != null && isApplicable(line) && (selected != STDERR_ONLY || stderr);
        return new MyProcessingResult(outputType, applicable, null);
    }
}
