package ui;

import com.intellij.util.ui.tree.AbstractTreeModel;
import core.ast.decomposition.cfg.ASTSlice;
import core.ast.decomposition.cfg.PDGVariableBasedSlices;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.util.List;

final class ExtractMethodModel extends AbstractTreeModel {
    private final List<PDGVariableBasedSlices> slices;

    ExtractMethodModel(@NotNull List<PDGVariableBasedSlices> slices) {
        this.slices = slices;
    }

    @Override
    public Object getRoot() { return slices; }

    @Override
    public Object getChild(Object parent, int index) {
        if (parent instanceof List) {
            List list = (List) parent;
            return list.get(index);
        } else if (parent instanceof PDGVariableBasedSlices) {
            PDGVariableBasedSlices slices = (PDGVariableBasedSlices) parent;
            return slices.getSlices().get(index);
        } return null;
    }

    @Override
    public int getChildCount(Object parent) {
        if (parent instanceof List) {
            return ((List) parent).size();
        } else if (parent instanceof PDGVariableBasedSlices) {
            return ((PDGVariableBasedSlices) parent).getSlices().size();
        }
            return 0;
    }

    @Override
    public boolean isLeaf(Object node) {
        return node instanceof ASTSlice;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {}

    @Override
    public int getIndexOfChild(Object parent, Object child) { return 0; }
}
