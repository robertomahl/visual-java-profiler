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
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.messages.MessageBusConnection;
import io.github.robertomahl.visualjavaprofiler.service.JFRProcessingService;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public class ToggleVisualizationAction extends AnAction {

    //Profiler Lens

    //Essentials
    //TODO: implement scale of colors to differ execution time
    //TODO: add option to only highlight x% most time-consuming methods - configurable
    //TODO: publish the plugin to JetBrains Marketplace

    //Fixes
    //TODO: make sure local, anonymous and lambda classes are handled

    //Improvements
    //TODO: add new metrics
    //TODO: indicate whether visualization is active or not

    //Extras
    //TODO: also include the actual execution time in a label, besides color highlighting
    //TODO: highlight most time-consuming files in the project files view as well
    //TODO: see highlights in the scrollbar
    //TODO: collect user interaction data

    private static boolean isVisible = false;

    private static MessageBusConnection connection = null;

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        e.getPresentation().setEnabled(project != null
                && editor != null
                && !project.getService(JFRProcessingService.class).isProfilingResultsNotProcessed());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final var project = Optional.ofNullable(anActionEvent.getProject()).orElseThrow();

        if (isVisible) {
            stop(project);
        } else {
            start(project);
        }
    }

    public void start(Project project) {
        registerFileOpenListener(project);
        applyToAllOpenFiles(project);
        isVisible = true;
    }

    public void stop(Project project) {
        unregisterFileOpenListener();
        removeFromAllOpenFiles(project);
        isVisible = false;
    }

    private void registerFileOpenListener(Project project) {
        if (connection != null)
            return;

        connection = project.getMessageBus().connect();
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                applyToFile(project, file);
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
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(virtualFile));

            if (psiFile instanceof PsiJavaFile && psiFile.isValid()) {
                List<Editor> editors = getEditors(project, virtualFile);

                if (!editors.isEmpty())
                    highlightTargetMethod((PsiJavaFile) psiFile, project, editors);
            }
        });
    }

    private void unregisterFileOpenListener() {
        if (connection == null)
            return;

        connection.disconnect();
        connection = null;
    }

    private void removeFromAllOpenFiles(Project project) {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);

        VirtualFile[] openFiles = fileEditorManager.getOpenFiles();
        for (VirtualFile virtualFile : openFiles) {
            removeFromFile(project, virtualFile);
        }
    }

    private void removeFromFile(Project project, VirtualFile virtualFile) {
        List<Editor> editors = getEditors(project, virtualFile);

        if (!editors.isEmpty())
            editors.forEach(editor -> editor.getMarkupModel().removeAllHighlighters());
    }

    private List<Editor> getEditors(Project project, VirtualFile virtualFile) {
        // FileEditor instances can be non-textual, so we need to get only the textual ones (Editor)
        return FileEditorManager.getInstance(project).getEditorList(virtualFile)
                .stream()
                .filter(TextEditor.class::isInstance)
                .map(TextEditor.class::cast)
                .map(TextEditor::getEditor)
                .toList();
    }

    private void highlightTargetMethod(PsiJavaFile psiFile, Project project, List<Editor> editors) {
        final var jfrProcessingService = project.getService(JFRProcessingService.class);
        if (jfrProcessingService.isProfilingResultsNotProcessed())
            throw new IllegalStateException("Profiling results are not set");

        List<PsiMethod> psiFileMethods = ReadAction.compute(() -> PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod.class)
                .stream()
                // TODO: Filtering out methods that are in classes that are not yet being handled, such as anonymous and local classes
                .filter(psiMethod -> psiMethod.getContainingClass() != null && psiMethod.getContainingClass().getQualifiedName() != null)
                .toList()
        );

        for (PsiMethod method : psiFileMethods) {
            final var methodIdentifier = getMethodIdentifier(method);
            final var methodResult = jfrProcessingService.getProfilingResults().get(methodIdentifier);
            if (methodResult != null) {
                editors.forEach(editor -> highlightMethod(project, method, methodResult, editor));
            }
        }
    }

    private String getMethodIdentifier(PsiMethod method) {
        final var className = ReadAction.compute(() ->
                Optional.ofNullable(method.getContainingClass())
                        .map(PsiClass::getQualifiedName)
                        .orElseThrow()
        );
        final var methodName = ReadAction.compute(() -> method.getName());
        final var methodDescriptor = ReadAction.compute(() -> ClassUtil.getAsmMethodSignature(method));

        return className + "." + methodName + methodDescriptor;
    }

    private void highlightMethod(Project project, PsiMethod method, Long methodResult, Editor editor) {
        //TODO: add scale of colors
        //TODO: change to JBColor, with dark respective color
        TextAttributes attributes = new TextAttributes(null, new Color(255, 165, 0, 100), null, null, Font.PLAIN); // Semi-transparent orange background

        int startOffset = method.getTextRange().getStartOffset();
        int endOffset = method.getTextRange().getEndOffset();

        ApplicationManager.getApplication().invokeLater(() -> {
            editor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, HighlighterLayer.LAST, attributes, HighlighterTargetArea.EXACT_RANGE);
        });
    }

}
