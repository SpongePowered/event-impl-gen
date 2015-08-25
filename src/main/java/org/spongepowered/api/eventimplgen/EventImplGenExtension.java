package org.spongepowered.api.eventimplgen;

import java.io.File;

public class EventImplGenExtension {

    public static String[] includeSrc = new String[0];
    public static String[] excludeSrc = new String[0];

    public static boolean isIncluded(File file) {
        file = file.getAbsoluteFile();
        boolean included = false;
        for (String include : EventImplGenExtension.includeSrc) {
            if (contains(new File(include).getAbsoluteFile(), file)) {
                included = true;
                break;
            }
        }
        if (!included) {
            return false;
        }
        for (String exclude : EventImplGenExtension.excludeSrc) {
            if (contains(new File(exclude).getAbsoluteFile(), file)) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(File parent, File file) {
        File nextParent = file;
        do {
            if (parent.equals(nextParent)) {
                return true;
            }
            nextParent = nextParent.getParentFile();
        } while (nextParent != null);
        return false;
    }

}
