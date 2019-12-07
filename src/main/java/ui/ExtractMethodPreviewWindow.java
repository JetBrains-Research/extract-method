package ui;

import com.intellij.execution.Output;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.ui.treeStructure.Tree;
import core.ast.decomposition.cfg.ASTSlice;
import core.ast.decomposition.cfg.PDGNode;
import core.ast.decomposition.cfg.PDGVariableBasedSlices;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
import java.util.Set;

import refactoring.PartialExtractMethodProcessor;

final class ExtractMethodPreviewWindow extends JPanel {
    private final Project project;
    private final Editor editor;
    private Tree tree;

    ExtractMethodPreviewWindow(@NotNull Project project, @NotNull Editor editor,
                               @NotNull List<PDGVariableBasedSlices> slices) {
        this.project = project;
        this.editor = editor;
        setLayout(new BorderLayout());
        add(buildOpportunitiesPanel(slices), BorderLayout.WEST);
        // add(buildPreviewPanel(slices.get(0)), BorderLayout.CENTER);
    }

    private JPanel buildOpportunitiesPanel(@NotNull List<PDGVariableBasedSlices> slices) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        tree = new Tree();
        tree.setModel(new ExtractMethodModel(slices));
        tree.addMouseListener((DoubleClickListener) this::optionSelected);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        panel.add(tree, BorderLayout.CENTER);
        return panel;
    }

    private void optionSelected() {
        if (tree.getSelectionPath().getPath().length == 3) {
            Object choice = tree.getSelectionPath().getPathComponent(2);
            if (choice instanceof ASTSlice) {
                extract((ASTSlice) choice);
            }
        }
    }

    private void OutputStatements(Set<PsiStatement> nodes, String name)
    {
        StringBuilder str = new StringBuilder();
        for (PsiStatement statement : nodes) {
            str.append(statement.getText()).append("\n");
        }
        Messages.showInfoMessage(str.toString(), name);
    }

    private void extract(ASTSlice slice) {
        OutputStatements(slice.getSliceStatements(),"Slice");
        OutputStatements(slice.getDuplicatedStatements(), "Duplicated");
        OutputStatements(slice.getRemovableStatements(), "Removable");
        PartialExtractMethodProcessor processor = new PartialExtractMethodProcessor(project, editor, slice);

        try {
            processor.setShowErrorDialogs(false);
            processor.prepare();
        } catch (PrepareFailedException exception) {
            exception.printStackTrace();
        }

        ExtractMethodHandler.invokeOnElements(project, processor,
                slice.getSourceMethodDeclaration().getContainingFile(), true);
    }

    @FunctionalInterface
    private interface DoubleClickListener extends MouseListener {
        default void mouseClicked(MouseEvent e) {
            if (e.getClickCount() >= 2) {
                onDoubleClick();
            }
        }

        void onDoubleClick();

        default void mousePressed(MouseEvent e) {}
        default void mouseReleased(MouseEvent e) {}
        default void mouseEntered(MouseEvent e) {}
        default void mouseExited(MouseEvent e) {}
    }
}
