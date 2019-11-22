package refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.extractMethod.*;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.VariableData;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import core.ast.decomposition.cfg.ASTSlice;
import core.ast.decomposition.cfg.PDGNode;
import org.jetbrains.annotations.NotNull;
import utils.PsiUtils;

import java.util.*;

public class PartialExtractMethodProcessor extends ExtractMethodProcessor {
    private PsiVariable myCriterion;
    private ASTSlice mySlice;
    private PsiElementFactory myElementFactory;

    /**
     * Constructs a processor for statement extraction to new method
     *
     * @param project           project containing statements
     * @param editor            editor showing statements
     * @param slice             slice defining an extraction
     */
    public PartialExtractMethodProcessor(@NotNull Project project, Editor editor, ASTSlice slice)
    {
        super(project, editor, getElements(slice.getSliceNodes()), slice.getLocalVariableCriterion().getType(),
                "Partial Method Extraction", "", "refactoring.extractMethod");
        mySlice = slice;
        myReturnType = slice.getLocalVariableCriterion().getType();
        myThrownExceptions = new PsiClassType[0];
        myTargetClass = slice.getSourceTypeDeclaration();
        myElementFactory = JavaPsiFacade.getElementFactory(PsiManager.getInstance(project).getProject());
        myOutputVariables = new PsiVariable[1];
        myOutputVariables[0] = myOutputVariable = myCriterion = slice.getLocalVariableCriterion();
    }

    static private PsiElement[] getElements(Set<PDGNode> nodes) {
        ArrayList<PsiStatement> statementsToExtract = new ArrayList<>();
        StringBuilder msg = new StringBuilder();
        for (PDGNode pdgNode : nodes) {
            boolean isNotChild = true;
            for (PDGNode node : nodes) {
                if (PsiUtils.isChild(node.getASTStatement(), pdgNode.getASTStatement())) {
                    isNotChild = false;
                }
            }
            if (isNotChild) {
                statementsToExtract.add(pdgNode.getASTStatement());
            }
        }
        return statementsToExtract.toArray(new PsiElement[0]);
    }

    /**
     * Checks whether the variable is declared in statements which are being extracted
     *
     * @param variable variable to check
     * @return true if variable declaration is present within statements, false otherwise
     */
    @Override
    public boolean isDeclaredInside(PsiVariable variable) {
        if (variable instanceof ImplicitVariable)
            return false;
        if (variable.getNameIdentifier() == null ||
            variable.getNameIdentifier().getTextRange() == null)
            return false;
        int startOffset = variable.getNameIdentifier().getTextRange().getStartOffset();

        for (PsiElement element : myElements) {
            if (element.getTextRange().getStartOffset() <= startOffset &&
                element.getTextRange().getEndOffset() >= startOffset)
                return true;
        }
        return false;
    }

    /**
     * Collects local variables which are declared in statements which are being extracted
     *
     * @return set of local variables
     */
    @NotNull
    @Override
    protected Set<PsiVariable> getEffectivelyLocalVariables() {
        HashSet<PsiVariable> variables = new HashSet<>();
        for (PsiElement psiElement : myElements) {
            Collection<PsiReferenceExpression> referenceExpressions =
                    PsiTreeUtil.findChildrenOfType(psiElement, PsiReferenceExpression.class);
            for (PsiReferenceExpression expression : referenceExpressions) {
                PsiElement element = expression.resolve();
                if (element instanceof PsiLocalVariable) {
                    variables.add((PsiVariable) element);
                }
            }
        }
        return variables;
    }

    /**
     * Collects used local variables, fields and method parameters.
     *
     * @return set of used variables.
     */
    private Set<PsiVariable> getUsedVariables() {
        HashSet<PsiVariable> variables = new HashSet<>();
        for (PsiElement psiElement : myElements) {
            Collection<PsiReferenceExpression> referenceExpressions =
                    PsiTreeUtil.findChildrenOfType(psiElement, PsiReferenceExpression.class);
            for (PsiReferenceExpression referenceExpression : referenceExpressions) {
                if (referenceExpression.resolve() != null && referenceExpression.resolve() instanceof PsiVariable) {
                    variables.add((PsiVariable) referenceExpression.resolve());
                }
            }
        }
        return variables;
    }

