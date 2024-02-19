package se.bth;

import java.io.File;
import java.util.List;
import java.util.Optional;
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
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;

import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserConstructorDeclaration;
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
    
    private List<Node> getNodes(List<CompilationUnit> cus, Class nodeClass) {
        List<Node> res = new LinkedList<Node>();
        cus.forEach(cu -> res.addAll(cu.findAll(nodeClass)));
        return res;
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
                } 
                else if (expr instanceof VariableDeclarationExpr) {
                	handleVariableDeclarationExpression((VariableDeclarationExpr) expr);
                }
                else if (expr instanceof AssignExpr) {
                    Expression variable = ((AssignExpr) expr).getTarget();
                    System.out.println("\tAssigned: " + variable);
                }
                // TODO: think if other expression types are important here as well
            }
            System.out.println("Number of Asserts: " + assertCount);
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
                MethodDeclaration methDeclaration = ((JavaParserMethodDeclaration) declaration).getWrappedNode();
                // TODO: classify methods
                System.out.println("Lines: " + methDeclaration.getRange());
                // get calling object name
                String callingObject = call.getScope().get().toString();
                ArrayList<String> modifiedFields = new ArrayList<String>();
                ArrayList<String> returnedFields = new ArrayList<String>();
                ArrayList<String> retrievedFields = new ArrayList<String>();
                analyzeCalledMethod(methDeclaration, callingObject, modifiedFields, returnedFields, retrievedFields);
                methDeclaration.findAll(Expression.class).forEach(c -> System.out.println("\tCall: " + c));
                methDeclaration.findAll(MethodCallExpr.class).forEach(c -> handleInnerMethodExpression(c, callingObject, modifiedFields, returnedFields, retrievedFields));
                
                System.out.println("\tModified Fields: " + modifiedFields.toString());
        		System.out.println("\tReturned Fields: " + returnedFields.toString());
        		System.out.println("\tRetrieved Fields: " + retrievedFields.toString());
        		
        		//identify method type
                boolean[] isPotentialAccessor = new boolean[2];
                boolean isGet = false;
                boolean isInternalProducer = false;
        		isPotentialAccessor = isPotentialAccessorMethod(methDeclaration);
