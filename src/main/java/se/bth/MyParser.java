package se.bth;

import java.io.File;
import java.util.List;
import java.util.LinkedList;

import java.io.IOException;
import java.util.List;
import java.util.LinkedList;
import java.util.stream.Collectors;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;

import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;

import com.github.javaparser.utils.SourceRoot;

public class MyParser {

    private List<CompilationUnit> allCus;

    public MyParser(File javaDir, File testDir) throws IOException {

        /*
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(
                                            new ReflectionTypeSolver(),
                                            new JavaParserTypeSolver(javaDir));
        */
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();

        // Use our Symbol Solver while parsing
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));

        typeSolver.add(new JavaParserTypeSolver(javaDir, parserConfiguration));
        typeSolver.add(new ReflectionTypeSolver());

        // Parse all source files
        SourceRoot sourceRoot = new SourceRoot(testDir.toPath());
        sourceRoot.setParserConfiguration(parserConfiguration);

        List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse("");
        // Now get all compilation units
        this.allCus = parseResults.stream()
                                    .filter(ParseResult::isSuccessful)
                                    .map(r -> r.getResult().get())
                                    .collect(Collectors.toList());
    }

    public void analyseMethods() {
            getNodes(this.allCus, MethodDeclaration.class).stream()
                                        .forEach(c -> this.processMethod((MethodDeclaration) c));
    }

    private void processMethod(MethodDeclaration method) {

        int assertCount = 0;

        // Analyse only methods, which are annotated with 'Test'
        if (method.getAnnotationByName("Test").isPresent()) {
            System.out.println(method.getNameAsString());
            for (Expression expr: method.findAll(Expression.class)) {
                // Go through all methods called by the analysed method
                if (expr instanceof MethodCallExpr) {
                    if (handleMethodExpression((MethodCallExpr) expr)) {
                        assertCount++;
                    }
                } else if (expr instanceof AssignExpr) {
                    Expression variable = ((AssignExpr) expr).getTarget();
                    System.out.println("\tAssigned: " + variable);
                }
                // TODO: think if other expression types are important here as well
            }
            System.out.println("Number of Asserts: " + assertCount);
        }
    }

    private boolean isModified(Expression expr) {
        boolean isMutator = false;
        Expression targetExpression = null;

        if (expr.getParentNode().isPresent()) {
        	System.out.println("parentNode: " + expr.getParentNode().toString());
            Expression parentExpr = (Expression) expr.getParentNode().get();
            if (parentExpr instanceof UnaryExpr) {
                targetExpression = ((UnaryExpr) parentExpr).getExpression();
            } else if (parentExpr instanceof AssignExpr) {
                targetExpression = ((AssignExpr) parentExpr).getTarget();
            }
            isMutator = expr.equals(targetExpression);
        }

        return isMutator;
    }

    private void analyzeCalledMethod(MethodDeclaration method, String callingObject) {
        boolean isMutator = false;

        // check all field accesses
        for (FieldAccessExpr expr: method.findAll(FieldAccessExpr.class)) {
            isMutator |= this.isModified(expr);
            Expression scope = expr.getScope();
            if (scope instanceof ThisExpr) {
                ThisExpr thisExpr = (ThisExpr) scope;
                ResolvedTypeDeclaration type = thisExpr.resolve();
                System.out.println("\tField Access: "
                                   + type.getQualifiedName() + "."
                                   + callingObject + "."
                                   + expr.getNameAsString());
            } else if (scope instanceof NameExpr) {
                NameExpr nameExpr = (NameExpr) scope;
                ResolvedValueDeclaration value = nameExpr.resolve();
                ResolvedReferenceType type = value.getType().asReferenceType();
//                System.out.println("nameExpr: " + nameExpr.getNameAsString() + "; " +
//                				   "value: " + value.getName() + "; " +
//                				   "type: " + type.getQualifiedName());
                System.out.println("\tField Access: "
                                   + type.getQualifiedName() + "."
                                   + value.getName() + "."
                                   + callingObject + "."
                                   + expr.getNameAsString());
            } else {
                throw new RuntimeException("Unknown instance for: " + scope);
            }
        }
        // check for fields which are accessed simply by name
        for (NameExpr expr: method.findAll(NameExpr.class)) {
            ResolvedValueDeclaration value = expr.resolve();
            if (value.isField()) {
                ResolvedFieldDeclaration field = (ResolvedFieldDeclaration) value;
                ResolvedTypeDeclaration type = field.declaringType();
                System.out.println("\tClass field: "
                                   + type.getQualifiedName() + "."
                                   + callingObject + "."
                                   + field.getName());

                isMutator |= this.isModified(expr);
            }
        }
        if (isMutator) {
            System.out.println("is Mutator!");
        }
    }

    private boolean handleMethodExpression(MethodCallExpr call) {
        boolean isAssert = false;
        System.out.println("Call: " + call.getNameAsString());
        try {
            // attempt to get the source code for the called method
            // this fails if the source code is not in javaDir (see constructor)
            // Basically, failure indicates external methods
            ResolvedMethodDeclaration declaration = call.resolve();
            if (declaration instanceof JavaParserMethodDeclaration) {
                MethodDeclaration d = ((JavaParserMethodDeclaration) declaration).getWrappedNode();
                // TODO: classify methods
                System.out.println("Lines: " + d.getRange());
                // get calling object name
                String callingObject = call.getScope().get().toString();
                analyzeCalledMethod(d, callingObject);
                d.findAll(Expression.class).forEach(c -> System.out.println("\tCall: " + c));
            } else {
                // I don't know if this will ever happen
                System.out.println("WARN: strange method found: " + declaration);
            }
        } catch(UnsolvedSymbolException e){
            // since junit methods are not part of the source, resolving junit methods will fail
            // then, we can identify asserts by their location in the Assert package (I hope)
            // TODO: there are other test frameworks and folders, e.g., jupiter in junit-5
            if (e.getName().equals("org.junit.Assert")) {
                // TODO: check what the assert statement checks
                isAssert = true;
            }
        }

        return isAssert;
    }

    private List<Node> getNodes(List<CompilationUnit> cus, Class nodeClass) {
        List<Node> res = new LinkedList<Node>();
        cus.forEach(cu -> res.addAll(cu.findAll(nodeClass)));
        return res;
    }
}
