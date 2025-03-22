package io.github.robertomahl.visualjavaprofiler.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

public class JFRReaderService {

    private static final String EXECUTION_SAMPLE_EVENT = "jdk.ExecutionSample";

    public Map<String, Long> readMethodMetrics(Path path) {
        Map<String, Long> methodMetrics = new HashMap<>();

        try (RecordingFile recording = new RecordingFile(path)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                if (EXECUTION_SAMPLE_EVENT.equals(event.getEventType().getName())) {
                    RecordedStackTrace stackTrace = event.getStackTrace();
                    if (stackTrace != null) {
                        stackTrace.getFrames().stream()
                                .map(frame -> frame.getMethod().getType().getName() + "." + frame.getMethod().getName())
                                .forEach(method -> methodMetrics.put(method, methodMetrics.getOrDefault(method, 0L) + 1));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return methodMetrics;
    }

}
