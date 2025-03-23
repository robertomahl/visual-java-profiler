package io.github.robertomahl.visualjavaprofiler.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

public class JFRReaderService {

    private static ProfilingMetric profilingMetric = ProfilingMetric.METHOD_EXECUTION_TIME;
    private static Path profilingResultPath = null;
    private static Map<String, Long> profilingResults = null;

    private static final Consumer<RecordedEvent> methodRunCountAction = JFRReaderService::computeMethodRunCount;
    private static final Consumer<RecordedEvent> methodExecutionTimeAction = JFRReaderService::computeMethodExecutionTime;
    //TODO: add avg execution time

    private static final String EXECUTION_SAMPLE_EVENT = "jdk.ExecutionSample";

    public enum ProfilingMetric {
        METHOD_RUN_COUNT(methodRunCountAction),
        METHOD_EXECUTION_TIME(methodExecutionTimeAction);

        ProfilingMetric(Consumer<RecordedEvent> action) {
            this.action = action;
        }

        private final Consumer<RecordedEvent> action;

        public Consumer<RecordedEvent> getAction() {
            return action;
        }
    }

    public static void read() {
        if (!JFRReaderService.isProfilingResultPathSet()) {
            throw new IllegalArgumentException("Profiling result path is not set");
        }
        read(profilingResultPath);
    }

    public static synchronized boolean isProfilingResultPathSet() {
        return profilingResultPath != null;
    }

    public static synchronized Map<String, Long> getProfilingResults() {
        return profilingResults;
    }

    public static synchronized void setProfilingMetric(ProfilingMetric profilingMetric) {
        JFRReaderService.profilingMetric = profilingMetric;
    }

    public static synchronized void setProfilingResultPath(Path profilingResultPath) {
        JFRReaderService.profilingResultPath = profilingResultPath;
    }

    private static void read(Path path) {
        profilingResults = new ConcurrentHashMap<>();

        try (RecordingFile recording = new RecordingFile(path)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                if (EXECUTION_SAMPLE_EVENT.equals(event.getEventType().getName())) {
                    profilingMetric.getAction().accept(event);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
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

    private static void computeMethodExecutionTime(RecordedEvent event) {
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null) {
            return;
        }
        if (!event.hasField("duration")) {
            throw new IllegalArgumentException("Event does not have a duration field");
        }

        stackTrace.getFrames().stream()
                .map(RecordedFrame::getMethod)
                .map(JFRReaderService::getMethodSignature)
                .forEach(methodSignature ->
                        profilingResults.put(methodSignature, profilingResults.getOrDefault(methodSignature, 0L) + event.getLong("duration")));
    }

    //TODO: Add method params to include overloads distinction
    private static String getMethodSignature(RecordedMethod method) {
        return method.getType().getName() + "." + method.getName();
    }
}
