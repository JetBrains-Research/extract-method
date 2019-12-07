package ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import core.ast.PartialMethodExtractor;
import core.ast.decomposition.cfg.ASTSlice;
import core.ast.decomposition.cfg.PDGVariableBasedSlices;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PartialExtractMethodAction extends AnAction {
    public PartialExtractMethodAction() { super("Partially extract"); }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent event) {
        final Project project = event.getProject();
        final Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null || !editor.getSelectionModel().hasSelection()) {
            Messages.showMessageDialog(project,"No Text Selected!", "Warning", Messages.getWarningIcon());
            return;
        }
        final PsiFile psiFile = event.getData(LangDataKeys.PSI_FILE);
        if (psiFile == null) {
            Messages.showMessageDialog(project,"Can't get PSI view", "Warning", Messages.getWarningIcon());
            return;
        }


        PsiElement firstStatement = psiFile.findElementAt(editor.getSelectionModel().getSelectionStart());
        PsiElement lastStatement = psiFile.findElementAt(editor.getSelectionModel().getSelectionEnd() - 1);

        if (firstStatement == null || lastStatement == null) {
            Messages.showInfoMessage("PSI elements missed for selection!", "PSI Elements Missed");
            return;
        }
        final PsiElement codeBlock = PsiTreeUtil.findFirstParent(
                PsiTreeUtil.findCommonContext(firstStatement, lastStatement),
                p -> { return p instanceof PsiCodeBlock; });

        PsiElement method = PsiTreeUtil.findFirstParent(codeBlock, p -> { return p instanceof PsiMethod;});
        if (method == null) {
            Messages.showInfoMessage("Selected text is not located in one method", "Incorrect Selection");
            return;
        }

        firstStatement = PsiTreeUtil.findFirstParent(firstStatement, p -> { return p.getParent() == codeBlock; });
        lastStatement = PsiTreeUtil.findFirstParent(lastStatement, p -> { return p.getParent() == codeBlock; });

        List<PDGVariableBasedSlices> opportunities = PartialMethodExtractor.getOpportunities((PsiMethod) method, firstStatement, lastStatement);
        ShowPreview(project, editor, opportunities);
    }

    private void ShowPreview(@NotNull Project project, @NotNull Editor editor,
                             List<PDGVariableBasedSlices> opportunities) {
        if (opportunities == null) {
            Messages.showErrorDialog("Couldn't resolve methods owner",
                    "Error While Getting Refactoring Opportunities");
            return;
        }
        if (opportunities.isEmpty()) {
            Messages.showInfoMessage("No refactoring opportunities found", "Opportunities Not Found");
            return;
        }

        final String windowId = "PartialMethodExtraction.Preview";

        final ExtractMethodPreviewWindow previewWindow = new ExtractMethodPreviewWindow(project, editor, opportunities);
        final ToolWindowManager manager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = manager.getToolWindow(windowId);
        if (toolWindow == null) {
            toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(
                    windowId,true, ToolWindowAnchor.BOTTOM);
            toolWindow.setTitle("Refactoring Opportunities");
        }
        final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        final Content content = contentFactory.createContent(previewWindow, "Refactoring Opportunities", false);
        toolWindow.getContentManager().removeAllContents(true);
        toolWindow.getContentManager().addContent(content);
        toolWindow.show(null);
    }
}
