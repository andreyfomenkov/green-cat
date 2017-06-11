package ui.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public class DialogUtils {

    private static void showDialog(Project project, MessageType type, String format, Object... args) {
        String message = String.format(format, args);
        Messages.showMessageDialog(project, message, type.getTitle(), type.getIcon());
    }

    public static void showInfoDialog(Project project, String format, Object... args) {
        showDialog(project, MessageType.INFO, format, args);
    }

    public static void showErrorDialog(Project project, String format, Object... args) {
        showDialog(project, MessageType.ERROR, format, args);
    }

    public static void showQuestionDialog(Project project, String format, Object... args) {
        showDialog(project, MessageType.QUESTION, format, args);
    }

    public static void showWarningDialog(Project project, String format, Object... args) {
        showDialog(project, MessageType.WARNING, format, args);
    }
}
