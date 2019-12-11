package core.ast;

import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import core.ast.decomposition.cfg.ASTSlice;
import core.ast.decomposition.cfg.PDGVariableBasedSlices;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@TestDataPath("$PROJECT_ROOT/testData/")
public class PartialMethodExtractorTest extends BasePlatformTestCase {
    PsiElementFactory elementFactory;
    PsiFile file;
    PsiElement firstStatement;
    PsiElement lastStatement;

    @Override
    protected String getTestDataPath() {
        return "testData/";
    }

    public void testSimple() {
        List<PDGVariableBasedSlices> opportunities = getRefactoringOpportunities("SimpleTest.java");
        assertEquals(opportunities.size(), 2);

        Map<String, PDGVariableBasedSlices> slicesByVariable = opportunities.stream().collect(
                Collectors.toMap(
                slices -> slices.getBaseVariable().getName(), Function.identity()));

        elementFactory = JavaPsiFacade.getElementFactory(myFixture.getProject());

        // Is assigned but not used, thus the only possible extraction is trivial
        assertNull(slicesByVariable.get("xx"));
        assertNull(slicesByVariable.get("yy"));

        PDGVariableBasedSlices zzBasedSlices = slicesByVariable.get("zz");
        assertNotNull(zzBasedSlices);
        checkStatements(zzBasedSlices.getSlices().get(0),
                prepareStatements("int zz = pp;", "zz = 14;", "zz *= 24;", "zz += yy - xx;"),
                prepareStatements(),
                prepareStatements("int zz = pp;", "zz = 14;", "zz *= 24;", "zz += yy - xx;"));

        PDGVariableBasedSlices ddBasedSlices = slicesByVariable.get("dd");
        assertNotNull(ddBasedSlices);
        checkStatements(ddBasedSlices.getSlices().get(0),
                prepareStatements("int dd = 0;", "dd += 35;", "dd *= 3;"),
                prepareStatements(),
                prepareStatements("int dd = 0;", "dd += 35;", "dd *= 3;"));
    }

    public void testNested() {
        List<PDGVariableBasedSlices> opportunities = getRefactoringOpportunities("NestedTest.java");
        assertEquals(2, opportunities.size());

        Map<String, PDGVariableBasedSlices> slicesByVariable = opportunities.stream().collect(
                Collectors.toMap(
                        slice -> slice.getBaseVariable().getName(), Function.identity()));

        PDGVariableBasedSlices xxBasedSlices = slicesByVariable.get("xx");
        assertNotNull(xxBasedSlices);
        assertEquals(xxBasedSlices.getSlices().size(), 1);
        assertEquals(xxBasedSlices.getSlices().get(0).getSliceStatements().size(),9);
        assertEquals(xxBasedSlices.getSlices().get(0).getDuplicatedStatements().size(),3);
        assertEquals(xxBasedSlices.getSlices().get(0).getRemovableStatements().size(),6);

        PDGVariableBasedSlices yyBasedSlices = slicesByVariable.get("yy");
        assertNotNull(yyBasedSlices);
        assertEquals(yyBasedSlices.getSlices().size(), 1);
        assertEquals(yyBasedSlices.getSlices().get(0).getSliceStatements().size(),9);
        assertEquals(yyBasedSlices.getSlices().get(0).getDuplicatedStatements().size(),3);
        assertEquals(yyBasedSlices.getSlices().get(0).getRemovableStatements().size(),6);
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

    private void printSlicesInfo(List<PDGVariableBasedSlices> opportunities) {
        for (PDGVariableBasedSlices slices : opportunities) {
            System.out.println(slices.getBaseVariable().getName());
            for (ASTSlice slice : slices.getSlices()) {
                outputStatements(slice.getSliceStatements(), "Slice");
                outputStatements(slice.getDuplicatedStatements(), "Duplicated");
                outputStatements(slice.getRemovableStatements(), "Removable");
                System.out.println();
            }
            System.out.println();
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

    private List<PDGVariableBasedSlices> getRefactoringOpportunities(final String fileName) {
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

        List<PDGVariableBasedSlices> opportunities = PartialMethodExtractor.getOpportunities(
                (PsiMethod) method,firstStatement, lastStatement);
        assertNotNull(opportunities);
        return opportunities;
    }
}