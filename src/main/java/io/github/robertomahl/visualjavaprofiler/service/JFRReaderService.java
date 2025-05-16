package io.github.robertomahl.visualjavaprofiler.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

@Service(Service.Level.PROJECT)
public final class JFRReaderService {

    private static final String EXECUTION_SAMPLE_EVENT = "jdk.ExecutionSample";

    private final Project project;

    private ProfilingMetric profilingMetric = ProfilingMetric.METHOD_RUN_COUNT;
    private RecordingFile recordingFile = null;
    private Map<String, Long> profilingResults = null;

    JFRReaderService(Project project) {
        this.project = project;
    }

    public enum ProfilingMetric {
        METHOD_RUN_COUNT(JFRReaderService::computeMethodRunCount);

        ProfilingMetric(BiConsumer<JFRReaderService, RecordedEvent> action) {
            this.action = action;
        }

        private final BiConsumer<JFRReaderService, RecordedEvent> action;

        public void apply(JFRReaderService service, RecordedEvent event) {
            action.accept(service, event);
        }
    }

    public boolean isNotRecordingFileSet() {
        return recordingFile == null;
    }

    public Map<String, Long> getProfilingResults() {
        return profilingResults;
    }

    public void setProfilingMetric(ProfilingMetric profilingMetric) {
        this.profilingMetric = profilingMetric;
        read();
    }

    public void setRecordingFile(RecordingFile recordingFile) {
        this.recordingFile = recordingFile;
        read();
    }

    private void read() {
        if (isNotRecordingFileSet())
            throw new IllegalArgumentException("Profiling result path is not set");

        profilingResults = new ConcurrentHashMap<>();

        while (recordingFile.hasMoreEvents()) {
            try {
                RecordedEvent event = recordingFile.readEvent();
                if (EXECUTION_SAMPLE_EVENT.equals(event.getEventType().getName())) {
                    profilingMetric.apply(this, event);
                }
            } catch (IOException ex) {
                throw new RuntimeException("Error reading JFR file", ex);
            }
        }
    }

    public void computeMethodRunCount(RecordedEvent event) {
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null) {
            return;
        }
        // Flat profile - JFR Style
        stackTrace.getFrames().stream()
                .map(RecordedFrame::getMethod)
                .filter(this::isInProjectScope)
                .findFirst()
                .map(this::getMethodSignature)
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

    private boolean isInProjectScope(RecordedMethod method) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = ProjectScope.getProjectScope(project);

        return facade.findClass(method.getType().getName(), scope) != null;
    }

    private String getMethodSignature(RecordedMethod method) {
        return method.getType().getName() + "." + method.getName();
    }
}
