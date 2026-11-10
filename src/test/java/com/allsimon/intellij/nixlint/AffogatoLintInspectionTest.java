package com.allsimon.intellij.nixlint;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class AffogatoLintInspectionTest extends BasePlatformTestCase {
    private static final String SAMPLE = "let\n  unused = 1;\n  x = 2;\nin\nx == true\n";

    private final AffogatoLintInspection inspection = new AffogatoLintInspection();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(inspection);
    }

    public void testReportsFindingsWithTheirRule() {
        myFixture.configureByText("default.nix", SAMPLE);

        assertSameElements(descriptions(myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING)),
                "unused let binding 'unused' (unused-let-binding)",
                "comparison with 'true'; if the other side is always a boolean, use it directly (bool-comparison)");
    }

    public void testReportsErrorsAsErrors() {
        myFixture.configureByText("default.nix", "x: x + y\n");

        assertSameElements(descriptions(myFixture.doHighlighting(HighlightSeverity.ERROR)),
                "undefined variable 'y' (undefined-variable)");
    }

    public void testFixesAFinding() {
        myFixture.configureByText("default.nix", SAMPLE.replace("unused =", "<caret>unused ="));

        IntentionAction fix = myFixture.findSingleIntention("Fix unused-let-binding");
        myFixture.launchAction(fix);

        myFixture.checkResult("let\n  x = 2;\nin\nx == true\n");
    }

    public void testSkipsDisabledRules() {
        inspection.getOptionController().setOption("rule.bool-comparison", false);
        myFixture.configureByText("default.nix", SAMPLE);

        assertEquals(false, inspection.getOptionController().getOption("rule.bool-comparison"));
        assertEquals("bool-comparison", inspection.disabledRules);
        assertSameElements(descriptions(myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING)),
                "unused let binding 'unused' (unused-let-binding)");
    }

    public void testIgnoresTheShellInjectedIntoAPhase() {
        myFixture.configureByText("default.nix",
                "{\n  patchPhase = ''\n    substituteInPlace configure \\\n      --replace /usr/local /\n  '';\n}\n");

        assertEmpty(descriptions(myFixture.doHighlighting(HighlightSeverity.ERROR)));
    }

    public void testLeavesParseErrorsToTheParser() {
        myFixture.configureByText("default.nix", "{ a = ; }\n");

        assertSameElements(descriptions(myFixture.doHighlighting(HighlightSeverity.ERROR)), "expected expression");
    }

    public void testReportsWhatNixRejectsBeyondTheGrammar() {
        myFixture.configureByText("default.nix", "{ a = 1; a = 2; }\n");

        assertTrue(descriptions(myFixture.doHighlighting(HighlightSeverity.ERROR)).stream()
                .anyMatch(description -> description.endsWith("(syntax-error)")));
    }

    public void testSuppressesARuleOnItsLine() {
        myFixture.configureByText("default.nix", "x: [\n  (x == <caret>true)\n]\n");

        myFixture.launchAction(myFixture.findSingleIntention("Suppress bool-comparison on this line"));

        myFixture.checkResult("x: [\n  # affogato: ignore bool-comparison\n  (x == true)\n]\n");
        assertEmpty(descriptions(myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING)));
    }

    public void testSuppressesARuleInTheFile() {
        myFixture.configureByText("default.nix", "x: x == <caret>true\n");

        myFixture.launchAction(myFixture.findSingleIntention("Suppress bool-comparison in this file"));

        myFixture.checkResult("# affogato: ignore-file bool-comparison\nx: x == true\n");
    }

    public void testReportsAPlaceholderHash() {
        myFixture.configureByText("default.nix", "{ lib, fetchurl }: fetchurl { url = \"x\"; hash = lib.fakeHash; }\n");

        assertTrue(descriptions(myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING)).stream()
                .anyMatch(description -> description.endsWith("(fake-hash)")));
    }

    public void testIgnoresOtherFiles() {
        myFixture.configureByText("notes.txt", SAMPLE);

        assertEmpty(descriptions(myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING)));
    }

    private static List<String> descriptions(List<HighlightInfo> infos) {
        return infos.stream().map(HighlightInfo::getDescription).toList();
    }
}
