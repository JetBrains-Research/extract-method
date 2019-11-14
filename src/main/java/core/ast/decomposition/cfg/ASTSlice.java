package core.ast.decomposition.cfg;

import com.intellij.psi.*;
import com.sun.istack.NotNull;

import java.util.*;

public class ASTSlice {
    @NotNull
    private final PsiClass sourceTypeDeclaration;
    @NotNull
    private final PsiMethod sourceMethodDeclaration;
    @NotNull
    private final PsiFile psiFile;
    @NotNull
    private PsiStatement variableCriterionDeclarationStatement;
    @NotNull
    private PsiStatement extractedMethodInvocationInsertionStatement;
    private PsiVariable localVariableCriterion;
    private Set<PDGNode> sliceNodes;
    private Set<PsiStatement> sliceStatements;
    private Set<PsiStatement> removableStatements;
    private Set<PsiStatement> duplicatedStatements;
    private Set<PsiVariable> passedParameters;

    private String extractedMethodName;
    private boolean declarationOfVariableCriterionBelongsToSliceNodes;
    private boolean declarationOfVariableCriterionBelongsToRemovableNodes;
    private boolean isObjectSlice;
    private int methodSize;

    public ASTSlice(PDGSelectionSlice pdgSliceUnion) {
        sourceMethodDeclaration = pdgSliceUnion.getPdg().getMethod().getMethodDeclaration();
        sourceTypeDeclaration = sourceMethodDeclaration.getContainingClass();
        sliceNodes = pdgSliceUnion.getSliceNodes();
        sliceStatements = new LinkedHashSet<>();
        for (PDGNode node : sliceNodes) {
            sliceStatements.add(node.getASTStatement());
        }
        removableStatements = new LinkedHashSet<>();
        for (PDGNode node : pdgSliceUnion.getRemovableNodes()) {
            removableStatements.add(node.getASTStatement());
        }
        duplicatedStatements = new LinkedHashSet<>(sliceStatements);
        duplicatedStatements.removeAll(removableStatements);
        Set<PsiVariable> variableDeclarationsAndAccessedFields =
                pdgSliceUnion.getPdg().getVariableDeclarationsAndAccessedFieldsInMethod();
        AbstractVariable criterion = pdgSliceUnion.getBaseVariable();
        for (PsiVariable variableDeclaration : variableDeclarationsAndAccessedFields) {
            if (variableDeclaration.equals(criterion.getOrigin())) {
                localVariableCriterion = variableDeclaration;
                extractedMethodName = Objects.requireNonNull(localVariableCriterion.getNameIdentifier()).getText();
                break;
            }
        }
        passedParameters = new LinkedHashSet<>();
        for (AbstractVariable variable : pdgSliceUnion.getPassedParameters()) {
            for (PsiVariable variableDeclaration : variableDeclarationsAndAccessedFields) {
                if (variableDeclaration.equals(variable.getOrigin())) {
                    passedParameters.add(variableDeclaration);
                    break;
                }
            }
        }
        PDGNode declarationOfVariableCriterionNode = pdgSliceUnion.getDeclarationOfVariableCriterion();
        if (declarationOfVariableCriterionNode != null)
            variableCriterionDeclarationStatement = declarationOfVariableCriterionNode.getASTStatement();
        extractedMethodInvocationInsertionStatement = pdgSliceUnion.getExtractedMethodInvocationInsertionNode().getASTStatement();
        declarationOfVariableCriterionBelongsToSliceNodes = pdgSliceUnion.declarationOfVariableCriterionBelongsToSliceNodes();
        declarationOfVariableCriterionBelongsToRemovableNodes = pdgSliceUnion.declarationOfVariableCriterionBelongsToRemovableNodes();
        psiFile = pdgSliceUnion.getPdg().getPsiFile();
        isObjectSlice = false;
        methodSize = pdgSliceUnion.getPdg().getTotalNumberOfStatements();
    }

    public boolean isVariableCriterionDeclarationStatementIsDeeperNestedThanExtractedMethodInvocationInsertionStatement() {
        PsiStatement variableCriterionDeclarationStatement = getVariableCriterionDeclarationStatement();
        if (variableCriterionDeclarationStatement != null) {
            int depthOfNestingForVariableCriterionDeclarationStatement = depthOfNesting(variableCriterionDeclarationStatement);
            PsiStatement extractedMethodInvocationInsertionStatement = getExtractedMethodInvocationInsertionStatement();
            int depthOfNestingForExtractedMethodInvocationInsertionStatement = depthOfNesting(extractedMethodInvocationInsertionStatement);
            if (depthOfNestingForVariableCriterionDeclarationStatement > depthOfNestingForExtractedMethodInvocationInsertionStatement)
                return true;
            return depthOfNestingForVariableCriterionDeclarationStatement == depthOfNestingForExtractedMethodInvocationInsertionStatement
                    && variableCriterionDeclarationStatement instanceof PsiTryStatement;
        }
        return false;
    }

    private int depthOfNesting(PsiStatement statement) {
        int depthOfNesting = 0;
        PsiElement parent = statement;
        while (!(parent instanceof PsiMethod)) {
            depthOfNesting++;
            parent = parent.getParent();
        }
        return depthOfNesting;
    }

    public PsiClass getSourceTypeDeclaration() {
        return sourceTypeDeclaration;
    }

    public PsiMethod getSourceMethodDeclaration() {
        return sourceMethodDeclaration;
    }

    public PsiVariable getLocalVariableCriterion() {
        return localVariableCriterion;
    }

    public Set<PsiVariable> getPassedParameters() {
        return passedParameters;
    }

    public Set<PDGNode> getSliceNodes() {
        return sliceNodes;
    }

    public Set<PsiStatement> getSliceStatements() {
        return sliceStatements;
    }

    public Set<PsiStatement> getRemovableStatements() {
        return removableStatements;
    }

    private PsiStatement getVariableCriterionDeclarationStatement() {
        return variableCriterionDeclarationStatement;
    }

    private PsiStatement getExtractedMethodInvocationInsertionStatement() {
        return extractedMethodInvocationInsertionStatement;
    }

    public String getExtractedMethodName() {
        return extractedMethodName;
    }

    public void setExtractedMethodName(String extractedMethodName) {
        this.extractedMethodName = extractedMethodName;
    }

    public boolean declarationOfVariableCriterionBelongsToSliceNodes() {
        return declarationOfVariableCriterionBelongsToSliceNodes;
    }

    public boolean declarationOfVariableCriterionBelongsToRemovableNodes() {
        return declarationOfVariableCriterionBelongsToRemovableNodes;
    }

    public PsiFile getPsiFile() {
        return psiFile;
    }


    public boolean isObjectSlice() {
        return isObjectSlice;
    }

    public int getMethodSize() {
        return methodSize;
    }

    public String sliceToString() {
        StringBuilder sb = new StringBuilder();
        for (PDGNode sliceNode : sliceNodes) {
            sb.append(sliceNode.getStatement().toString());
        }
        return sb.toString();
    }

    public String toString() {
        return //getSourceTypeDeclaration().getQualifiedName() + "::" +
                getSourceMethodDeclaration().getName() + "." +
                getLocalVariableCriterion().getName();
    }

    public int getNumberOfSliceStatements() {
        return getSliceStatements().size();
    }

    public int getNumberOfDuplicatedStatements() {
        int numberOfSliceStatements = getNumberOfSliceStatements();
        int numberOfRemovableStatements = getRemovableStatements().size();
        return numberOfSliceStatements - numberOfRemovableStatements;
    }

    public Set<PsiStatement> getDuplicatedStatements() {
        return duplicatedStatements;
    }
}
