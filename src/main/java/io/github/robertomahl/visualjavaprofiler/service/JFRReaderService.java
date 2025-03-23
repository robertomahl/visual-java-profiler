package io.github.robertomahl.visualjavaprofiler.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

public class JFRReaderService {

    private static ProfilingMetric profilingMetric = ProfilingMetric.METHOD_RUN_COUNT;
    private static RecordingFile recordingFile = null;
    private static Map<String, Long> profilingResults = null;

    private static final String EXECUTION_SAMPLE_EVENT = "jdk.ExecutionSample";

    public enum ProfilingMetric {
        METHOD_RUN_COUNT(JFRReaderService::computeMethodRunCount),
        ;

        ProfilingMetric(Consumer<RecordedEvent> action) {
            this.action = action;
        }

        private final Consumer<RecordedEvent> action;

        public Consumer<RecordedEvent> getAction() {
            return action;
        }
    }

    public static synchronized boolean isNotRecordingFileSet() {
        return recordingFile == null;
    }

    public static synchronized Map<String, Long> getProfilingResults() {
        return profilingResults;
    }

    public static synchronized void setProfilingMetric(ProfilingMetric profilingMetric) {
        JFRReaderService.profilingMetric = profilingMetric;
    }

    public static synchronized void setRecordingFile(RecordingFile recordingFile) {
        JFRReaderService.recordingFile = recordingFile;
    }

    public static void read() {
        if (JFRReaderService.isNotRecordingFileSet())
            throw new IllegalArgumentException("Profiling result path is not set");

        profilingResults = new ConcurrentHashMap<>();

        while (recordingFile.hasMoreEvents()) {
            try {
                RecordedEvent event = recordingFile.readEvent();
                if (EXECUTION_SAMPLE_EVENT.equals(event.getEventType().getName())) {
                    profilingMetric.getAction().accept(event);
                }
            } catch (IOException ex) {
                throw new RuntimeException("Error reading JFR file", ex);
            }
        }
    }

    private static void computeMethodRunCount(RecordedEvent event) {
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null) {
            return;
        }
        stackTrace.getFrames().stream()
                .map(RecordedFrame::getMethod)
                .map(JFRReaderService::getMethodSignature)
                .forEach(methodSignature ->
                        profilingResults.put(methodSignature, profilingResults.getOrDefault(methodSignature, 0L) + 1));
    }

    private static String getMethodSignature(RecordedMethod method) {
        return method.getType().getName() + "." + method.getName();
    }
}
