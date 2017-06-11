package ui.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import core.GreenCat;

public class EventLog {

    private EventLog() {
    }

    public static void info(String title, String message) {
        Notification notification = new Notification(GreenCat.EVENT_LOG_GROUP_ID, title, message, NotificationType.INFORMATION);
        Notifications.Bus.notify(notification);
    }

    public static void warn(String title, String message) {
        Notification notification = new Notification(GreenCat.EVENT_LOG_GROUP_ID, title, message, NotificationType.WARNING);
        Notifications.Bus.notify(notification);
    }

    public static void error(String title, String message) {
        Notification notification = new Notification(GreenCat.EVENT_LOG_GROUP_ID, title, message, NotificationType.ERROR);
        Notifications.Bus.notify(notification);
    }

    public static void info(String message) {
        info(GreenCat.PLUGIN_NAME, message);
    }

    public static void warn(String message) {
        warn(GreenCat.PLUGIN_NAME, message);
    }

    public static void error(String message) {
        error(GreenCat.PLUGIN_NAME, message);
    }
}
