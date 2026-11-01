package com.allsimon.intellij.nixfmt;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class GraalnixFormattingServiceTest extends BasePlatformTestCase {
    public void testReformatsNixFiles() {
        PsiFile file = myFixture.configureByText("default.nix", "{pkgs,...}:{  a  =  1;}");

        reformat(file);

        myFixture.checkResult("{ pkgs, ... }: { a = 1; }\n");
    }

    public void testLeavesInvalidNixUnchanged() {
        PsiFile file = myFixture.configureByText("default.nix", "{ a = ; }");

        reformat(file);

        myFixture.checkResult("{ a = ; }");
    }

    public void testClaimsOnlyNixFiles() {
        GraalnixFormattingService service = new GraalnixFormattingService();

        assertTrue(service.canFormat(myFixture.configureByText("flake.nix", "{ }")));
        assertFalse(service.canFormat(myFixture.configureByText("notes.txt", "{ }")));
    }

    private void reformat(PsiFile file) {
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            CodeStyleManager.getInstance(getProject()).reformat(file);
        });
    }
}
