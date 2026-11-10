package com.allsimon.intellij.run;

import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Links the hash a fixed-output derivation got, as Nix prints it under the one it was specified: following the link
 * writes it in place of the specified one in the project, or of the only placeholder hash there.
 */
public final class NixHashMismatchFilter implements Filter {
    private static final Pattern SPECIFIED = Pattern.compile("specified:\\s*(\\S+)");
    private static final Pattern GOT = Pattern.compile("got:\\s*((?:sha1|sha256|sha512)-[A-Za-z0-9+/]+=*)");
    private static final Pattern PLACEHOLDER = Pattern.compile("lib\\.fake(?:Hash|Sha256|Sha512)\\b");

    private final Project project;
    private @Nullable String specified;

    NixHashMismatchFilter(Project project) {
        this.project = project;
    }

    @Override
    public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
        Matcher specifiedHash = SPECIFIED.matcher(line);
        if (specifiedHash.find()) {
            specified = specifiedHash.group(1);
            return null;
        }
        Matcher got = GOT.matcher(line);
        if (!got.find() || specified == null) {
            return null;
        }
        int lineStart = entireLength - line.length();
        return new Result(lineStart + got.start(1), lineStart + got.end(1),
                new Replace(project, specified, got.group(1)));
    }

    /** Writes {@code got} where the project has {@code specified}, or its only placeholder. */
    record Replace(Project project, String specified, String got) implements HyperlinkInfo {
        private record Spot(VirtualFile file, Document document, int start, int end, String text) {}

        @Override
        public void navigate(@NotNull Project ignored) {
            List<Spot> spots = find(Pattern.compile(Pattern.quote(specified)), got);
            if (spots.isEmpty()) {
                spots = find(PLACEHOLDER, "\"" + got + "\"");
                if (spots.size() != 1) {
                    notify(spots.isEmpty()
                            ? "Found neither " + specified + " nor a placeholder hash in the project's .nix files"
                            : "Found " + spots.size() + " placeholder hashes: replace the one of this build with "
                                    + got);
                    return;
                }
            }
            List<Spot> found = spots;
            WriteCommandAction.runWriteCommandAction(project, "Replace Hash", null, () -> {
                for (Spot spot : found.reversed()) {
                    spot.document().replaceString(spot.start(), spot.end(), spot.text());
                }
            });
            Spot first = found.getFirst();
            new OpenFileDescriptor(project, first.file(), first.start()).navigate(true);
        }

        private List<Spot> find(Pattern pattern, String replacement) {
            List<Spot> spots = new ArrayList<>();
            for (VirtualFile file : FilenameIndex.getAllFilesByExt(project, "nix",
                    GlobalSearchScope.projectScope(project))) {
                Document document = FileDocumentManager.getInstance().getDocument(file);
                if (document == null) {
                    continue;
                }
                Matcher matcher = pattern.matcher(document.getCharsSequence());
                while (matcher.find()) {
                    spots.add(new Spot(file, document, matcher.start(), matcher.end(), replacement));
                }
            }
            return spots;
        }

        private void notify(String message) {
            NotificationGroupManager.getInstance().getNotificationGroup("Devenv")
                    .createNotification(message, NotificationType.WARNING).notify(project);
        }
    }

    public static final class Provider implements ConsoleFilterProvider {
        @Override
        public Filter @NotNull [] getDefaultFilters(@NotNull Project project) {
            return new Filter[]{new NixHashMismatchFilter(project)};
        }
    }
}
