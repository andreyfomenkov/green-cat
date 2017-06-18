package ui.window;

public enum ReportMessageType {

    REGULAR("#FFFFFF", false),
    WARNING("#FFFF00", false),
    ERROR("#FF0000", true),
    GREEN("#00FF00", true);

    public final String color;
    public final boolean bold;

    ReportMessageType(String color, boolean bold) {
        this.color = color;
        this.bold = bold;
    }
}
