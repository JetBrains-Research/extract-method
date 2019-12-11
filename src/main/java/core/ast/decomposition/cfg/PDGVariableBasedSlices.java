package core.ast.decomposition.cfg;

import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PDGVariableBasedSlices {
    private AbstractVariable baseVariable;
    private List<ASTSlice> slices;

    public PDGVariableBasedSlices(PDG pdg, AbstractVariable variable, PsiElement first, PsiElement last) {
        baseVariable = variable;
        PsiElement parent = first.getParent();

        List<PDGNode> topNodes = new ArrayList<>();
        for (GraphNode node : pdg.nodes) {
            PDGNode pdgNode = (PDGNode) node;
            PsiElement element = pdgNode.getStatement().getStatement();
            if (element.getParent() == parent &&
                    element.getTextRange().getStartOffset() >= first.getTextRange().getStartOffset() &&
                    element.getTextRange().getEndOffset() <= last.getTextRange().getEndOffset()) {
                topNodes.add(pdgNode);
            }
        }
        topNodes.sort(Comparator.comparingInt(
                element -> element.getStatement().getStatement().getTextRange().getStartOffset()));

        slices = new ArrayList<>();
        for (PDGNode node : topNodes) {
            if (isLocalVariableDefined(node, variable)) {
                PDGSelection selection = new PDGSelection(pdg, node.getStatement().getStatement(), last);
                PDGSelectionSlice union = new PDGSelectionSlice(selection, variable);
                if (union.isValid()){
                    ASTSlice slice = new ASTSlice(union);
                    slices.add(slice);
                }
            }
        }
    }

    private boolean isLocalVariableDefined(@NotNull PDGNode node, @NotNull AbstractVariable variable) {
        if (node.definesLocalVariable(variable)) {
            return true;
        }
        for (PDGNode pdgNode : node.getControlDependentNodes()) {
            if (isLocalVariableDefined(pdgNode, variable)) {
                return true;
            }
        }
        return false;
    }

    public AbstractVariable getBaseVariable() {
        return baseVariable;
    }

    public List<ASTSlice> getSlices() {
        return slices;
    }

    public String toString() {
        return baseVariable.getName();
    }
}
