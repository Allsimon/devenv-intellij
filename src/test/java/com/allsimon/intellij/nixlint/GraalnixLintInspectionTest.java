package com.allsimon.intellij.nixlint;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class GraalnixLintInspectionTest extends BasePlatformTestCase {
    private static final String SAMPLE = "let\n  unused = 1;\n  x = 2;\nin\nx == true\n";

    private final GraalnixLintInspection inspection = new GraalnixLintInspection();

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

    public void testIgnoresOtherFiles() {
        myFixture.configureByText("notes.txt", SAMPLE);

        assertEmpty(descriptions(myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING)));
    }

    private static List<String> descriptions(List<HighlightInfo> infos) {
        return infos.stream().map(HighlightInfo::getDescription).toList();
    }
}
