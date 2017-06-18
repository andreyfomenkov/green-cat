package core.settings;

import com.google.common.collect.ImmutableList;

import java.util.List;

public class Classpath {

    private final ImmutableList<String> pathList;

    public Classpath(List<String> pathList) {
        this.pathList = ImmutableList.copyOf(pathList);
    }

    public List<String> get() {
        return pathList;
    }
}
