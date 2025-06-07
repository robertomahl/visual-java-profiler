package io.github.robertomahl.visualjavaprofiler.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.ClassUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

public class MethodRunCountProcessingMethod implements ProfilingMetricProcessingMethod {

    private static final String EXECUTION_SAMPLE_EVENT = "jdk.ExecutionSample";

    private final Set<String> classesInProjectScope = ConcurrentHashMap.newKeySet();
    private final Set<String> classesNotInProjectScope = ConcurrentHashMap.newKeySet();

    public MethodRunCountProcessingMethod() {
    }

    @Override
    public ProcessingMethodResult compute(Project project, RecordingFile recordingFile) {
        ConcurrentMap<String, Long> profilingResults = new ConcurrentHashMap<>();

        List<RecordedEvent> events = new ArrayList<>();
        while (recordingFile.hasMoreEvents()) {
            try {
                RecordedEvent event = recordingFile.readEvent();
                if (EXECUTION_SAMPLE_EVENT.equals(event.getEventType().getName())) {
                    events.add(event);
                }
            } catch (IOException ex) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    Messages.showErrorDialog(project, "Error reading JFR file.", "Error");
                });
                return null;
            }
        }
        events.parallelStream().forEach(event -> computeEvent(project, profilingResults, event));

        return new ProcessingMethodResult(profilingResults);
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
                .filter(RecordedFrame::isJavaFrame)
                .map(RecordedFrame::getMethod)
                .filter(this::isNotLambda)
                .filter(method -> isInProjectScope(project, method))
                .map(this::getMethodIdentifier)
                .forEach(methodSignature ->
                        profilingResults.merge(methodSignature, 1L, Long::sum));
    }

    private void flatProfile(Project project, Map<String, Long> profilingResults, RecordedStackTrace stackTrace) {
        stackTrace.getFrames().stream()
                .filter(RecordedFrame::isJavaFrame)
                .map(RecordedFrame::getMethod)
                .filter(this::isNotLambda)
                .filter(method -> isInProjectScope(project, method))
                .findFirst()
                .map(this::getMethodIdentifier)
                .ifPresent(methodSignature ->
                        profilingResults.merge(methodSignature, 1L, Long::sum));
    }

    private boolean isNotLambda(RecordedMethod method) {
        // Lambdas shall be skipped so their parent method is counted instead
        return !method.getName().startsWith("lambda");
    }

    private boolean isInProjectScope(Project project, RecordedMethod method) {
        if (method == null || method.getType() == null || method.getType().getName() == null) {
            return false;
        }

        if (classesInProjectScope.contains(method.getType().getName()))
            return true;
        else if (classesNotInProjectScope.contains(method.getType().getName()))
            return false;

        final var isInProjectScope = ReadAction.nonBlocking(() -> {
                    PsiManager manager = PsiManager.getInstance(project);
                    String name = method.getType().getName().replace('/', '.');
                    GlobalSearchScope scope = ProjectScope.getProjectScope(project);
                    return ClassUtil.findPsiClass(manager, name, null, true, scope) != null;
                })
                .inSmartMode(project)
                .executeSynchronously();

        if (isInProjectScope)
            classesInProjectScope.add(method.getType().getName());
        else
            classesNotInProjectScope.add(method.getType().getName());

        return isInProjectScope;
    }

    private String getMethodIdentifier(RecordedMethod method) {
        return method.getType().getName() + "." + method.getName() + method.getDescriptor();
    }
}
