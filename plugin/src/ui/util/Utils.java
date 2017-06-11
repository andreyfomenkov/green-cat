package ui.util;

public class Utils {

    private Utils() {
    }

    public static boolean isNullOrEmpty(String text) {
        return text == null || text.trim().isEmpty();
    }
}
