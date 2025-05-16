package io.github.robertomahl.visualjavaprofiler.service;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import java.util.Map;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;

public class MethodRunCountProfiler {

    private final Project project;

    public MethodRunCountProfiler(Project project) {
        this.project = project;
    }

    public void compute(RecordedEvent event, Map<String, Long> profilingResults) {
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
