package io.github.robertomahl.visualjavaprofiler.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
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

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                try (RecordingFile recordingFile = new RecordingFile(Path.of(file.getPath()))) {
                    JFRProcessingService jfrProcessingService = project.getService(JFRProcessingService.class);

                    //TODO: Perhaps not all the reading has to be wrapped in a read action
                    ApplicationManager.getApplication().runReadAction(() -> {
                        jfrProcessingService.read(recordingFile);
                    });

                    ApplicationManager.getApplication().invokeLater(() -> {
                        ToggleVisualizationAction toggleVisualizationAction = new ToggleVisualizationAction();
                        toggleVisualizationAction.removeFromAllOpenFiles(project);
                        toggleVisualizationAction.applyToAllOpenFiles(project);
                    });
                }
            } catch (IOException ex) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    Messages.showErrorDialog(project, "Invalid file. Please select a valid JFR file.", "Error");
                });
            }
        });
    }

}
