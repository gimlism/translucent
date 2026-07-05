package com.gimlism.translucent.substrate;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.viz.AsciiListRenderer;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.viz.AsciiMapRenderer;
import com.gimlism.translucent.hashmap.viz.Palette;
import com.gimlism.translucent.substrate.events.RecordingListener;
import com.gimlism.translucent.substrate.events.StructureEvent;
import com.gimlism.translucent.substrate.viz.EventRenderer;
import com.gimlism.translucent.substrate.viz.Replayer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubstrateReuseTest {
    // ONE generic helper renders a recorded stream from ANY structure.
    private static <E extends StructureEvent> String renderAll(List<E> events, EventRenderer<E> renderer) {
        var buffer = new ByteArrayOutputStream();
        new Replayer<>(events, renderer).autoPlay(new PrintStream(buffer, true, StandardCharsets.UTF_8), 0);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void oneGenericPathDrivesBothMapAndList() {
        var map = new TeachingHashMap<Integer, String>();
        var mapRec = new RecordingListener<MapEvent>();
        map.addListener(mapRec);
        map.put(1, "a");

        var list = new TeachingArrayList<String>(4);
        var listRec = new RecordingListener<ListEvent>();
        list.addListener(listRec);
        list.add("z");

        // same renderAll<E>, two different structures
        assertTrue(renderAll(mapRec.events(), new AsciiMapRenderer(new Palette(Palette.Mode.PLAIN)))
            .contains("PUT 1=a"));
        assertTrue(renderAll(listRec.events(), new AsciiListRenderer())
            .contains("APPEND z @ 0"));
    }
}
