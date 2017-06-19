package core.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ClassPathResolver {

    private static final String DIVIDER = ":";
    private static final String LIBRARY_SUFFIX = ".jar";
    private final String classpath;
    private List<String> srcPaths;
    private List<String> libPaths;

    public ClassPathResolver(String classpath) {
        this.classpath = classpath;
    }

    public void resolve(boolean verbose) {
        String[] split = classpath.split(DIVIDER);
        List<String> srcPaths = new ArrayList<>();
        List<String> libPaths = new ArrayList<>();

        for (String item : split) {
            if (item.endsWith(LIBRARY_SUFFIX)) {
                libPaths.add(item);

//                if (verbose) {
//                    Logger.d("[LIB] %s", item);
//                }
            } else {
                srcPaths.add(item);

//                if (verbose) {
//                    Logger.d("[SRC] %s", item);
//                }
            }
        }

        int srcSize = srcPaths.size();
        int libSize = libPaths.size();

        Iterator<String> iterator = srcPaths.iterator();

        while (iterator.hasNext()) {
            String path = iterator.next();
            File file = new File(path);

            if (!file.exists()) {
                iterator.remove();

//                if (verbose) {
//                    Logger.d("[SRC NOT FOUND] %s -> REMOVED FROM CLASSPATH_PROD", path);
//                }
            }
        }

        iterator = libPaths.iterator();

        while (iterator.hasNext()) {
            String path = iterator.next();
            File file = new File(path);

            if (!file.exists()) {
                iterator.remove();

//                if (verbose) {
//                    Logger.d("[LIB NOT FOUND] %s -> REMOVED FROM CLASSPATH_PROD", path);
//                }
            }
        }

        this.srcPaths = srcPaths;
        this.libPaths = libPaths;
//        Logger.d("[CLASSPATH] Total SRC: %d -> %d", srcSize, srcPaths.size());
//        Logger.d("[CLASSPATH] Total LIB: %d -> %d", libSize, libPaths.size());
    }

    public List<String> getSrcPaths() {
        if (srcPaths == null) {
            throw new RuntimeException("Classpath not resolved yet");
        }
        return new ArrayList<>(srcPaths);
    }

    public List<String> getLibPaths() {
        if (libPaths == null) {
            throw new RuntimeException("Classpath not resolved yet");
        }
        return new ArrayList<>(libPaths);
    }
}
