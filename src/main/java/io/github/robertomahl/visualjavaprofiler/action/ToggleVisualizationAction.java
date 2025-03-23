package io.github.robertomahl.visualjavaprofiler.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.messages.MessageBusConnection;
import io.github.robertomahl.visualjavaprofiler.service.JFRReaderService;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public class ToggleVisualizationAction extends AnAction {

    //Profiler Lens

    //Essentials
    //TODO: integrate with profiling output
    //TODO: implement scale of colors to differ execution time
    //TODO: add start/stop logic for the highlighting

    //Improvements
    //TODO: stop the highlighting if the method's been changed since the profiling execution
    //TODO: also include the actual execution time in a label, besides color highlighting

    //Extras
    //TODO: add option to only highlight x% most time-consuming methods - configurable
    //TODO: highlighting most time-consuming files in the project files view as well

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
    public void actionPerformed(AnActionEvent anActionEvent) {
        final var project = Optional.ofNullable(anActionEvent.getProject()).orElseThrow();

        if (JFRReaderService.isNotRecordingFileSet())
            new SelectProfilingResultAction().actionPerformed(anActionEvent);

        registerFileOpenListener(project);
        applyToAllOpenFiles(project);
    }

    private void registerFileOpenListener(Project project) {
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                //Moving operation to BGT, since Psi reading is costly and shouldn't be done in EDT
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    applyToFile(project, file);
                });
            }
        });
    }

    private void applyToAllOpenFiles(Project project) {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);

        VirtualFile[] openFiles = fileEditorManager.getOpenFiles();
        for (VirtualFile virtualFile : openFiles) {
            applyToFile(project, virtualFile);
        }
    }

    private void applyToFile(Project project, VirtualFile virtualFile) {
        PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(virtualFile));
        if (psiFile instanceof PsiJavaFile
                && psiFile.isValid()) {

            // FileEditor instances can be non-textual, so we need to get only the textual ones (Editor)
            List<Editor> editors = FileEditorManager.getInstance(project).getEditorList(virtualFile)
                    .stream()
                    .filter(TextEditor.class::isInstance)
                    .map(TextEditor.class::cast)
                    .map(TextEditor::getEditor)
                    .toList();

            if (!editors.isEmpty())
                ReadAction.run(() -> highlightTargetMethod((PsiJavaFile) psiFile, project, editors));
        }

    }

    private void highlightTargetMethod(PsiJavaFile psiFile, Project project, List<Editor> editors) {
        if (JFRReaderService.getProfilingResults() == null)
            throw new IllegalStateException("Profiling results are not set");

        for (PsiMethod method : PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod.class)) {
            String methodSignature = getMethodSignature(method);
            Long methodResult = JFRReaderService.getProfilingResults().get(methodSignature);
            if (methodResult != null) {
                editors.forEach(editor -> highlightMethod(project, method, methodResult, editor));
            }
        }
    }

    private String getMethodSignature(PsiMethod method) {
        final var className = Optional.ofNullable(method.getContainingClass())
                .map(PsiClass::getQualifiedName)
                .orElseThrow();

        StringBuilder signature = new StringBuilder(className).append(".").append(method.getName())
                //.append("(")
                ;

        /*
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                signature.append(", ");
            }
            signature.append(parameters[i].getType().getCanonicalText());
        }
        signature.append(")");
         */

        return signature.toString();
    }

    private void highlightMethod(Project project, PsiMethod method, Long methodResult, Editor editor) {
        //TODO: add scale of colors
        //TODO: change to JBColor, with dark respective color
        TextAttributes attributes = new TextAttributes(null, new Color(255, 165, 0, 100), null, null, Font.PLAIN); // Semi-transparent orange background

        int startOffset = method.getTextRange().getStartOffset();
        int endOffset = method.getTextRange().getEndOffset();

        editor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, HighlighterLayer.LAST, attributes, HighlighterTargetArea.EXACT_RANGE);
    }

}
