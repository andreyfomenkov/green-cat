package ui.window;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import javax.swing.text.DefaultCaret;

public class TelemetryToolWindow {

    private static final String WINDOW_ID = "GreenCat Telemetry";
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
            DefaultCaret caret = (DefaultCaret) textPane.getCaret();
            caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

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

    public void clear() {
        initHtmlComposer();
    }

    public void show() {
        window.show(EMPTY_TASK);
    }

    public void hide() {
        window.hide(EMPTY_TASK);
    }

    public synchronized TelemetryToolWindow message(String format, Object... args) {
        composer.addLine(ReportMessageType.REGULAR, format, args);
        return this;
    }

    public synchronized TelemetryToolWindow warn(String format, Object... args) {
        composer.addLine(ReportMessageType.WARNING, format, args);
        return this;
    }

    public synchronized TelemetryToolWindow error(String format, Object... args) {
        composer.addLine(ReportMessageType.ERROR, format, args);
        return this;
    }

    public synchronized TelemetryToolWindow green(String format, Object... args) {
        composer.addLine(ReportMessageType.GREEN, format, args);
        return this;
    }

    public synchronized void update() {
        textPane.setText(composer.compose());
    }
}
