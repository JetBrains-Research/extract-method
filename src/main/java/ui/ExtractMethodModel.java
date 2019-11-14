package ui;

import com.intellij.util.ui.tree.AbstractTreeModel;
import core.ast.decomposition.cfg.ASTSlice;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.util.List;

final class ExtractMethodModel extends AbstractTreeModel {
    private final List<ASTSlice> slices;

    ExtractMethodModel(@NotNull List<ASTSlice> slices) {
        this.slices = slices;
    }

    @Override
    public Object getRoot() { return slices; }

    @Override
    public Object getChild(Object parent, int index) {
        if (parent instanceof List) {
            List list = (List) parent;
            return list.get(index);
        } else return null;
    }

    @Override
    public int getChildCount(Object parent) {
        if (parent instanceof List) {
            return ((List) parent).size();
        } else
            return 0;
    }

    @Override
    public boolean isLeaf(Object node) { return !(node instanceof List); }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {}

    @Override
    public int getIndexOfChild(Object parent, Object child) { return 0; }
}
