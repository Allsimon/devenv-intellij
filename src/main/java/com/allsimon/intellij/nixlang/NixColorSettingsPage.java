package com.allsimon.intellij.nixlang;

import com.allsimon.intellij.nixhighlight.NixColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.affogato.parser.Highlighter.Kind;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.Map;

public final class NixColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = {
            new AttributesDescriptor("Keyword", NixColors.of(Kind.KEYWORD)),
            new AttributesDescriptor("Operator", NixColors.of(Kind.OPERATOR)),
            new AttributesDescriptor("Number", NixColors.of(Kind.NUMBER)),
            new AttributesDescriptor("String//Text", NixColors.of(Kind.STRING)),
            new AttributesDescriptor("String//Escape", NixColors.of(Kind.ESCAPE)),
            new AttributesDescriptor("String//Interpolation", NixColors.of(Kind.INTERP)),
            new AttributesDescriptor("Path", NixColors.of(Kind.PATH)),
            new AttributesDescriptor("Comment", NixColors.of(Kind.COMMENT)),
            new AttributesDescriptor("Identifiers//Attribute", NixColors.of(Kind.ATTR)),
            new AttributesDescriptor("Identifiers//Parameter", NixColors.of(Kind.PARAM)),
            new AttributesDescriptor("Identifiers//Constant", NixColors.of(Kind.CONSTANT)),
            new AttributesDescriptor("Identifiers//Builtin", NixColors.of(Kind.BUILTIN)),
    };

    static final String DEMO = """
            # A package
            { <param>stdenv</param>, <param>fetchurl</param>, <param>enableTests</param> ? <constant>false</constant> }:
            stdenv.mkDerivation (<param>finalAttrs</param>: {
              <attr>pname</attr> = "hello";
              <attr>src</attr> = fetchurl {
                <attr>url</attr> = "mirror://gnu/${finalAttrs.<attr>pname</attr><interp>}</interp>.tar.gz";
              };
              <attr>patches</attr> = [ ./fix.patch ];
              <attr>doCheck</attr> = enableTests && <builtin>builtins</builtin>.<attr>currentSystem</attr> != "x86_64-darwin";
              /* Runs once installed */
              <attr>postInstall</attr> = ''
                echo "''${out}\\n"
              '';
              <attr>meta</attr>.<attr>priority</attr> = 10;
            })
            """;

    @Override
    public Icon getIcon() {
        return NixFileType.INSTANCE.getIcon();
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new NixHighlighter();
    }

    @Override
    public @NotNull String getDemoText() {
        return DEMO;
    }

    @Override
    public @NotNull Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return Map.of(
                "attr", NixColors.of(Kind.ATTR),
                "param", NixColors.of(Kind.PARAM),
                "constant", NixColors.of(Kind.CONSTANT),
                "builtin", NixColors.of(Kind.BUILTIN),
                "interp", NixColors.of(Kind.INTERP));
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Nix";
    }
}
