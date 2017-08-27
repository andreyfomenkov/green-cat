package result;

public enum DeployResult {
    OK("Deployment complete"),
    TERMINATED("Deployment terminated"),
    FAILED("Deployment failed");

    public final String message;

    DeployResult(String message) {
        this.message = message;
    }
}
