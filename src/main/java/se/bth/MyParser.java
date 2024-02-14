package se.bth;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

import java.io.IOException;
import java.util.List;
import java.util.LinkedList;
import java.util.stream.Collectors;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
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
    
    private final int undentifiedFieldInteractionType = 0;
    private final int isModifiedOnly = 1;
    private final int isReturnedOnly = 2;
    private final int isModifiedNReturned = 3;
    private final int isInitialized = 4;
    
    private final boolean isPotentialGet = true;
    private final boolean isPotentialInternalProducer = true;

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

    private int getFieldInteractionType (Expression originalExpr) {
    	boolean fromReturnStmt = false;
        Expression targetExpression = null;
        Expression expr = null;
        if (originalExpr.getParentNode().isPresent()) {
        	System.out.println("\tparentNode: " + originalExpr.getParentNode().toString());
        	Node parentNode = originalExpr.getParentNode().get();
        	if (parentNode instanceof ReturnStmt) {
        		expr = originalExpr;
        		fromReturnStmt = true;
        	}
        	else if (parentNode instanceof Expression) {
        		expr = (Expression) parentNode;
        	}
        	
//        		Expression parentExpr = (Expression) parentNode;
            if (expr instanceof UnaryExpr) {
            	targetExpression = ((UnaryExpr) expr).getExpression();
            	if(originalExpr.equals(targetExpression)) {
            		if (fromReturnStmt)
            			return isModifiedNReturned;
            		else
            			return isModifiedOnly;
            	}            		
            } else if (expr instanceof AssignExpr) {
                targetExpression = ((AssignExpr) expr).getTarget();
                if (originalExpr.equals(targetExpression))
                	return isModifiedOnly;
            }
            if (fromReturnStmt) {
            	return isReturnedOnly; //meaning simple return statement like "return attrA;"
            }    
//            else
//            	return isRetrievedOnly; // meaning without returning, i.e., used by internal producer method
        }
        return undentifiedFieldInteractionType;
    }

    private boolean updateFieldsAccessListByCalledMethod(Expression expr, String fieldAccess, ArrayList<String> modifiedFields, ArrayList<String> returnedFields, ArrayList<String> retrievedFields)
    {
    	boolean isMutator = false;
    	int fieldInteractionType = this.getFieldInteractionType(expr);
    	if (fieldInteractionType == isModifiedOnly || fieldInteractionType == isModifiedNReturned) {
    		isMutator |= true;
    		if (!modifiedFields.contains(fieldAccess)) 
    			modifiedFields.add(fieldAccess);
    	}
    	else if (fieldInteractionType == isReturnedOnly || fieldInteractionType == isModifiedNReturned) {
    		if (!returnedFields.contains(fieldAccess)) 
    			returnedFields.add(fieldAccess);
    	}
    	if (!retrievedFields.contains(fieldAccess)) 
			retrievedFields.add(fieldAccess);
    	
    	return isMutator;
    }
    
    private boolean[] isPotentialAccessorMethod (MethodDeclaration method) {
    	int numStmts = 0;
        boolean hasReturnStmt = false;
        boolean[] returnValues = new boolean[2];
        for (Statement stmt: method.findAll(Statement.class)) {
        	numStmts++;
        	if (stmt.isReturnStmt()) {
        		hasReturnStmt = true;
        	}
        }
        if (numStmts == 2 && hasReturnStmt) //1st stmt is the block stmt of the method
        	returnValues[0] = isPotentialGet;
        if (hasReturnStmt)
        	returnValues[1] = isPotentialInternalProducer;
        
        return returnValues;
    }
    
    
    private void analyzeCalledMethod(MethodDeclaration method, String callingObject) {
    	
        boolean isMutator = false;
        boolean isGet = false;
        boolean isInternalProducer = false;
        boolean[] isPotentialAccessor = new boolean[2];
        
        ArrayList<String> modifiedFields = new ArrayList<String>();
        ArrayList<String> returnedFields = new ArrayList<String>();
        ArrayList<String> retrievedFields = new ArrayList<String>();
                
        // check all field accesses
        for (FieldAccessExpr expr: method.findAll(FieldAccessExpr.class)) {
        	String fieldAccess = null;       	        	
            Expression scope = expr.getScope();
            if (scope instanceof ThisExpr) {
                ThisExpr thisExpr = (ThisExpr) scope;
                ResolvedTypeDeclaration type = thisExpr.resolve();
                fieldAccess = type.getQualifiedName() + "." + callingObject + "." + expr.getNameAsString();
                System.out.println("\tField Access: " + fieldAccess);
            } else if (scope instanceof NameExpr) {
                NameExpr nameExpr = (NameExpr) scope;
                ResolvedValueDeclaration value = nameExpr.resolve();
                ResolvedReferenceType type = value.getType().asReferenceType();
                fieldAccess = type.getQualifiedName() + "." + value.getName() + "." + callingObject + "."
                			+ expr.getNameAsString();
                System.out.println("\tField Access: " + fieldAccess);
            } else {
                throw new RuntimeException("Unknown instance for: " + scope);
            }

            isMutator |= updateFieldsAccessListByCalledMethod(expr, fieldAccess, modifiedFields, returnedFields, retrievedFields);        	
        }
        
        // check for fields which are accessed simply by name
        for (NameExpr expr: method.findAll(NameExpr.class)) {
        	String classField = null;
            ResolvedValueDeclaration value = expr.resolve();
            if (value.isField()) {
                ResolvedFieldDeclaration field = (ResolvedFieldDeclaration) value;
                ResolvedTypeDeclaration type = field.declaringType();
                classField = type.getQualifiedName() + "." + callingObject + "." + field.getName();
                System.out.println("\tClass field: " + classField);

                isMutator |= updateFieldsAccessListByCalledMethod(expr, classField, modifiedFields, returnedFields, retrievedFields);
            }
        }

		System.out.println("\tModified Fields: " + modifiedFields.toString());
		System.out.println("\tReturned Fields: " + returnedFields.toString());
		System.out.println("\tRetrieved Fields: " + retrievedFields.toString());
		
		//identify method type
		isPotentialAccessor = isPotentialAccessorMethod(method);
		if (isMutator) {
            System.out.println("is Mutator!");            
        }
        else if (isPotentialAccessor[0] == isPotentialGet && returnedFields.size() == 1) {
        	isGet = true;
        	System.out.println("is Get!"); 
        }
        else if (isPotentialAccessor[0] == isPotentialInternalProducer && returnedFields.size() == 0 && retrievedFields.size() > 1) {
        	isInternalProducer = true;
        	System.out.println("is Internal Producer!"); 
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


