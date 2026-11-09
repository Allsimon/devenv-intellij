package com.allsimon.intellij.nixlang;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class NixPathTest extends BasePlatformTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("pkgs/hello/default.nix", "{ }");
        myFixture.addFileToProject("pkgs/hello/package.nix", "{ }");
    }

    public void testAPathRefersToItsFile() {
        myFixture.configureByText("default.nix", "import ./pkgs/hello/pack<caret>age.nix");

        PsiFileSystemItem target = (PsiFileSystemItem) myFixture.getFile()
                .findReferenceAt(myFixture.getCaretOffset()).resolve();

        assertEquals("package.nix", target.getName());
    }

    public void testEachSegmentRefersToItsDirectory() {
        myFixture.configureByText("default.nix", "callPackage ./pk<caret>gs/hello { }");

        PsiFileSystemItem target = (PsiFileSystemItem) myFixture.getFile()
                .findReferenceAt(myFixture.getCaretOffset()).resolve();

        assertTrue(target.isDirectory());
        assertEquals("pkgs", target.getName());
    }

    public void testCompletesTheFilesOfADirectory() {
        myFixture.configureByText("default.nix", "import ./pkgs/hello/p<caret>");

        myFixture.completeBasic();

        assertEquals("import ./pkgs/hello/package.nix", myFixture.getEditor().getDocument().getText());
    }

    public void testRenamingAFileRewritesItsPaths() {
        myFixture.configureByText("default.nix", "import ./pkgs/hello/package.nix");
        PsiFile target = myFixture.getPsiManager().findFile(
                myFixture.findFileInTempDir("pkgs/hello/package.nix"));

        myFixture.renameElement(target, "hello.nix");

        assertEquals("import ./pkgs/hello/hello.nix", myFixture.getEditor().getDocument().getText());
    }

    public void testLeavesInterpolatedAndAbsolutePathsAlone() {
        myFixture.configureByText("default.nix", "[ ./pkgs/${x} /etc/hosts ~/x ]");

        assertNull(myFixture.getFile().findReferenceAt(myFixture.getFile().getText().indexOf("pkgs")));
        assertNull(myFixture.getFile().findReferenceAt(myFixture.getFile().getText().indexOf("hosts")));
    }
}
