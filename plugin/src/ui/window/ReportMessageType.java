package ui.window;

public enum ReportMessageType {

    REGULAR("#EEEEEE", false),
    WARNING("#EEEE44", false),
    ERROR("#EE4444", true),
    GREEN("#44EE44", true);

    public final String color;
    public final boolean bold;

    ReportMessageType(String color, boolean bold) {
        this.color = color;
        this.bold = bold;
    }
}
