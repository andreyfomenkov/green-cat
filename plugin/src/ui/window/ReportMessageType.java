package ui.window;

public enum ReportMessageType {

    REGULAR("#EEEEEE", false),
    WARNING("#EEEE00", false),
    ERROR("#EE0000", true),
    GREEN("#00EE00", true);

    public final String color;
    public final boolean bold;

    ReportMessageType(String color, boolean bold) {
        this.color = color;
        this.bold = bold;
    }
}
