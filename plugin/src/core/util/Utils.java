package core.util;

public class Utils {

    public static String formatNanoTimeToSeconds(long nanoTime) {
        String format = String.format("%.1f", nanoTime / Math.pow(10, 9));
        return format.endsWith(".0") ? format.substring(0, 1) : format;
    }
}
