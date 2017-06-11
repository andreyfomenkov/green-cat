package core.telemetry;

import java.util.ArrayList;
import java.util.List;

import static ui.util.Utils.isNullOrEmpty;

public class Telemetry {

    private final List<Step> stepList = new ArrayList<>();

    private Telemetry() {
    }

    public static class Builder {

        private final Telemetry telemetry;

        public Builder() {
            telemetry = new Telemetry();
        }

        public Builder add(Step step) {
            telemetry.stepList.add(step);
            return this;
        }

        public Telemetry build() {
            return telemetry;
        }
    }

    public String getDump() {
        StringBuilder builder = new StringBuilder();
        builder.append("    * * * * * * * * * * *    ");
        builder.append("* * *  BUILD TELEMETRY  * * *");
        builder.append("    * * * * * * * * * * *    ");
        int index = 1;

        for (Step step : stepList) {
            String name = step.getName();
            String purpose = step.getPurpose();
            String dump = step.getDump();
            builder.append(" ");

            if (isNullOrEmpty(purpose)) {
                builder.append(String.format("[STEP %d]: %s", index, name));
            } else {
                builder.append(String.format("[STEP %d]: %s (%s)", index, name, purpose));
            }

            builder.append(dump);
            index++;
        }

        builder.append(" ");
        builder.append("* * * * * * * * * * * * * *");
        return builder.toString();
    }
}
