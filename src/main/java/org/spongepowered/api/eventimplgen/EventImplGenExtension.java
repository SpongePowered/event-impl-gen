package org.spongepowered.api.eventimplgen;

import java.io.File;

public class EventImplGenExtension {

    public String[] includeSrc = new String[0];
    public String[] excludeSrc = new String[0];
    public String outputDir = "";
    public String outputFactory = "";
    public boolean validateCode = true;

    public boolean isIncluded(File file) {
        file = file.getAbsoluteFile();
        boolean included = false;
        for (String include : includeSrc) {
            if (contains(new File(include).getAbsoluteFile(), file)) {
                included = true;
                break;
            }
        }
        if (!included) {
            return false;
        }
        for (String exclude : excludeSrc) {
            if (contains(new File(exclude).getAbsoluteFile(), file)) {
                return false;
            }
        }
        return true;
    }

    private boolean contains(File parent, File file) {
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
