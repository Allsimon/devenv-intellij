package com.allsimon.intellij.nixlang;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.RunInEdt;
import org.affogato.lint.Edit;
import org.affogato.lsp.Updates;
import org.affogato.lsp.Updates.Update;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunInEdt(writeIntent = true)
class NixUpdatePackageTest {
    private static final String PACKAGE = """
            { stdenv, fetchFromGitHub }:
            stdenv.mkDerivation rec {
              pname = "hello";
              version = "1.2";
              src = fetchFromGitHub { owner = "acme"; repo = "hello"; rev = "v${version}"; hash = "old"; };
            }
            """;

    @RegisterExtension
    final NixFixture fixture = new NixFixture(true);

    private final List<Path> asked = new ArrayList<>();

    @AfterEach
    void restore() {
        NixUpdatePackageIntention.replace(null, null);
        NixUpdates.replace(null, null);
    }

    /** Updates as affogato's updater would, moving the version to 1.3 and the hash to "new". */
    private void updateTo13() {
        NixUpdatePackageIntention.replace((file, text, index, progress) -> {
            asked.add(file);
            progress.report("Fetching acme/hello at v1.3", 0.5);
            int version = text.indexOf("\"1.2\"");
            int hash = text.indexOf("\"old\"");
            return new Update("1.3", List.of(new Edit(version, version + 5, "\"1.3\""),
                    new Edit(hash, hash + 5, "\"new\"")), List.of());
        }, null);
    }

    @Test
    void writesTheUpdateOfThePackageAroundTheCaret() {
        updateTo13();
        fixture.run(f -> {
            var file = f.configureByText("package.nix", PACKAGE.replace("version = \"1.2\"", "ver<caret>sion = \"1.2\""));
            f.launchAction(f.findSingleIntention("Update hello"));
            String expected = PACKAGE.replace("1.2", "1.3").replace("\"old\"", "\"new\"");
            PlatformTestUtil.waitWithEventsDispatching("the update is not written",
                    () -> f.getEditor().getDocument().getText().equals(expected), 60);
            assertEquals(List.of(file.getVirtualFile().toNioPath()), asked);
        });
    }

    @Test
    void leavesAnUpToDatePackage() {
        NixUpdatePackageIntention.replace((file, text, index, progress) -> {
            asked.add(file);
            return null;
        }, null);
        fixture.run(f -> {
            f.configureByText("package.nix", PACKAGE.replace("pname", "pn<caret>ame"));
            f.launchAction(f.findSingleIntention("Update hello"));
            PlatformTestUtil.waitWithEventsDispatching("the update is not asked for", () -> !asked.isEmpty(), 60);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            assertEquals(PACKAGE, f.getEditor().getDocument().getText());
        });
    }

    @Test
    void isOfferedOnlyInAPackageThatCanBeUpdated() {
        fixture.run(f -> {
            f.configureByText("pinned.nix", PACKAGE.replace("v${version}", "0123456789abcdef0123456789abcdef01234567")
                    .replace("pname", "pn<caret>ame"));
            assertTrue(f.filterAvailableIntentions("Update hello").isEmpty());
            f.configureByText("other.nix", "{ a<caret> = 1; }");
            assertTrue(f.filterAvailableIntentions("Update").isEmpty());
        });
    }

    @Test
    void refreshesTheHashesOfAPackagePinnedToACommit() {
        String commit = "0123456789abcdef0123456789abcdef01234567";
        NixUpdatePackageIntention.replace(null, (file, text, index, progress) -> {
            asked.add(file);
            int hash = text.indexOf("\"old\"");
            return new Update("1.2", List.of(new Edit(hash, hash + 5, "\"new\"")), List.of());
        });
        fixture.run(f -> {
            String pinned = PACKAGE.replace("v${version}", commit);
            f.configureByText("pinned.nix", pinned.replace("pname", "pn<caret>ame"));
            assertTrue(f.filterAvailableIntentions("Update hello").isEmpty());
            f.launchAction(f.findSingleIntention("Refresh the hashes of hello"));
            String expected = pinned.replace("\"old\"", "\"new\"");
            PlatformTestUtil.waitWithEventsDispatching("the hash is not refreshed",
                    () -> f.getEditor().getDocument().getText().equals(expected), 60);
        });
    }

