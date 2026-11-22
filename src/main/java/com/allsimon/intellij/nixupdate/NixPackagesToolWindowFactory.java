package com.allsimon.intellij.nixupdate;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/** The Nix Packages tool window: the packages of the project, found when it opens. */
public final class NixPackagesToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        NixPackagesPanel panel = new NixPackagesPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, null, false);
        Disposer.register(content, panel);
        toolWindow.getContentManager().addContent(content);
        panel.scan();
    }
}
