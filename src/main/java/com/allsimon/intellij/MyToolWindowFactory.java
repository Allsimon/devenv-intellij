package com.allsimon.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import java.util.Random;
import javax.swing.JButton;
import org.jetbrains.annotations.NotNull;

public class MyToolWindowFactory implements ToolWindowFactory {
    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        MyToolWindow myToolWindow = new MyToolWindow();
        Content content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false);
        toolWindow.getContentManager().addContent(content);
    }

    static class MyToolWindow {
        private final JBPanel<?> content = new JBPanel<>();

        MyToolWindow() {
            JBLabel label = new JBLabel(MyMessageBundle.message("toolwindow.MyToolWindow.number.label", "?"));

            content.add(label);

            JButton shuffle = new JButton(MyMessageBundle.message("toolwindow.MyToolWindow.shuffle.button"));
            shuffle.addActionListener(event -> label.setText(MyMessageBundle.message(
                    "toolwindow.MyToolWindow.number.label", new Random(System.currentTimeMillis()).nextInt(1000)
            )));
            content.add(shuffle);
        }

        JBPanel<?> getContent() {
            return content;
        }
    }
}
