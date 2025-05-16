package io.github.robertomahl.visualjavaprofiler.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

@Service(Service.Level.PROJECT)
public final class JFRProcessingService {

    private static final String EXECUTION_SAMPLE_EVENT = "jdk.ExecutionSample";

    private final MethodRunCountProfiler methodRunCountProfiler;

    private ProfilingMetric profilingMetric = ProfilingMetric.METHOD_RUN_COUNT;
    private Map<String, Long> profilingResults = null;

    JFRProcessingService(Project project) {
        this.methodRunCountProfiler = new MethodRunCountProfiler(project);
    }

    public enum ProfilingMetric {
        METHOD_RUN_COUNT((service, event) -> service.methodRunCountProfiler.compute(event, service.profilingResults));

        ProfilingMetric(BiConsumer<JFRProcessingService, RecordedEvent> action) {
            this.action = action;
        }

        private final BiConsumer<JFRProcessingService, RecordedEvent> action;

        public void apply(JFRProcessingService service, RecordedEvent event) {
            action.accept(service, event);
        }
    }

    public boolean isProfilingResultsNotProcessed() {
        return profilingResults == null;
    }

    public Map<String, Long> getProfilingResults() {
        return profilingResults;
    }

    public void setActiveProfilingMetric(ProfilingMetric profilingMetric) {
        this.profilingMetric = profilingMetric;
    }

    public void read(RecordingFile recordingFile) {
        if (recordingFile == null) {
            throw new IllegalArgumentException("RecordingFile cannot be null.");
        }

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
}
