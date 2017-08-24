package ui.window;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;

public class TelemetryToolWindow {

    private static final String WINDOW_ID = "ui.GreenCat Telemetry";
    private static final Runnable EMPTY_TASK = () -> {};
    private static TelemetryToolWindow instance;
    private ToolWindow window;
    private JTextPane textPane;
    private HtmlReportComposer composer;

    private TelemetryToolWindow(Project project) {
        ToolWindowManager manager = ToolWindowManager.getInstance(project);

        if (window == null) {
            textPane = new JTextPane();
            textPane.setEditable(false);
            JBScrollPane scrollPane = new JBScrollPane(textPane);

            textPane.setContentType("text/html");
            initHtmlComposer();

            window = manager.registerToolWindow(WINDOW_ID, scrollPane, ToolWindowAnchor.BOTTOM);
            window.show(EMPTY_TASK);
        }
    }

    private void initHtmlComposer() {
        composer = new HtmlReportComposer();
        textPane.setText(composer.compose());
    }

    public static TelemetryToolWindow get(Project project) {
        if (instance == null) {
            instance = new TelemetryToolWindow(project);
        }
        return instance;
    }

    public TelemetryToolWindow clear() {
        initHtmlComposer();
        return this;
    }

    public void show() {
        window.show(EMPTY_TASK);
    }

    public void hide() {
        window.hide(EMPTY_TASK);
    }

    public TelemetryToolWindow message(String format, Object... args) {
        composer.addLine(ReportMessageType.REGULAR, format, args);
        textPane.setText(composer.compose());
        return this;
    }

    public TelemetryToolWindow warn(String format, Object... args) {
        composer.addLine(ReportMessageType.WARNING, format, args);
        textPane.setText(composer.compose());
        return this;
    }

    public TelemetryToolWindow error(String format, Object... args) {
        composer.addLine(ReportMessageType.ERROR, format, args);
        textPane.setText(composer.compose());
        return this;
    }

    public TelemetryToolWindow green(String format, Object... args) {
        composer.addLine(ReportMessageType.GREEN, format, args);
        textPane.setText(composer.compose());
        return this;
    }
}
