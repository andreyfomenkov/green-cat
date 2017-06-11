package core.telemetry;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static ui.util.Utils.isNullOrEmpty;

public class Step {

    private final String name;
    private @Nullable final String purpose;
    private final List<String> dumpList = new ArrayList<>();

    public static Step create(String name) {
        return new Step(name, null, null);
    }

    public static Step create(String name, @Nullable String purpose) {
        return new Step(name, purpose, null);
    }

    private Step(String name, @Nullable String purpose, String dump) {
        this.name = name;
        this.purpose = purpose;
        addLine(dump);
    }

    public Step addLine(String dump) {
        if (!isNullOrEmpty(dump)) {
            dumpList.add(dump);
        }
        return this;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getPurpose() {
        return purpose;
    }

    public String getDump() {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < dumpList.size(); i++) {
            String line = dumpList.get(i);
            builder.append(i == 0 ? "" : "\n").append(line);
        }

        return builder.toString();
    }
}
