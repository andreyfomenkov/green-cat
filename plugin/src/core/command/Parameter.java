package core.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Parameter {

    private final String option;
    private final List<String> args;

    public Parameter(String option, String... args) {
        this.option = option;
        this.args = args == null ? null : Arrays.asList(args);
    }

    public Parameter(String option, List<String> args) {
        this.option = option;
        this.args = args;
    }

    public String getOption() {
        return option;
    }

    public List<String> getArgs() {
        return new ArrayList<>(args);
    }

    public String composeString() {
        StringBuilder builder = new StringBuilder(option);

        if (args != null) {
            for (String arg : args) {
                builder.append(" ").append(arg);
            }
        }

        return builder.toString();
    }
}
