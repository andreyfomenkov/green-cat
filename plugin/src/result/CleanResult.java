package result;

public enum CleanResult {
    OK("Cleanup complete"),
    NO_DEVICES_CONNECTED("No devices/emulators connected"),
    MULTIPLE_DEVICES_CONNECTED("Multiple devices/emulators found"),
    UNKNOWN_ERROR("Failed to clean");

    public final String message;

    CleanResult(String message) {
        this.message = message;
    }
}
