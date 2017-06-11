package core.input;

import com.google.common.collect.ImmutableList;

import java.util.List;

public class AnnotationProcessors {

    private final ImmutableList<String> pathList;

    public AnnotationProcessors(List<String> pathList) {
        this.pathList = ImmutableList.copyOf(pathList);
    }

    public List<String> get() {
        return pathList;
    }
}
