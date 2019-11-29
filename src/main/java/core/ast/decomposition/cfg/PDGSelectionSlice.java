package core.ast.decomposition.cfg;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class PDGSelectionSlice {
    protected PDG pdg;
    private PDGSelection selection;
    private Set<PDGNode> nodeCriteria;
    protected Set<PDGNode> sliceNodes;
    private AbstractVariable baseVariable;
    private Set<AbstractVariable> passedParameters;
    protected Set<PDGNode> indispensableNodes;
    protected Set<PDGNode> removableNodes;

    public PDGSelectionSlice(PDGSelection selection, AbstractVariable baseVariable) {
        pdg = selection.getPdg();
        this.selection = selection;
        sliceNodes = new TreeSet<>();
        nodeCriteria = selection.getAssignmentNodesOfVariableCriterion(baseVariable);
        for (PDGNode nodeCriterion : nodeCriteria) {
            sliceNodes.addAll(selection.computeSlice(nodeCriterion));
        }
        this.baseVariable = baseVariable;
        //add any required object-state slices that may be used from the resulting slice
        Set<PDGNode> nodesToBeAddedToSliceDueToDependenceOnObjectStateSlices = new TreeSet<>();
        Set<PlainVariable> alreadyExaminedObjectReferences = new LinkedHashSet<>();
        for (PDGNode sliceNode : sliceNodes) {
            Set<AbstractVariable> usedVariables = sliceNode.usedVariables;
            for (AbstractVariable usedVariable : usedVariables) {
                if (usedVariable instanceof PlainVariable) {
                    PlainVariable plainVariable = (PlainVariable) usedVariable;
                    if (!alreadyExaminedObjectReferences.contains(plainVariable)
                            && !baseVariable.getInitialVariable().equals(plainVariable)) {
                        Map<CompositeVariable, LinkedHashSet<PDGNode>> definedAttributeNodeCriteriaMap =
                                pdg.getDefinedAttributesOfReference(plainVariable);
                        if (!definedAttributeNodeCriteriaMap.isEmpty()) {
                            TreeSet<PDGNode> objectSlice = new TreeSet<>();
                            for (CompositeVariable compositeVariable : definedAttributeNodeCriteriaMap.keySet()) {
                                Set<PDGNode> nodeCriteria2 = definedAttributeNodeCriteriaMap.get(compositeVariable);
                                for (PDGNode nodeCriterion : nodeCriteria2) {
                                    if (selection.isPartOf(nodeCriterion))
                                        objectSlice.addAll(selection.computeSlice(nodeCriterion));
                                }
                            }
                            nodesToBeAddedToSliceDueToDependenceOnObjectStateSlices.addAll(objectSlice);
                        }
                        alreadyExaminedObjectReferences.add(plainVariable);
                    }
                }
            }
        }
        sliceNodes.addAll(nodesToBeAddedToSliceDueToDependenceOnObjectStateSlices);
        Set<PDGNode> throwStatementNodes = getThrowStatementNodesWithinRegion();
        Set<PDGNode> nodesToBeAddedToSliceDueToThrowStatementNodes = new TreeSet<>();
        for (PDGNode throwNode : throwStatementNodes) {
            for (PDGNode sliceNode : sliceNodes) {
                if (sliceNode instanceof PDGControlPredicateNode && isNestedInside(throwNode, sliceNode)) {
                    Set<PDGNode> throwNodeSlice = selection.computeSlice(throwNode);
                    nodesToBeAddedToSliceDueToThrowStatementNodes.addAll(throwNodeSlice);
                    break;
                }
            }
        }
        sliceNodes.addAll(nodesToBeAddedToSliceDueToThrowStatementNodes);
        Set<PDGNode> remainingNodes = new TreeSet<>();
        remainingNodes.add(pdg.getEntryNode());
        for (GraphNode node : pdg.nodes) {
            PDGNode pdgNode = (PDGNode) node;
            if (!sliceNodes.contains(pdgNode))
                remainingNodes.add(pdgNode);
        }
        Set<PDGNode> throwStatementNodesToBeAddedToDuplicatedNodesDueToRemainingNodes = new TreeSet<>();
        for (PDGNode throwNode : throwStatementNodes) {
            for (PDGNode remainingNode : remainingNodes) {
                if (remainingNode.getId() != 0 && isNestedInside(throwNode, remainingNode)) {
                    throwStatementNodesToBeAddedToDuplicatedNodesDueToRemainingNodes.add(throwNode);
                    break;
                }
            }
        }
        this.passedParameters = new LinkedHashSet<>();
        Set<PDGNode> nCD = new LinkedHashSet<>();
        Set<PDGNode> nDD = new LinkedHashSet<>();
        for (GraphEdge edge : pdg.edges) {
            PDGDependence dependence = (PDGDependence) edge;
            PDGNode srcPDGNode = (PDGNode) dependence.src;
            PDGNode dstPDGNode = (PDGNode) dependence.dst;
            if (dependence instanceof PDGDataDependence) {
                PDGDataDependence dataDependence = (PDGDataDependence) dependence;
                if (remainingNodes.contains(srcPDGNode) && sliceNodes.contains(dstPDGNode))
                    passedParameters.add(dataDependence.getData());
                if (sliceNodes.contains(srcPDGNode) && remainingNodes.contains(dstPDGNode)
                        && !dataDependence.getData().equals(baseVariable)
                        && !dataDependence.getData().isField())
                    nDD.add(srcPDGNode);
            } else if (dependence instanceof PDGControlDependence) {
                if (sliceNodes.contains(srcPDGNode) && remainingNodes.contains(dstPDGNode))
                    nCD.add(srcPDGNode);
            }
        }
        Set<PDGNode> controlIndispensableNodes = new LinkedHashSet<>();
        for (PDGNode p : nCD) {
            for (AbstractVariable usedVariable : p.usedVariables) {
                Set<PDGNode> pSliceNodes = selection.computeSlice(p, usedVariable);
                for (GraphNode node : pdg.nodes) {
                    PDGNode q = (PDGNode) node;
                    if (pSliceNodes.contains(q) || q.equals(p))
                        controlIndispensableNodes.add(q);
                }
            }
            if (p.usedVariables.isEmpty()) {
                Set<PDGNode> pSliceNodes = selection.computeSlice(p);
                for (GraphNode node : pdg.nodes) {
                    PDGNode q = (PDGNode) node;
                    if (pSliceNodes.contains(q) || q.equals(p))
                        controlIndispensableNodes.add(q);
                }
            }
        }
        Set<PDGNode> dataIndispensableNodes = new LinkedHashSet<>();
        for (PDGNode p : nDD) {
            for (AbstractVariable definedVariable : p.definedVariables) {
                Set<PDGNode> pSliceNodes = selection.computeSlice(p, definedVariable);
                for (GraphNode node : pdg.nodes) {
                    PDGNode q = (PDGNode) node;
                    if (pSliceNodes.contains(q))
                        dataIndispensableNodes.add(q);
                }
            }
        }
        this.indispensableNodes = new TreeSet<>();
        indispensableNodes.addAll(controlIndispensableNodes);
        indispensableNodes.addAll(dataIndispensableNodes);
        Set<PDGNode> throwStatementNodesToBeAddedToDuplicatedNodesDueToIndispensableNodes = new TreeSet<>();
        for (PDGNode throwNode : throwStatementNodes) {
            for (PDGNode indispensableNode : indispensableNodes) {
                if (isNestedInside(throwNode, indispensableNode)) {
                    throwStatementNodesToBeAddedToDuplicatedNodesDueToIndispensableNodes.add(throwNode);
                    break;
                }
            }
        }
        for (PDGNode throwNode : throwStatementNodesToBeAddedToDuplicatedNodesDueToRemainingNodes) {
            indispensableNodes.addAll(selection.computeSlice(throwNode));
        }
        for (PDGNode throwNode : throwStatementNodesToBeAddedToDuplicatedNodesDueToIndispensableNodes) {
            indispensableNodes.addAll(selection.computeSlice(throwNode));
        }
        this.removableNodes = new LinkedHashSet<>();
        for (GraphNode node : pdg.nodes) {
            PDGNode pdgNode = (PDGNode) node;
            if (!remainingNodes.contains(pdgNode) && !indispensableNodes.contains(pdgNode))
                removableNodes.add(pdgNode);
        }
    }

    private boolean isNestedInside(PDGNode nestedNode, PDGNode parentNode) {
        for (GraphEdge edge : nestedNode.incomingEdges) {
            PDGDependence dependence = (PDGDependence) edge;
            if (dependence instanceof PDGControlDependence) {
                PDGControlDependence controlDependence = (PDGControlDependence) dependence;
                PDGNode srcPDGNode = (PDGNode) controlDependence.src;
                if (srcPDGNode.equals(parentNode))
                    return true;
                else
                    return isNestedInside(srcPDGNode, parentNode);
            }
        }
        return false;
    }

    private Set<PDGNode> getThrowStatementNodesWithinRegion() {
        Set<PDGNode> throwNodes = new LinkedHashSet<>();
        for (GraphNode node : selection.nodes) {
            PDGNode pdgNode = (PDGNode) node;
            if (pdgNode.getCFGNode() instanceof CFGThrowNode) {
                throwNodes.add(pdgNode);
            }
        }
        return throwNodes;
    }

    public PDG getPdg() {
        return pdg;
    }

    Set<PDGNode> getSliceNodes() {
        return sliceNodes;
    }

    Set<PDGNode> getRemovableNodes() {
        return removableNodes;
    }

    AbstractVariable getBaseVariable() {
        return baseVariable;
    }

    Set<AbstractVariable> getPassedParameters() {
        return passedParameters;
    }

    PDGNode getDeclarationOfVariableCriterion() {
        for (PDGNode pdgNode : sliceNodes) {
            if (pdgNode.declaresLocalVariable(baseVariable))
                return pdgNode;
        }
        return null;
    }

    PDGNode getExtractedMethodInvocationInsertionNode() {
        return ((TreeSet<PDGNode>) sliceNodes).first();
    }

    boolean declarationOfVariableCriterionBelongsToSliceNodes() {
        for (PDGNode node : sliceNodes) {
            if (node.declaresLocalVariable(baseVariable))
                return true;
        }
        return false;
    }

    boolean declarationOfVariableCriterionBelongsToRemovableNodes() {
        for (PDGNode node : removableNodes) {
            if (node.declaresLocalVariable(baseVariable))
                return true;
        }
        return false;
    }

    boolean isNotTrivial() {
        if (sliceNodes.size() <= 1)
            return false;
        if (sliceNodes.size() == 2) {
            return !sliceNodes.stream().map(n -> n.declaresLocalVariable(baseVariable)).reduce((a, b) -> a || b).get();
        }
        return true;
    }

    // Checks whether current slice is valid, useful and "optimal"
    public boolean isValid() {
        return isNotTrivial();
    }
}
