package io.github.robertomahl.visualjavaprofiler.service;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

public class MethodRunCountProcessingMethod implements ProfilingMetricProcessingMethod {

    private static final String EXECUTION_SAMPLE_EVENT = "jdk.ExecutionSample";

    public MethodRunCountProcessingMethod() {
    }

    @Override
    public Map<String, Long> compute(Project project, RecordingFile recordingFile) {
        Map<String, Long> profilingResults = new HashMap<>();
        while (recordingFile.hasMoreEvents()) {
            try {
                RecordedEvent event = recordingFile.readEvent();
                if (EXECUTION_SAMPLE_EVENT.equals(event.getEventType().getName())) {
                    computeEvent(project, profilingResults, event);
                }
            } catch (IOException ex) {
                throw new RuntimeException("Error reading JFR file", ex);
            }
        }
        return profilingResults;
    }

    private void computeEvent(Project project, Map<String, Long> profilingResults, RecordedEvent event) {
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null) {
            return;
        }
        flatProfile(project, profilingResults, stackTrace);
        //inclusiveProfile(project, profilingResults, stackTrace);
    }

    private void inclusiveProfile(Project project, Map<String, Long> profilingResults, RecordedStackTrace stackTrace) {
        stackTrace.getFrames().stream()
                .map(RecordedFrame::getMethod)
                .filter(method -> isInProjectScope(project, method))
                .map(this::getMethodSignature)
                .forEach(methodSignature ->
                        profilingResults.put(methodSignature, profilingResults.getOrDefault(methodSignature, 0L) + 1));
    }

    private void flatProfile(Project project, Map<String, Long> profilingResults, RecordedStackTrace stackTrace) {
        stackTrace.getFrames().stream()
                .map(RecordedFrame::getMethod)
                .filter(method -> isInProjectScope(project, method))
                .findFirst()
                .map(this::getMethodSignature)
                .ifPresent(methodSignature ->
                        profilingResults.put(methodSignature, profilingResults.getOrDefault(methodSignature, 0L) + 1));
    }

    private boolean isInProjectScope(Project project, RecordedMethod method) {
        if (method == null || method.getType() == null || method.getType().getName() == null) {
            return false;
        }
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = ProjectScope.getProjectScope(project);
        return facade.findClass(method.getType().getName(), scope) != null;
    }

    private String getMethodSignature(RecordedMethod method) {
        return method.getType().getName() + "." + method.getName();
    }
}
