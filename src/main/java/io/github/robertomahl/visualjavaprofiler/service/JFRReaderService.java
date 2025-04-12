package io.github.robertomahl.visualjavaprofiler.service;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
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
    private static Project project = null;

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

    public static void read(Project project) {
        if (JFRReaderService.isNotRecordingFileSet())
            throw new IllegalArgumentException("Profiling result path is not set");

        JFRReaderService.project = project;
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
        // Flat profile - JFR Style
        stackTrace.getFrames().stream()
                .map(RecordedFrame::getMethod)
                .filter(JFRReaderService::isInProjectScope)
                .findAny()
                .map(JFRReaderService::getMethodSignature)
                .ifPresent(methodSignature ->
                        profilingResults.put(methodSignature, profilingResults.getOrDefault(methodSignature, 0L) + 1));

        // Inclusive profile
//        stackTrace.getFrames().stream()
//                .map(RecordedFrame::getMethod)
//                .filter(JFRReaderService::isInProjectScope)
//                .map(JFRReaderService::getMethodSignature)
//                .forEach(methodSignature ->
//                        profilingResults.put(methodSignature, profilingResults.getOrDefault(methodSignature, 0L) + 1));
    }

    private static boolean isInProjectScope(RecordedMethod method) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = ProjectScope.getProjectScope(project);

        return facade.findClass(method.getType().getName(), scope) != null;
    }

    private static String getMethodSignature(RecordedMethod method) {
        return method.getType().getName() + "." + method.getName();
    }
}