    /**
     * Creates new method in class and extracts statements into the method.
     */
    @Override
    public void doExtract() {
        PsiMethod newMethod = generateEmptyMethod(myMethodName, null);
        prepareMethodBody(newMethod);
        myExtractedMethod = addExtractedMethod(newMethod);
        setMethodCall(generateMethodCall(null, true));

        final String outputVariableName = myCriterion.getName();
        if (isDeclaredInside(myCriterion) && outputVariableName != null) {
            PsiDeclarationStatement statement = myElementFactory.createVariableDeclarationStatement(outputVariableName,
                    myCriterion.getType(), getMethodCall());
            statement = (PsiDeclarationStatement) JavaCodeStyleManager.getInstance(myProject)
                    .shortenClassReferences(addToMethodCallLocation(statement));
            PsiVariable var = (PsiVariable) statement.getDeclaredElements()[0];
            setMethodCall((PsiMethodCallExpression) var.getInitializer());
        } else {
            PsiExpressionStatement statement = (PsiExpressionStatement) myElementFactory.createStatementFromText(outputVariableName + "=x;", null);
            statement = (PsiExpressionStatement) addToMethodCallLocation(statement);
            PsiAssignmentExpression assignment = (PsiAssignmentExpression) statement.getExpression();
            setMethodCall((PsiMethodCallExpression) Objects.requireNonNull(assignment.getRExpression()).replace(getMethodCall()));
        }

        for (PsiElement element : myElements) {
            if (mySlice.getDuplicatedStatements().contains(element) ||
                    element instanceof PsiCodeBlock ||
                    element instanceof PsiBlockStatement)
                removeExtractedStatements(element);
            else if (mySlice.getRemovableStatements().contains(element))
                element.delete();
        }
    }

    private void removeExtractedStatements(PsiElement element) {
        for (PsiElement child : element.getChildren()) {
            if (mySlice.getDuplicatedStatements().contains(child) ||
                    child instanceof PsiCodeBlock ||
                    child instanceof PsiBlockStatement) {
                removeExtractedStatements(child);
            } else if (mySlice.getRemovableStatements().contains(child))
                child.delete();
        }
    }

    /**
     * Sets parameters for new method.
     */
    @Override
    public void setDataFromInputVariables() {
        final List<VariableData> variables = myInputVariables.getInputVariables();
        final Set<PsiVariable> usedVariables = getUsedVariables();
        ArrayList<PsiVariable> inputVariables = new ArrayList<>();
        for (VariableData data : variables) {
            if (usedVariables.contains(data.variable)) {
                inputVariables.add(data.variable);
            }
        }
        myInputVariables = new InputVariables(inputVariables, myProject, new LocalSearchScope(myElements), true);
        myVariableDatum = myInputVariables.getInputVariables().toArray(new VariableData[0]);
    }

    /**
     * Prepares body for a new method.
     *
     * @param newMethod new method with empty body.
     */
    private void prepareMethodBody(PsiMethod newMethod) {
        PsiCodeBlock body = newMethod.getBody();
        if (body != null) {
            for (PsiElement psiElement : myElements) {
                PsiElement copied = body.add(psiElement);
                clearRemovableStatements(copied, psiElement);
            }

            PsiReturnStatement returnStatement;
            if (myNullConditionalCheck) {
                returnStatement = (PsiReturnStatement) myElementFactory.createStatementFromText("return null;", null);
            } else if (myOutputVariable != null) {
                returnStatement = (PsiReturnStatement) myElementFactory.createStatementFromText("return " + myOutputVariable.getName() + ";", null);
            } else if (myGenerateConditionalExit) {
                returnStatement = (PsiReturnStatement) myElementFactory.createStatementFromText("return true;", null);
            } else {
                returnStatement = (PsiReturnStatement) myElementFactory.createStatementFromText("return;", null);
            }
            final PsiReturnStatement insertedReturnStatement = (PsiReturnStatement) body.add(returnStatement);
            if (myOutputVariables.length == 1) {
                final PsiExpression returnValue = insertedReturnStatement.getReturnValue();
                if (returnValue instanceof PsiReferenceExpression) {
                    final PsiVariable variable = ObjectUtils.tryCast(((PsiReferenceExpression) returnValue).resolve(), PsiVariable.class);
                    if (variable != null && Comparing.strEqual(variable.getName(), myOutputVariable.getName())) {
                        final PsiStatement statement = PsiTreeUtil.getPrevSiblingOfType(insertedReturnStatement, PsiStatement.class);
                        if (statement instanceof PsiDeclarationStatement) {
                            final PsiElement[] declaredElements = ((PsiDeclarationStatement) statement).getDeclaredElements();
                            if (ArrayUtil.find(declaredElements, variable) != -1) {
                                InlineUtil.inlineVariable(variable, PsiUtil.skipParenthesizedExprDown(variable.getInitializer()),
                                        (PsiReferenceExpression) returnValue);
                                variable.delete();
                            }
                        }
                    }
                }
            }
        }
    }

    private void clearRemovableStatements(PsiElement copied, PsiElement original) {
        PsiElement[] copiedChildren = copied.getChildren();
        PsiElement[] originalChildren = original.getChildren();
        for (int i = 0; i < originalChildren.length; ++i) {
            PsiElement copiedChild = copiedChildren[i];
            PsiElement originalChild = originalChildren[i];
            if (copiedChild == null)
                continue;
            if (originalChild instanceof PsiStatement) {
                PsiStatement statement = (PsiStatement) originalChild;
                if (mySlice.getDuplicatedStatements().contains(statement) ||
                    statement instanceof PsiBlockStatement)
                    clearRemovableStatements(copiedChild,originalChild);
                else if (!mySlice.getRemovableStatements().contains(statement))
                    copiedChild.delete();
            } else
                clearRemovableStatements(copiedChild, originalChild);
        }
    }
}
