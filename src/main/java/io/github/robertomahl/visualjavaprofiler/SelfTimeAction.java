package io.github.robertomahl.visualjavaprofiler;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.messages.MessageBusConnection;
import java.awt.Color;
import java.awt.Font;
import org.jetbrains.annotations.NotNull;

public class SelfTimeAction extends AnAction {

    //TODO: consider finding approach that does no require the action to be run first: "Use Startup Activity if it should always run when the project opens"?
    //TODO: once the action is performed, also execute for already opened files
    //TODO: should I stop the highlighting if the method's been changed since the profiling execution?

    //TODO: add start/stop logic for actions

    private static final String TARGET_METHOD_SIGNATURE = "com.minguard.service.impl.StatusServiceImpl.findAll()";

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        //TODO: Drop this requirement
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        e.getPresentation().setEnabled(project != null && editor != null && psiFile != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // Using background threads (BGT)
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        Project project = anActionEvent.getProject();
        if (project != null) {
            registerFileOpenListener(project);
        }

    }

    public void registerFileOpenListener(@NotNull Project project) {
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                //Moving operation to BGT, since Psi reading is costly and shouldn't be done in EDT
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    PsiManager psiManager = PsiManager.getInstance(project);

                    PsiFile psiFile = ReadAction.compute(() -> psiManager.findFile(file));
                    if (psiFile instanceof PsiJavaFile
                            && psiFile.isValid()) {
                        ReadAction.run(() -> highlightTargetMethod((PsiJavaFile) psiFile, project));
                    }
                });
            }
        });
    }

    private void highlightTargetMethod(PsiJavaFile psiFile, Project project) {
        for (PsiMethod method : PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod.class)) {
            String methodSignature = getMethodSignature(method);
            if (TARGET_METHOD_SIGNATURE.equals(methodSignature)) {
                highlightMethod(project, method);
            }
        }
    }

    private String getMethodSignature(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return null;
        }

        String className = containingClass.getQualifiedName();
        if (className == null) {
            return null;
        }

        StringBuilder signature = new StringBuilder(className).append(".").append(method.getName()).append("(");
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                signature.append(", ");
            }
            signature.append(parameters[i].getType().getCanonicalText());
        }
        signature.append(")");

        return signature.toString();
    }

    private void highlightMethod(Project project, PsiMethod method) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) return;

        //TODO: improve highlighting so it is applied to all text within the method
        MarkupModel markupModel = editor.getMarkupModel();
        TextAttributes attributes = new TextAttributes(Color.RED, null, null, EffectType.LINE_UNDERSCORE, Font.BOLD);

        int startOffset = method.getTextRange().getStartOffset();
        int endOffset = method.getTextRange().getEndOffset();
        markupModel.addRangeHighlighter(startOffset, endOffset, HighlighterLayer.ADDITIONAL_SYNTAX, attributes, HighlighterTargetArea.EXACT_RANGE);
    }

}