//        		if (isMutator) {
        		if (modifiedFields.size() > 0) {
                    System.out.println("is Mutator!");            
                }
                else if (isPotentialAccessor[0] == isPotentialGet && returnedFields.size() == 1) {
                	isGet = true;
                	System.out.println("is Get!"); 
                }
                else if (isPotentialAccessor[1] == isPotentialInternalProducer && returnedFields.size() == 0 && retrievedFields.size() > 1) {
                	isInternalProducer = true;
                	System.out.println("is Internal Producer!"); 
                }
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
    
    private void analyzeCalledMethod(MethodDeclaration method, String callingObject, 
        ArrayList<String> modifiedFields, ArrayList<String> returnedFields, ArrayList<String> retrievedFields) {
            
    	// check all field accesses
    	for (FieldAccessExpr expr: method.findAll(FieldAccessExpr.class)) {
    		String fieldAccess = getObjectField(expr, callingObject);
    		
    		updateFieldsAccessListByCalledMethod(expr, fieldAccess, modifiedFields, returnedFields, retrievedFields);        	
    	}
            
    	// check for fields which are accessed simply by name
    	for (NameExpr expr: method.findAll(NameExpr.class)) {
    		String classField = getClassField(expr, callingObject); 
    		updateFieldsAccessListByCalledMethod(expr, classField, modifiedFields, returnedFields, retrievedFields);
        }
    }
    
    private String getObjectField (FieldAccessExpr expr, String callingObject) {
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
        return fieldAccess;
    }
    
    private String getClassField (NameExpr expr, String callingObject) {
    	String classField = null;
        ResolvedValueDeclaration value = expr.resolve();
        if (value.isField()) {
            ResolvedFieldDeclaration field = (ResolvedFieldDeclaration) value;
            ResolvedTypeDeclaration type = field.declaringType();
            classField = type.getQualifiedName() + "." + callingObject + "." + field.getName();
            System.out.println("\tClass field: " + classField);
        }
        return classField;
    }
    
    private void updateFieldsAccessListByCalledMethod(Expression expr, String fieldAccess, ArrayList<String> modifiedFields, ArrayList<String> returnedFields, ArrayList<String> retrievedFields)
    {
    	int fieldInteractionType = this.getFieldInteractionType(expr);
    	if (fieldInteractionType == isModifiedOnly || fieldInteractionType == isModifiedNReturned) {
    		if (!modifiedFields.contains(fieldAccess)) 
    			modifiedFields.add(fieldAccess);
    	}
    	else if (fieldInteractionType == isReturnedOnly || fieldInteractionType == isModifiedNReturned) {
    		if (returnedFields != null && !returnedFields.contains(fieldAccess)) 
    			returnedFields.add(fieldAccess);
    	}
    	if (retrievedFields != null && !retrievedFields.contains(fieldAccess)) 
			retrievedFields.add(fieldAccess);
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
    
    private void handleInnerMethodExpression(MethodCallExpr call, String callingObject, 
        	ArrayList<String> modifiedFields, ArrayList<String> returnedFields, ArrayList<String> retrievedFields) {
        System.out.println("Inner Call: " + call.getNameAsString());
        try {
            ResolvedMethodDeclaration declaration = call.resolve();
            if (declaration instanceof JavaParserMethodDeclaration) {
                MethodDeclaration d = ((JavaParserMethodDeclaration) declaration).getWrappedNode();
                System.out.println("Inner Lines: " + d.getRange());
                // get calling object name
//                if (call.getScope().isPresent())
//                	callingObject = call.getScope().get().toString();                	
                analyzeInnerCalledMethod(d, callingObject, modifiedFields, returnedFields, retrievedFields);
                d.findAll(Expression.class).forEach(c -> System.out.println("\tInner Call: " + c));
                d.findAll(MethodCallExpr.class).forEach(c -> handleInnerMethodExpression(c, callingObject, modifiedFields, returnedFields, retrievedFields));
            } else {
                // I don't know if this will ever happen
                System.out.println("WARN: strange method found: " + declaration);
            }
        } catch(UnsolvedSymbolException e){
            System.out.println(e.getName()); 
        }
    }
    
    private void analyzeInnerCalledMethod(MethodDeclaration method, String callingObject, 
        ArrayList<String> modifiedFields, ArrayList<String> returnedFields, ArrayList<String> retrievedFields) {
    	
    	boolean isGet = false;
    	boolean isInternalProducer = false;
    	boolean[] isPotentialAccessor = new boolean[2];
        	
    	// check all field accesses
    	for (FieldAccessExpr expr: method.findAll(FieldAccessExpr.class)) {
    		String fieldAccess = getObjectField(expr, callingObject);
    		updateFieldsAccessListByCalledMethod(expr, fieldAccess, modifiedFields, returnedFields, retrievedFields);
        }
    	
    	// check for fields which are accessed simply by name
    	for (NameExpr expr: method.findAll(NameExpr.class)) {
    		String classField = getClassField(expr, callingObject); 
    		updateFieldsAccessListByCalledMethod(expr, classField, modifiedFields, returnedFields, retrievedFields);
        }

    	if (modifiedFields != null)
    		System.out.println("\tModified Fields: " + modifiedFields.toString());
        if (returnedFields != null)
        	System.out.println("\tReturned Fields: " + returnedFields.toString());
        if (retrievedFields  != null)
        	System.out.println("\tRetrieved Fields: " + retrievedFields.toString());
        		
        //identify method type
        isPotentialAccessor = isPotentialAccessorMethod(method);
        if (modifiedFields != null && modifiedFields.size() > 0) {
        	System.out.println("\tInner method is Mutator!");            
        }
        else if (isPotentialAccessor[0] == isPotentialGet && returnedFields != null && returnedFields.size() == 1) {
        	isGet = true;
        	System.out.println("\tInner method is Get!"); 
        }
        else if (isPotentialAccessor[1] == isPotentialInternalProducer && retrievedFields  != null && returnedFields != null && returnedFields.size() == 0 && retrievedFields.size() > 1) {
        	isInternalProducer = true;
        	System.out.println("\tInner method is Internal Producer!"); 
        }		
    }
    
    private void handleVariableDeclarationExpression(VariableDeclarationExpr expr) {
    	for (VariableDeclarator varDeclarator: expr.findAll(VariableDeclarator.class)) {
    		String varName = varDeclarator.getNameAsString();
    		Expression initializer = varDeclarator.getInitializer().get();
    		if (initializer instanceof ObjectCreationExpr) {
    			ObjectCreationExpr constructorCall = (ObjectCreationExpr) initializer;
                ResolvedConstructorDeclaration declaration = constructorCall.resolve();
                String initializedObj = declaration.getQualifiedName().substring(0, declaration.getQualifiedName().lastIndexOf(".")) + "." + varName;
                System.out.println("\tInitialized object: " + initializedObj);
                
                if (declaration instanceof JavaParserConstructorDeclaration) {
                    ConstructorDeclaration constrDeclaration = ((JavaParserConstructorDeclaration) declaration).getWrappedNode();
                	System.out.println("Lines: " + constrDeclaration.getRange());
                    ArrayList<String> initializedFields = new ArrayList<String>();
                    analyzeCalledConstructor(constrDeclaration, varName, initializedFields); // TODO: not sure how it works when replacing callingObj with initializedObj
                    constrDeclaration.findAll(Expression.class).forEach(c -> System.out.println("\tExpr: " + c));
                    constrDeclaration.findAll(MethodCallExpr.class).forEach(c -> handleInnerMethodExpression(c, varName, initializedFields, null, null));
                    constrDeclaration.findAll(ObjectCreationExpr.class).forEach(c -> handleInnerConstructorCallInConstructor(c, initializedFields));constrDeclaration.findAll(Expression.class).forEach(c -> System.out.println("\tCall: " + c));
                    
                    System.out.println("\tInitialized Fields: " + initializedFields.toString());    	                              
                }
    	    }
    	}
    }  
    
    private void analyzeCalledConstructor(ConstructorDeclaration constrDeclaration, String initializedObj, ArrayList<String> initializedFields) {
        // check all field accesses
    	for (FieldAccessExpr expr: constrDeclaration.findAll(FieldAccessExpr.class)) {
    		String fieldAccess = getObjectField(expr, initializedObj);
    		updateFieldsAccessListByCalledMethod(expr, fieldAccess, initializedFields, null, null);        	
        }
            
    	// check for fields which are accessed simply by name
    	for (NameExpr expr: constrDeclaration.findAll(NameExpr.class)) {
    		String classField = getClassField(expr, initializedObj); 
    		updateFieldsAccessListByCalledMethod(expr, classField, initializedFields, null, null);
    	}
    }
    
    private void handleInnerConstructorCallInConstructor(ObjectCreationExpr constrDeclaration, ArrayList<String> initializedFields) {
    	
    }              
}