    @Test
    void leavesHashesThatAreRight() {
        NixUpdatePackageIntention.replace(null, (file, text, index, progress) -> {
            asked.add(file);
            return new Update("1.2", List.of(), List.of());
        });
        fixture.run(f -> {
            f.configureByText("package.nix", PACKAGE.replace("pname", "pn<caret>ame"));
            f.launchAction(f.findSingleIntention("Refresh the hashes of hello"));
            PlatformTestUtil.waitWithEventsDispatching("the refresh is not asked for", () -> !asked.isEmpty(), 60);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            assertEquals(PACKAGE, f.getEditor().getDocument().getText());
        });
    }

    @Test
    void marksAnOutdatedPackageWithItsUpdateAsFix() {
        NixUpdates.replace(p -> "hello".equals(p.name()) ? "1.3" : null, null);
        updateTo13();
        fixture.run(f -> {
            f.configureByText("package.nix", PACKAGE);
            var marks = f.doHighlighting().stream()
                    .filter(h -> "hello 1.3 is available".equals(h.getDescription()))
                    .toList();
            assertEquals(1, marks.size());
            assertEquals("\"1.2\"", f.getEditor().getDocument().getText()
                    .substring(marks.getFirst().getStartOffset(), marks.getFirst().getEndOffset()));
            f.getEditor().getCaretModel().moveToOffset(marks.getFirst().getStartOffset() + 1);
            f.launchAction(f.findSingleIntention("Update hello to 1.3"));
            String expected = PACKAGE.replace("1.2", "1.3").replace("\"old\"", "\"new\"");
            PlatformTestUtil.waitWithEventsDispatching("the update is not written",
                    () -> f.getEditor().getDocument().getText().equals(expected), 60);
        });
    }

    @Test
    void marksNothingWithoutANewerVersion() {
        NixUpdates.replace(p -> null, null);
        fixture.run(f -> {
            f.configureByText("package.nix", PACKAGE);
            assertTrue(f.doHighlighting().stream().noneMatch(h -> String.valueOf(h.getDescription())
                    .endsWith("is available")));
        });
    }

    @Test
    void updatesThePackagesOfTheWorkspace() {
        List<List<Path>> roots = new ArrayList<>();
        fixture.run(f -> {
            var file = f.configureByText("package.nix", PACKAGE);
            Path path = file.getVirtualFile().toNioPath();
            String expected = PACKAGE.replace("1.2", "1.3");
            NixUpdates.replace(null, (given, progress) -> {
                roots.add(given);
                return new Updates.WorkspaceUpdate(Map.of(path.toString(), new Updates.Change(PACKAGE, expected)),
                        List.of("hello: 1.2 -> 1.3"));
            });
            com.allsimon.intellij.nixupdate.NixUpdateWorkspaceAction.run(f.getProject(), List.of(path.getParent()),
                    () -> {});
            PlatformTestUtil.waitWithEventsDispatching("the workspace is not updated",
                    () -> f.getEditor().getDocument().getText().equals(expected), 60);
            assertEquals(List.of(List.of(path.getParent())), roots);
        });
    }

    @Test
    void listsThePackagesOfTheProjectWithTheirLatestVersion() {
        NixUpdates.replace(p -> "hello".equals(p.name()) ? "1.3" : null, null);
        fixture.run(f -> {
            f.configureByText("package.nix", PACKAGE);
            f.configureByText("pinned.nix", PACKAGE.replace("v${version}", "0123456789abcdef0123456789abcdef01234567")
                    .replace("\"hello\"", "\"pinned\""));
            var panel = new com.allsimon.intellij.nixupdate.NixPackagesPanel(f.getProject());
            try {
                panel.scan();
                PlatformTestUtil.waitWithEventsDispatching("the packages are not listed", () -> panel.rows().stream()
                        .anyMatch(r -> "hello".equals(r.name()) && "1.3".equals(r.latest())), 60);
                assertTrue(panel.rows().stream().anyMatch(r -> "pinned".equals(r.name())
                        && "(pinned to a commit)".equals(r.latest())));
            } finally {
                com.intellij.openapi.util.Disposer.dispose(panel);
            }
        });
    }

    @Test
    void cancellingStopsTheWork() throws Exception {
        var indicator = new com.intellij.openapi.progress.EmptyProgressIndicator();
        var interrupted = new java.util.concurrent.CompletableFuture<Boolean>();
        var cancel = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                return;
            }
            indicator.cancel();
        });
        cancel.start();
        try {
            NixUpdates.cancellable(indicator, () -> {
                try {
                    Thread.sleep(60_000);
                    interrupted.complete(false);
                } catch (InterruptedException e) {
                    interrupted.complete(true);
                    throw e;
                }
                return null;
            });
            throw new AssertionError("not cancelled");
        } catch (com.intellij.openapi.progress.ProcessCanceledException expected) {
            assertTrue(interrupted.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
    }
}
