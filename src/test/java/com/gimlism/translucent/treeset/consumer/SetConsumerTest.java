package com.gimlism.translucent.treeset.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.events.Add;
import com.gimlism.translucent.treeset.events.SetEvent;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SetConsumerTest {

    @Test
    void recordingListenerBuffersEventsImmutably() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        SetRecordingListener rec = new SetRecordingListener();
        set.addListener(rec);
        set.add(1);
        set.add(2);
        assertTrue(rec.events().stream().anyMatch(e -> e instanceof Add));
        assertThrows(UnsupportedOperationException.class, () -> rec.events().add((SetEvent) null));
    }

    @Test
    void consoleLoggerWritesFormattedLines() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        set.addListener(new ConsoleSetEventLogger(new PrintStream(buf, true, StandardCharsets.UTF_8)));
        set.add(7);
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("add 7"), "logged: " + out);
    }
}
