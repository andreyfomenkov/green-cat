package ui.window;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class TelemetryToolWindow implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow window) {
        Component component = window.getComponent();
        component.getParent().add(new JLabel("Hello, World!"));
        window.show(new Runnable() {

            @Override
            public void run() {
            }
        });
    }
}
