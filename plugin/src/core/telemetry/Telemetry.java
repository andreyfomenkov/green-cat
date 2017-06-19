package core.telemetry;

import ui.window.TelemetryToolWindow;

import java.util.ArrayList;
import java.util.List;

public class Telemetry {

    private enum Type {
        MESSAGE,
        WARNING,
        ERROR,
        GREEN
    }

    private static class MessageItem {

        public final Type type;
        public final String value;

        public MessageItem(Type type, String value) {
            this.type = type;
            this.value = value;
        }
    }

    private final List<MessageItem> itemList = new ArrayList<>();

    public Telemetry clear() {
        itemList.clear();
        return this;
    }

    public Telemetry message(String format, Object... args) {
        return addMessage(Type.MESSAGE, format, args);
    }

    public Telemetry warn(String format, Object... args) {
        return addMessage(Type.WARNING, format, args);
    }

    public Telemetry error(String format, Object... args) {
        return addMessage(Type.ERROR, format, args);
    }

    public Telemetry green(String format, Object... args) {
        return addMessage(Type.GREEN, format, args);
    }

    private Telemetry addMessage(Type type, String format, Object... args) {
        itemList.add(new MessageItem(type, String.format(format, args)));
        return this;
    }

    public void populateToolWindow(TelemetryToolWindow window) {
        for (MessageItem item : itemList) {
            switch (item.type) {
                case MESSAGE:
                    window.message(item.value);
                    break;
                case WARNING:
                    window.warn(item.value);
                    break;
                case ERROR:
                    window.error(item.value);
                    break;
                case GREEN:
                    window.green(item.value);
                    break;
                default:
                    throw new RuntimeException("Unknown item type: " + item.type);
            }
        }
    }
}
