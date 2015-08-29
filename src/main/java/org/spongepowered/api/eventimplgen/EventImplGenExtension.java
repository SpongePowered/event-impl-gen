/*
 * This file is part of Event Implementation Generator, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2015-2015 SpongePowered <http://spongepowered.org/>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.api.eventimplgen;

import java.io.File;

public class EventImplGenExtension {

    public String[] includeSrc = new String[0];
    public String[] excludeSrc = new String[0];
    public String outputDir = "";
    public String outputFactory = "";
    public boolean validateCode = true;
    public String eventImplCreateMethod = "";
    public String disambAnnot = "";

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
