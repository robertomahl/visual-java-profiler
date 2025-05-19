package io.github.robertomahl.visualjavaprofiler.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.robertomahl.visualjavaprofiler.service.JFRProcessingService;
import java.io.IOException;
import java.nio.file.Path;
import jdk.jfr.consumer.RecordingFile;
import org.jetbrains.annotations.NotNull;

public class SelectProfilingResultAction extends AnAction {

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        e.getPresentation().setEnabled(project != null && editor != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false)
                .withTitle("Select JFR File")
                .withDescription("Choose a Java Flight Recorder (JFR) file.");

        VirtualFile file = FileChooser.chooseFile(descriptor, project, null);
        if (file == null)
            return;

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing JFR File", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    try (RecordingFile recordingFile = new RecordingFile(Path.of(file.getPath()))) {
                        JFRProcessingService jfrProcessingService = project.getService(JFRProcessingService.class);

                        ApplicationManager.getApplication().runReadAction(() -> {
                            jfrProcessingService.read(recordingFile);
                        });

                        ApplicationManager.getApplication().invokeLater(() -> {
                            ToggleVisualizationAction toggleVisualizationAction = new ToggleVisualizationAction();
                            toggleVisualizationAction.stop(project);
                            toggleVisualizationAction.start(project);
                        });
                    }
                } catch (IOException ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showErrorDialog(project, "Invalid file. Please select a valid JFR file.", "Error");
                    });
                }
            }
        });
    }

}
