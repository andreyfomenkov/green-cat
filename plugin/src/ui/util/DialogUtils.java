package ui.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public class DialogUtils {

    private static void showDialog(Project project, DialogMessageType type, String format, Object... args) {
        String message = String.format(format, args);
        Messages.showMessageDialog(project, message, type.getTitle(), type.getIcon());
    }

    public static void showInfoDialog(Project project, String format, Object... args) {
        showDialog(project, DialogMessageType.INFO, format, args);
    }

    public static void showErrorDialog(Project project, String format, Object... args) {
        showDialog(project, DialogMessageType.ERROR, format, args);
    }

    public static void showQuestionDialog(Project project, String format, Object... args) {
        showDialog(project, DialogMessageType.QUESTION, format, args);
    }

    public static void showWarningDialog(Project project, String format, Object... args) {
        showDialog(project, DialogMessageType.WARNING, format, args);
    }
}
