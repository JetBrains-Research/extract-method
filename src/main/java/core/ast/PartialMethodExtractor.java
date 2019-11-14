package core.ast;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.*;
import core.ast.decomposition.MethodBodyObject;
import core.ast.decomposition.cfg.*;

import java.util.*;

public class PartialMethodExtractor {

    public static List<ASTSlice> getOpportunities(PsiMethod method, PsiElement firstElement, PsiElement lastElement) {
        AbstractMethodDeclaration declaration = processMethodDeclaration(method);
        CFG cfg = new CFG(declaration);
        // Under what circumstances can this situation occur?
        if (method.getContainingClass() == null)
            return null;
        PDG pdg = new PDG(cfg, method.getContainingFile(), getFieldObjects(method.getContainingClass()));
        PDGSelection selection = new PDGSelection(pdg, firstElement, lastElement);
        List<ASTSlice> slices = new ArrayList<>();
        for (PsiVariable variableDeclaration : pdg.getVariableDeclarationsInMethod()) {
            PlainVariable variable = new PlainVariable(variableDeclaration);
            if (selection.isAssigned(variable)) {
                PDGSelectionSlice union = new PDGSelectionSlice(selection, variable);
                ASTSlice slice = new ASTSlice(union);
                slices.add(slice);
            }
        }
        return slices;
    }

    private static Set<FieldObject> getFieldObjects(PsiClass parentClass) {
        HashSet<FieldObject> set = new HashSet<>();
        PsiField[] fieldDeclarations = parentClass.getFields();

        for (PsiField fieldDeclaration : fieldDeclarations) {
            List<CommentObject> fieldDeclarationComments = new ArrayList<>();

            TypeObject typeObject = TypeObject.extractTypeObject(fieldDeclaration.getType().getCanonicalText());
            typeObject.setArrayDimension(typeObject.getArrayDimension());
            FieldObject fieldObject = new FieldObject(typeObject, fieldDeclaration.getName(), fieldDeclaration);
            fieldObject.setClassName(parentClass.getName());
            fieldObject.addComments(fieldDeclarationComments);

            if (fieldDeclaration.hasModifier(JvmModifier.PUBLIC))
                fieldObject.setAccess(Access.PUBLIC);
            else if (fieldDeclaration.hasModifier(JvmModifier.PROTECTED))
                fieldObject.setAccess(Access.PROTECTED);
            else if (fieldDeclaration.hasModifier(JvmModifier.PRIVATE))
                fieldObject.setAccess(Access.PRIVATE);
            else
                fieldObject.setAccess(Access.NONE);
            if (fieldDeclaration.hasModifier(JvmModifier.STATIC))
                fieldObject.setStatic(true);

            set.add(fieldObject);
        }
        return set;
    }

    private static AbstractMethodDeclaration processMethodDeclaration(PsiMethod methodDeclaration) {
        PsiClass parentClass = methodDeclaration.getContainingClass();
        String methodName = methodDeclaration.getName();
        final ConstructorObject constructorObject = new ConstructorObject();
        constructorObject.setMethodDeclaration(methodDeclaration);
        constructorObject.setName(methodName);
        constructorObject.setClassName(parentClass.getName());

        if (methodDeclaration.hasModifier(JvmModifier.PUBLIC))
            constructorObject.setAccess(Access.PUBLIC);
        else if (methodDeclaration.hasModifier(JvmModifier.PROTECTED))
            constructorObject.setAccess(Access.PROTECTED);
        else if (methodDeclaration.hasModifier(JvmModifier.PRIVATE))
            constructorObject.setAccess(Access.PRIVATE);
        else
            constructorObject.setAccess(Access.NONE);

        PsiParameter[] parameters = methodDeclaration.getParameterList().getParameters();
        for (PsiParameter parameter : parameters) {
            String parameterType = parameter.getType().getCanonicalText();
            TypeObject typeObject = TypeObject.extractTypeObject(parameterType);
            if (parameter.isVarArgs()) {
                typeObject.setArrayDimension(1);
            }
            ParameterObject parameterObject = new ParameterObject(typeObject, parameter.getName(), parameter.isVarArgs());
            parameterObject.setSingleVariableDeclaration(parameter);
            constructorObject.addParameter(parameterObject);
        }

        PsiCodeBlock methodBody = methodDeclaration.getBody();
        if (methodBody != null) {
            MethodBodyObject methodBodyObject = new MethodBodyObject(methodBody);
            constructorObject.setMethodBody(methodBodyObject);
        }

        for (AnonymousClassDeclarationObject anonymous : constructorObject.getAnonymousClassDeclarations()) {
            PsiAnonymousClass anonymousClassDeclaration = anonymous.getAnonymousClassDeclaration();
            int anonymousClassDeclarationStartPosition = anonymousClassDeclaration.getStartOffsetInParent();
            int anonymousClassDeclarationEndPosition = anonymousClassDeclarationStartPosition + anonymousClassDeclaration.getTextLength();
            for (CommentObject comment : constructorObject.commentList) {
                int commentStartPosition = comment.getStartPosition();
                int commentEndPosition = commentStartPosition + comment.getLength();
                if (anonymousClassDeclarationStartPosition <= commentStartPosition && anonymousClassDeclarationEndPosition >= commentEndPosition) {
                    anonymous.addComment(comment);
                }
            }
        }

        if (methodDeclaration.isConstructor()) {
            return constructorObject;
        } else {
            MethodObject methodObject = new MethodObject(methodDeclaration, constructorObject);
            PsiAnnotation[] extendedModifiers = methodDeclaration.getAnnotations();
            for (PsiAnnotation extendedModifier : extendedModifiers) {
                if ("Test".equals(extendedModifier.getQualifiedName())) {
                    methodObject.setTestAnnotation(true);
                    break;
                }
            }
            PsiType returnType = methodDeclaration.getReturnType();
            String qualifiedName = returnType != null ? returnType.getCanonicalText() : null;
            TypeObject typeObject = TypeObject.extractTypeObject(qualifiedName);
            methodObject.setReturnType(typeObject);
            if (methodDeclaration.hasModifier(JvmModifier.ABSTRACT))
                methodObject.setAbstract(true);
            if (methodDeclaration.hasModifier(JvmModifier.STATIC))
                methodObject.setStatic(true);
            if (methodDeclaration.hasModifier(JvmModifier.SYNCHRONIZED))
                methodObject.setSynchronized(true);
            if (methodDeclaration.hasModifier(JvmModifier.NATIVE))
                methodObject.setNative(true);

            return methodObject;
        }
    }
}
