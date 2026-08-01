package com.allsimon.intellij.jdk;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DevenvJdkTest {
    /**
     * What 'devenv eval languages.java.enable languages.java.jdk.package.home' prints, store path shortened.
     */
    private static final String ENABLED = """
            {
              "languages.java.enable": true,
              "languages.java.jdk.package.home": "/nix/store/xxx-openjdk-21.0.12+2/lib/openjdk"
            }
            """;

    @Test
    public void readsTheJdkHomeOfAProjectThatEnablesJava() {
        assertEquals("/nix/store/xxx-openjdk-21.0.12+2/lib/openjdk", DevenvJdk.parseHome(ENABLED));
    }

    /**
     * The JDK option has a default, so it evaluates to a perfectly good path even in a project that
     * never asked for Java - which is exactly why the enable flag is evaluated along with it.
     */
    @Test
    public void ignoresTheJdkOfAProjectThatDoesNotEnableJava() {
        assertNull(DevenvJdk.parseHome("""
                {
                  "languages.java.enable": false,
                  "languages.java.jdk.package.home": "/nix/store/xxx-openjdk-21.0.12+2/lib/openjdk"
                }
                """));
    }

    @Test
    public void stripsTheColoursDevenvWritesEvenIntoAPipe() {
        assertEquals("/nix/store/xxx-openjdk-21.0.12+2/lib/openjdk", DevenvJdk.parseHome(
                "\033[0m{\n  \"languages.java.enable\": true,\n"
                        + "  \"languages.java.jdk.package.home\": \"/nix/store/xxx-openjdk-21.0.12+2/lib/openjdk\"\n}\n"));
    }

    @Test
    public void returnsNothingWhenAnAttributeIsMissing() {
        assertNull(DevenvJdk.parseHome("{\"languages.java.enable\": true}"));
        assertNull(DevenvJdk.parseHome(
                "{\"languages.java.jdk.package.home\": \"/nix/store/xxx-openjdk-21.0.12+2/lib/openjdk\"}"));
    }

    @Test
    public void returnsNothingWhenTheAttributesHaveAnUnexpectedShape() {
        assertNull(DevenvJdk.parseHome("""
                {
                  "languages.java.enable": "true",
                  "languages.java.jdk.package.home": ["/nix/store/xxx-openjdk-21.0.12+2/lib/openjdk"]
                }
                """));
        assertNull(DevenvJdk.parseHome("""
                {
                  "languages.java.enable": true,
                  "languages.java.jdk.package.home": "   "
                }
                """));
    }

    @Test
    public void returnsNothingWhenDevenvPrintedSomethingElseEntirely() {
        assertNull(DevenvJdk.parseHome("\"just a string\""));
    }
}
