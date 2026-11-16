package com.allsimon.intellij.nixlang;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/** An attribute of a file, selected through {@code import} in another. */
@RunInEdt(writeIntent = true)
class NixImportedAttributeTest {
    private static final String EXPORTS = "{ pkgs }: {\n  foo = 1;\n  bar = 2;\n}\n";
    private static final String USES = "let a = import ./a.nix { pkgs = null; }; in [ a.foo a.bar (import ./a.nix { }).foo ]\n";

    @RegisterExtension
    final NixFixture fixture = new NixFixture(true);

    @Test
    void resolvesASelectionToItsBinding() {
        fixture.run(f -> {
            f.addFileToProject("a.nix", EXPORTS);
            open(f, "b.nix", USES.replaceFirst("a\\.foo", "a.f<caret>oo"));

            var target = f.getFile().findReferenceAt(f.getCaretOffset()).resolve();

            assertInstanceOf(NixNamedElement.class, target);
            assertEquals("a.nix", target.getContainingFile().getName());
            assertEquals(EXPORTS.indexOf("foo"), target.getTextOffset());
        });
    }

    @Test
    void leavesASelectionOfAnUnknownSetUnresolved() {
        fixture.run(f -> {
            open(f, "b.nix", "pkgs: pkgs.hel<caret>lo");

            assertNull(f.getFile().findReferenceAt(f.getCaretOffset()).resolve());
        });
    }

    @Test
    void findsTheSelectionsInOtherFiles() {
        fixture.run(f -> {
            f.addFileToProject("b.nix", USES);
            open(f, "a.nix", EXPORTS.replace("foo", "f<caret>oo"));

            assertEquals(2, f.findUsages(f.getElementAtCaret()).size());
        });
    }

    @Test
    void renamesFromASelection() {
        fixture.run(f -> {
            var exports = f.addFileToProject("a.nix", EXPORTS);
            open(f, "b.nix", USES.replaceFirst("a\\.foo", "a.f<caret>oo"));

            assertEquals(exports.findElementAt(EXPORTS.indexOf("foo")).getParent(),
                    TargetElementUtil.findTargetElement(f.getEditor(), TargetElementUtil.getInstance().getAllAccepted()));
            f.renameElementAtCaret("baz");

            f.checkResult("let a = import ./a.nix { pkgs = null; }; in [ a.baz a.bar (import ./a.nix { }).baz ]\n");
            assertEquals(EXPORTS.replace("foo", "baz"), exports.getText());
        });
    }

    /** Opens {@code text} as {@code name}, which {@code configureByText} would change on disk, out of reach of {@code import}. */
    private static void open(CodeInsightTestFixture f, String name, String text) {
        int caret = text.indexOf("<caret>");
        f.configureFromExistingVirtualFile(f.addFileToProject(name, text.replace("<caret>", "")).getVirtualFile());
        f.getEditor().getCaretModel().moveToOffset(caret);
    }
}
