package com.allsimon.intellij.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class NixRunConfigurationTest extends BasePlatformTestCase {
    public void testRunsNixWithItsArguments() throws Exception {
        NixRunConfiguration configuration = configuration(NixRunConfiguration.Tool.NIX, "--version");
        configuration.setWorkingDirectory(System.getProperty("java.io.tmpdir"));

        var output = new CapturingProcessHandler(configuration.commandLine()).runProcess(60_000);

        assertEquals(0, output.getExitCode());
        assertTrue(output.getStdout(), output.getStdout().contains("nix"));
    }

    public void testSplitsTheArgumentsAsAShellWould() throws ExecutionException {
        NixRunConfiguration configuration = configuration(NixRunConfiguration.Tool.NIX,
                "eval --expr '1 + 1'");

        assertEquals(java.util.List.of("eval", "--expr", "1 + 1"), configuration.commandLine().getParametersList()
                .getList());
    }

    public void testRunsWhereItIsToldElseInTheProject() throws ExecutionException {
        NixRunConfiguration configuration = configuration(NixRunConfiguration.Tool.DEVENV, "test");
        assertEquals(getProject().getBasePath(), configuration.directory());

        configuration.setWorkingDirectory("/tmp");
        assertEquals("/tmp", configuration.directory());
        assertEquals("devenv test", configuration.suggestedName());
    }

    public void testTheGutterReusesTheConfigurationOfACommand() {
        var command = new NixRunTargets.Command(NixRunConfiguration.Tool.NIX, "build .#hello");
        var first = new NixRunLineMarkerContributor.RunCommand(command, "/tmp").settings(getProject());
        var second = new NixRunLineMarkerContributor.RunCommand(command, "/tmp").settings(getProject());

        assertSame(first, second);
        assertTrue(first.isTemporary());
        assertEquals("nix build .#hello", first.getName());
        assertNotSame(first, new NixRunLineMarkerContributor.RunCommand(command, "/var").settings(getProject()));
    }

    private NixRunConfiguration configuration(NixRunConfiguration.Tool tool, String arguments) {
        var factory = NixRunConfigurationType.getInstance().factory(tool);
        var settings = RunManager.getInstance(getProject()).createConfiguration("test", factory);
        var configuration = (NixRunConfiguration) settings.getConfiguration();
        configuration.setArguments(arguments);
        return configuration;
    }
}
