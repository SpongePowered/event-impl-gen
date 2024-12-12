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
package org.spongepowered.eventimplgen.processor;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class EventImplWriterTest {

    private EventImplWriter writer;
    private Elements elements;
    private FileWriter fileWriter;

    @BeforeEach
    public void setUp() throws IOException {
        elements = mock(Elements.class);
        fileWriter = mock(FileWriter.class);
        writer = new EventImplWriter(
                mock(Filer.class),
                elements,
                mock(PropertySorter.class),
                Set.of(),
                mock(EventGenOptions.class),
                mock(FactoryInterfaceGenerator.class),
                mock(ClassGenerator.class)
        );
    }

    @Test
    public void testSerializeFileList() throws IOException {
        TypeElement element1 = mock(TypeElement.class);
        TypeElement element2 = mock(TypeElement.class);
        when(elements.getBinaryName(element1)).thenReturn(() -> "test.Element1");
        when(elements.getBinaryName(element2)).thenReturn(() -> "test.Element2");

        writer.allFoundProperties.put(element1, new EventData(List.of(), Set.of()));
        writer.allFoundProperties.put(element2, new EventData(List.of(), Set.of()));

        writer.serializeFileList();

        Gson gson = new Gson();
        List<String> expectedList = List.of("test.Element1", "test.Element2");
        String expectedJson = gson.toJson(expectedList);

        String actualJson = Files.readString(Paths.get("fileList.json"));
        assertEquals(expectedJson, actualJson);
    }

    @Test
    public void testDeserializeFileList() throws IOException {
        Gson gson = new Gson();
        List<String> expectedList = List.of("test.Element1", "test.Element2");
        String json = gson.toJson(expectedList);
        Files.writeString(Paths.get("fileList.json"), json);

        List<String> actualList = writer.deserializeFileList();
        assertEquals(expectedList, actualList);
    }

    @Test
    public void testValidateFileList() throws IOException {
        TypeElement element1 = mock(TypeElement.class);
        TypeElement element2 = mock(TypeElement.class);
        when(elements.getTypeElement("test.Element1")).thenReturn(element1);
        when(elements.getTypeElement("test.Element2")).thenReturn(null);

        Gson gson = new Gson();
        List<String> fileList = List.of("test.Element1", "test.Element2");
        String json = gson.toJson(fileList);
        Files.writeString(Paths.get("fileList.json"), json);

        writer.validateFileList();

        assertEquals(0, writer.allFoundProperties.size());
        assertEquals(0, writer.roundFoundProperties.size());
        assertEquals(false, writer.classesWritten);
    }

    @Test
    public void testReadFileList() throws IOException {
        TypeElement element1 = mock(TypeElement.class);
        when(elements.getTypeElement("test.Element1")).thenReturn(element1);

        Gson gson = new Gson();
        List<String> fileList = List.of("test.Element1");
        String json = gson.toJson(fileList);
        Files.writeString(Paths.get("fileList.json"), json);

        writer.readFileList();

        assertEquals(1, writer.allFoundProperties.size());
        assertEquals(element1, writer.allFoundProperties.keySet().iterator().next());
    }
}
