/*
 * This file is part of Event Implementation Generator, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
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

import java.util.Collections;
import java.util.Map;

public class EventImplGenExtension {

    public String[] includePkg = new String[0];
    public String[] excludePkg = new String[0];
    public String outputDir = "";
    public String outputFactory = "";
    public boolean validateCode = true;
    public String eventImplCreateMethod = "";
    public String sortPriorityPrefix = "";
    public Map<String, String> groupingPrefixes = Collections.emptyMap();

    public boolean isIncluded(String qualifiedName) {
        boolean included = false;
        for (String include : includePkg) {
            if (contains(include, qualifiedName)) {
                included = true;
                break;
            }
        }
        if (!included) {
            return false;
        }
        for (String exclude : excludePkg) {
            if (contains(exclude, qualifiedName)) {
                return false;
            }
        }
        return true;
    }

    private boolean contains(String _package, String qualifiedName) {
        String nextPackage = qualifiedName;
        do {
            if (_package.equals(nextPackage)) {
                return true;
            }
            final int index = Math.max(nextPackage.lastIndexOf('.'), 0);
            nextPackage = nextPackage.substring(0, index);
        } while (!nextPackage.isEmpty());
        return false;
    }
}
