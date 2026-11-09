package com.allsimon.intellij.nixlang;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class NixIntentionsTest extends BasePlatformTestCase {
    public void testConvertsADoubleQuotedStringToIndented() {
        myFixture.configureByText("default.nix", "{ a = \"x<caret>\\ny ${z}\"; }");

        myFixture.launchAction(myFixture.findSingleIntention("Convert to ''...''"));

        myFixture.checkResult("{ a = ''x\ny ${z}''; }");
    }

    public void testConvertsAnIndentedStringToDoubleQuoted() {
        myFixture.configureByText("default.nix", "{ a = ''\n  x<caret>\n''; }");

        myFixture.launchAction(myFixture.findSingleIntention("Convert to \"...\""));

        myFixture.checkResult("{ a = \"x\\n\"; }");
    }

    public void testIsNotOfferedWhereTheValueWouldChange() {
        myFixture.configureByText("default.nix", "{ a = \"  x<caret>\"; \"b\" = 1; }");

        assertEmpty(myFixture.filterAvailableIntentions("Convert to ''...''"));
    }

    public void testIsNotOfferedOnAnAttributeName() {
        myFixture.configureByText("default.nix", "{ \"a<caret>\" = 1; }");

        assertEmpty(myFixture.filterAvailableIntentions("Convert to ''...''"));
    }

    public void testExpandsAndCollapsesABinding() {
        myFixture.configureByText("default.nix", "{ meta.lic<caret>ense = lib.licenses.mit; }");

        myFixture.launchAction(myFixture.findSingleIntention("Expand into a nested set"));
        myFixture.checkResult("{ meta = { license = lib.licenses.mit; }; }");

        myFixture.getEditor().getCaretModel().moveToOffset(3);
        myFixture.launchAction(myFixture.findSingleIntention("Collapse into an attribute path"));
        myFixture.checkResult("{ meta.license = lib.licenses.mit; }");
    }

    public void testOffersNoRewriteOfASingleAttribute() {
        myFixture.configureByText("default.nix", "{ a<caret> = 1; }");

        assertEmpty(myFixture.filterAvailableIntentions("Expand into a nested set"));
        assertEmpty(myFixture.filterAvailableIntentions("Collapse into an attribute path"));
    }

    public void testInlinesALetBindingIntoEachUse() {
        myFixture.configureByText("default.nix", "let\n  a<caret> = 1 + 2;\n  b = 3;\nin\na * a + b\n");

        myFixture.launchAction(myFixture.findSingleIntention("Inline binding"));

        myFixture.checkResult("let\n  b = 3;\nin\n(1 + 2) * (1 + 2) + b\n");
    }

    public void testRemovesALetLeftEmpty() {
        myFixture.configureByText("default.nix", "let n<caret> = \"x\"; in [ n n ]");

        myFixture.launchAction(myFixture.findSingleIntention("Inline binding"));

        myFixture.checkResult("[ \"x\" \"x\" ]");
    }

    public void testIsNotOfferedWhereTheValueWouldMeanSomethingElse() {
        myFixture.configureByText("default.nix", "x: let a<caret> = x; in x: a");

        assertEmpty(myFixture.filterAvailableIntentions("Inline binding"));
    }

    public void testIsNotOfferedForARecursiveOrInheritedBinding() {
        myFixture.configureByText("default.nix", "let a<caret> = [ a ]; in a");
        assertEmpty(myFixture.filterAvailableIntentions("Inline binding"));

        myFixture.configureByText("default.nix", "let a<caret> = 1; in { inherit a; }");
        assertEmpty(myFixture.filterAvailableIntentions("Inline binding"));
    }

    public void testExtractsIntoTheEnclosingLet() {
        myFixture.configureByText("default.nix",
                "{ pkgs }:\nlet\n  a = 1;\nin\n[ (<selection>pkgs.lib.optional</selection> true a) ]\n");

        myFixture.launchAction(myFixture.findSingleIntention("Extract into a let binding"));

        myFixture.checkResult("{ pkgs }:\nlet\n  a = 1;\n  optional = pkgs.lib.optional;\nin\n[ (optional true a) ]\n");
    }

    public void testWrapsTheValueWhenNoLetFits() {
        myFixture.configureByText("default.nix", "{ src = fetchurl { url = <selection>\"https://x\"</selection>; }; }");

        myFixture.launchAction(myFixture.findSingleIntention("Extract into a let binding"));

        myFixture.checkResult("let text = \"https://x\"; in { src = fetchurl { url = text; }; }");
    }

    public void testSkipsALetWhereTheVariablesWouldMeanSomethingElse() {
        myFixture.configureByText("default.nix", "let a = 1; in x: { b = <selection>x + a</selection>; }");

        myFixture.launchAction(myFixture.findSingleIntention("Extract into a let binding"));

        myFixture.checkResult("let a = 1; in x: let value = x + a; in { b = value; }");
    }

    public void testPicksAFreeName() {
        myFixture.configureByText("default.nix", "let text = 1; in [ <selection>\"a\"</selection> ]");

        myFixture.launchAction(myFixture.findSingleIntention("Extract into a let binding"));

        myFixture.checkResult("let text = 1; text2 = \"a\"; in [ text2 ]");
    }

    public void testKeepsAFunctionAFunction() {
        myFixture.configureByText("default.nix", "{ stdenv }: stdenv.mkDerivation { name = <selection>\"a\"</selection>; }");

        myFixture.launchAction(myFixture.findSingleIntention("Extract into a let binding"));

        myFixture.checkResult("{ stdenv }: let text = \"a\"; in stdenv.mkDerivation { name = text; }");
    }

    public void testNeedsASelectionOfAWholeExpression() {
        myFixture.configureByText("default.nix", "[ (a <selection>+ b</selection>) ]");

        assertEmpty(myFixture.filterAvailableIntentions("Extract into a let binding"));
    }

    public void testWrapsABindingInMkIfWithTheConditionSelected() {
        myFixture.configureByText("default.nix",
                "{ lib, config, ... }:\nlet cfg = config.x; in { services.ng<caret>inx = a // b; }");

        myFixture.launchAction(myFixture.findSingleIntention("Wrap in mkIf"));

        myFixture.checkResult("{ lib, config, ... }:\nlet cfg = config.x; in { services.nginx = lib.mkIf "
                + "<selection>cfg.enable<caret></selection> (a // b); }");
    }

    public void testWrapsWithMkIfWhenLibIsNotInScope() {
        myFixture.configureByText("default.nix", "{ mkIf }: { a<caret> = { }; }");

        myFixture.launchAction(myFixture.findSingleIntention("Wrap in mkIf"));

        myFixture.checkResult("{ mkIf }: { a = mkIf <selection>condition<caret></selection> { }; }");
    }

    public void testRemovesMkIf() {
        myFixture.configureByText("default.nix", "{ a<caret> = lib.mkIf cfg.enable (b // c); }");

        assertEmpty(myFixture.filterAvailableIntentions("Wrap in mkIf"));
        myFixture.launchAction(myFixture.findSingleIntention("Remove mkIf"));

        myFixture.checkResult("{ a = b // c; }");
    }
}
