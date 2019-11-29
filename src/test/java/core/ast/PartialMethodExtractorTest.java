package core.ast;

import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import core.ast.decomposition.cfg.ASTSlice;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PartialMethodExtractorTest extends LightPlatformCodeInsightFixtureTestCase {
    PsiElementFactory elementFactory;
    PsiFile file;
    PsiElement firstStatement;
    PsiElement lastStatement;

    public void testSimple() {
        List<ASTSlice> opportunities = getRefactoringOpportunities("src/testData/SimpleTest.java");
        assertEquals(opportunities.size(), 3);

        Map<String, ASTSlice> sliceByVariable = opportunities.stream().collect(
                Collectors.toMap(
                slice -> slice.getLocalVariableCriterion().getName(), Function.identity()));

        elementFactory = JavaPsiFacade.getElementFactory(myFixture.getProject());

        // Is assigned but not used, thus the only possible extraction is trivial
        assertNull(sliceByVariable.get("xx"));

        ASTSlice yyBasedSlice = sliceByVariable.get("yy");
        assertNotNull(yyBasedSlice);
        checkStatements(yyBasedSlice,
                prepareStatements("int xx = pp;", "int yy = 0;", "yy = xx + tt;"),
                prepareStatements("int xx = pp;"),
                prepareStatements("int yy = 0;", "yy = xx + tt;"));

        ASTSlice zzBasedSlice = sliceByVariable.get("zz");
        assertNotNull(zzBasedSlice);
        checkStatements(zzBasedSlice,
                prepareStatements("int xx = pp;", "int yy = 0;", "yy = xx + tt;",
                        "int zz = pp;", "zz = 14;", "zz *= 24;", "zz += yy - xx;"),
                prepareStatements("int xx = pp;", "int yy = 0;", "yy = xx + tt;"),
                prepareStatements("int zz = pp;", "zz = 14;", "zz *= 24;", "zz += yy - xx;"));

        ASTSlice ddBasedSlice = sliceByVariable.get("dd");
        assertNotNull(ddBasedSlice);
        checkStatements(ddBasedSlice,
                prepareStatements("int dd = 0;", "dd += 35;", "dd *= 3;"),
                prepareStatements(),
                prepareStatements("int dd = 0;", "dd += 35;", "dd *= 3;"));
    }

    public void testNested() {
        List<ASTSlice> opportunities = getRefactoringOpportunities("src/testData/NestedTest.java");
        assertEquals(opportunities.size(), 2);

        Map<String, ASTSlice> sliceByVariable = opportunities.stream().collect(
                Collectors.toMap(
                        slice -> slice.getLocalVariableCriterion().getName(), Function.identity()));

        ASTSlice xxBasedSlice = sliceByVariable.get("xx");
        assertNotNull(xxBasedSlice);
        assertEquals(xxBasedSlice.getSliceStatements().size(),9);
        assertEquals(xxBasedSlice.getDuplicatedStatements().size(),3);
        assertEquals(xxBasedSlice.getRemovableStatements().size(),6);

        ASTSlice yyBasedSlice = sliceByVariable.get("yy");
        assertNotNull(yyBasedSlice);
        assertEquals(yyBasedSlice.getSliceStatements().size(),9);
        assertEquals(yyBasedSlice.getDuplicatedStatements().size(),3);
        assertEquals(yyBasedSlice.getRemovableStatements().size(),6);
    }

    private Set<String> prepareStatements(String... statements) {
        return new HashSet<>(Arrays.asList(statements));
    }

    private void checkStatements(ASTSlice slice, Set<String> sliceStatements,
                                 Set<String> duplicatedStatements, Set<String> removableStatements) {
        assertEquals(slice.getSliceStatements().stream().map(PsiStatement::getText).
                collect(Collectors.toSet()), sliceStatements);
        assertEquals(slice.getDuplicatedStatements().stream().map(PsiStatement::getText).
                collect(Collectors.toSet()), duplicatedStatements);
        assertEquals(slice.getRemovableStatements().stream().map(PsiStatement::getText).
                collect(Collectors.toSet()), removableStatements);
    }

    private void printSlicesInfo(List<ASTSlice> opportunities) {
        for (ASTSlice slice : opportunities) {
            System.out.println(slice.getLocalVariableCriterion().getName());
            outputStatements(slice.getSliceStatements(), "Slice");
            outputStatements(slice.getDuplicatedStatements(), "Duplicated");
            outputStatements(slice.getRemovableStatements(), "Removable");
        }
    }

    private void outputStatements(Set<PsiStatement> nodes, String name) {
        StringBuilder str = new StringBuilder();
        int i = 0;
        for (PsiStatement statement : nodes) {
            str.append(++i).append(": ").append(statement.getText()).append("\n");
        }
        System.out.println(name);
        System.out.println(str.toString());
    }

    private List<ASTSlice> getRefactoringOpportunities(final String fileName) {
        file = myFixture.configureByFile(fileName);
        SelectionModel selectionModel = myFixture.getEditor().getSelectionModel();
        firstStatement = file.findElementAt(selectionModel.getSelectionStart());
        lastStatement = file.findElementAt(selectionModel.getSelectionEnd());

        assertNotNull(firstStatement);
        assertNotNull(lastStatement);

        final PsiElement codeBlock = PsiTreeUtil.findFirstParent(
                PsiTreeUtil.findCommonContext(firstStatement, lastStatement),
                p -> p instanceof PsiCodeBlock);
        PsiElement method = PsiTreeUtil.findFirstParent(codeBlock, p -> p instanceof PsiMethod);

        assertNotNull(method);

        firstStatement = PsiTreeUtil.findFirstParent(firstStatement, p -> p.getParent() == codeBlock);
        lastStatement  = PsiTreeUtil.findFirstParent(lastStatement, p -> p.getParent() == codeBlock);

        List<ASTSlice> opportunities = PartialMethodExtractor.getOpportunities(
                (PsiMethod) method,firstStatement, lastStatement);
        assertNotNull(opportunities);
        return opportunities;
    }
}