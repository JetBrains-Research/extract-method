package core.ast.decomposition.cfg;

import com.intellij.psi.PsiElement;

import java.util.LinkedHashSet;
import java.util.Set;

public class PDGSelection extends Graph {
    private PDG pdg;

    public PDGSelection(PDG pdg, PsiElement first, PsiElement last) {
        this.pdg = pdg;

        for (GraphNode node: pdg.nodes) {
            PDGNode pdgNode = (PDGNode) node;
            PsiElement element = pdgNode.getStatement().getStatement();
            if (element.getTextRange().getStartOffset() >= first.getTextRange().getStartOffset() &&
                    element.getTextRange().getEndOffset() <= last.getTextRange().getEndOffset()) {
                this.nodes.add(pdgNode);
            }
        }

        for (GraphEdge edge : pdg.edges) {
            PDGDependence dependence = (PDGDependence) edge;
            if (nodes.contains(dependence.src) && nodes.contains(dependence.dst)) {
                if (dependence instanceof PDGDataDependence) {
                    PDGDataDependence dataDependence = (PDGDataDependence) dependence;
                    if (dataDependence.isLoopCarried()) {
                        PDGNode loopNode = dataDependence.getLoop().getPDGNode();
                        if (nodes.contains(loopNode))
                            edges.add(dataDependence);
                    } else
                        edges.add(dataDependence);
                } else if (dependence instanceof PDGAntiDependence) {
                    PDGAntiDependence antiDependence = (PDGAntiDependence) dependence;
                    if (antiDependence.isLoopCarried()) {
                        PDGNode loopNode = antiDependence.getLoop().getPDGNode();
                        if (nodes.contains(loopNode))
                            edges.add(antiDependence);
                    } else
                        edges.add(antiDependence);
                } else if (dependence instanceof PDGOutputDependence) {
                    PDGOutputDependence outputDependence = (PDGOutputDependence) dependence;
                    if (outputDependence.isLoopCarried()) {
                        PDGNode loopNode = outputDependence.getLoop().getPDGNode();
                        if (nodes.contains(loopNode))
                            edges.add(outputDependence);
                    } else
                        edges.add(outputDependence);
                } else
                    edges.add(dependence);
            }
        }
    }

    public boolean isAssigned(AbstractVariable variable) {
        for (GraphNode node : nodes) {
            PDGNode pdgNode = (PDGNode) node;
            if (pdgNode.definesLocalVariable(variable))
                return true;
        }
        return false;
    }

    Set<PDGNode> getAssignmentNodesOfVariableCriterion(AbstractVariable localVariableCriterion) {
        Set<PDGNode> nodeCriteria = new LinkedHashSet<>();
        for (GraphNode node : nodes) {
            PDGNode pdgNode = (PDGNode) node;
            if (pdgNode.definesLocalVariable(localVariableCriterion))
                nodeCriteria.add(pdgNode);
        }
        return nodeCriteria;
    }

    public boolean isPartOf(GraphNode nodeCriterion) {
        return nodes.contains(nodeCriterion);
    }

    Set<PDGNode> computeSlice(PDGNode nodeCriterion, AbstractVariable localVariableCriterion) {
        Set<PDGNode> sliceNodes = new LinkedHashSet<>();
        if (nodeCriterion.definesLocalVariable(localVariableCriterion)) {
            sliceNodes.addAll(traverseBackward(nodeCriterion, new LinkedHashSet<>()));
        } else if (nodeCriterion.usesLocalVariable(localVariableCriterion)) {
            Set<PDGNode> defNodes = getDefNodes(nodeCriterion, localVariableCriterion);
            for (PDGNode defNode : defNodes) {
                sliceNodes.addAll(traverseBackward(defNode, new LinkedHashSet<>()));
            }
            sliceNodes.addAll(traverseBackward(nodeCriterion, new LinkedHashSet<>()));
        }
        return sliceNodes;
    }

    private Set<PDGNode> getDefNodes(PDGNode node, AbstractVariable localVariable) {
        Set<PDGNode> defNodes = new LinkedHashSet<>();
        for (GraphEdge edge : node.incomingEdges) {
            PDGDependence dependence = (PDGDependence) edge;
            if (edges.contains(dependence) && dependence instanceof PDGDataDependence) {
                PDGDataDependence dataDependence = (PDGDataDependence) dependence;
                if (dataDependence.getData().equals(localVariable)) {
                    PDGNode srcPDGNode = (PDGNode) dependence.src;
                    defNodes.add(srcPDGNode);
                }
            }
        }
        return defNodes;
    }

    Set<PDGNode> computeSlice(PDGNode nodeCriterion) {
        return new LinkedHashSet<>(traverseBackward(nodeCriterion, new LinkedHashSet<>()));
    }

    private Set<PDGNode> traverseBackward(PDGNode node, Set<PDGNode> visitedNodes) {
        Set<PDGNode> sliceNodes = new LinkedHashSet<>();
        sliceNodes.add(node);
        visitedNodes.add(node);
        for (GraphEdge edge : node.incomingEdges) {
            PDGDependence dependence = (PDGDependence) edge;
            if (edges.contains(dependence) && !(dependence instanceof PDGAntiDependence)
                    && !(dependence instanceof PDGOutputDependence)) {
                PDGNode srcPDGNode = (PDGNode) dependence.src;
                if (!visitedNodes.contains(srcPDGNode))
                    sliceNodes.addAll(traverseBackward(srcPDGNode, visitedNodes));
            }
        }
        return sliceNodes;
    }

    public PDG getPdg() { return pdg; }
}
