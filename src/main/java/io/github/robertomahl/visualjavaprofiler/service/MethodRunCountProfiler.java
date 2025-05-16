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

public class MethodRunCountProfiler {

    private static final String EXECUTION_SAMPLE_EVENT = "jdk.ExecutionSample";

    private final Project project;

    public MethodRunCountProfiler(Project project) {
        this.project = project;
    }

    public Map<String, Long> compute(RecordingFile recordingFile) {
        Map<String, Long> profilingResults = new HashMap<>();
        while (recordingFile.hasMoreEvents()) {
            try {
                RecordedEvent event = recordingFile.readEvent();
                if (EXECUTION_SAMPLE_EVENT.equals(event.getEventType().getName())) {
                    computeEvent(profilingResults, event);
                }
            } catch (IOException ex) {
                throw new RuntimeException("Error reading JFR file", ex);
            }
        }
        return profilingResults;
    }

    private void computeEvent(Map<String, Long> profilingResults, RecordedEvent event) {
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null) {
            return;
        }
        flatProfile(profilingResults, stackTrace);
        //inclusiveProfile(profilingResults, stackTrace);
    }

    private void inclusiveProfile(Map<String, Long> profilingResults, RecordedStackTrace stackTrace) {
        stackTrace.getFrames().stream()
                .map(RecordedFrame::getMethod)
                .filter(this::isInProjectScope)
                .map(this::getMethodSignature)
                .forEach(methodSignature ->
                        profilingResults.put(methodSignature, profilingResults.getOrDefault(methodSignature, 0L) + 1));
    }

    private void flatProfile(Map<String, Long> profilingResults, RecordedStackTrace stackTrace) {
        stackTrace.getFrames().stream()
                .map(RecordedFrame::getMethod)
                .filter(this::isInProjectScope)
                .findFirst()
                .map(this::getMethodSignature)
                .ifPresent(methodSignature ->
                        profilingResults.put(methodSignature, profilingResults.getOrDefault(methodSignature, 0L) + 1));
    }

    private boolean isInProjectScope(RecordedMethod method) {
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
