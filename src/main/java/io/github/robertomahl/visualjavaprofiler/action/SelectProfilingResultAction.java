package io.github.robertomahl.visualjavaprofiler.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.robertomahl.visualjavaprofiler.service.JFRReaderService;
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
        // Using background threads (BGT)
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false)
                .withTitle("Select JFR File")
                .withDescription("Choose a java flight recorder (JFR) file.");

        VirtualFile file = FileChooser.chooseFile(descriptor, project, null);
        if (file != null) {
            try (RecordingFile recordingFile = new RecordingFile(Path.of(file.getPath()))) {

                final var jfrReaderService = project.getService(JFRReaderService.class);
                jfrReaderService.setRecordingFile(recordingFile);

                final var toggleVisualizationAction = new ToggleVisualizationAction();
                toggleVisualizationAction.removeFromAllOpenFiles(project);
                toggleVisualizationAction.applyToAllOpenFiles(project);
            } catch (IOException ex) {
                Messages.showErrorDialog(project, "Invalid file. Please select a valid JFR file.", "Error");
            }
        }
    }

}
