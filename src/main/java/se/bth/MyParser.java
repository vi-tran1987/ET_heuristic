package se.bth;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import java.io.IOException;
import java.util.List;
import java.util.LinkedList;
import java.util.stream.Collectors;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
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
import com.github.javaparser.resolution.types.ResolvedPrimitiveType;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserConstructorDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserVariableDeclaration;
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
        // Analyse only methods, which are annotated with 'Test'
        if (method.getAnnotationByName("Test").isPresent()) {
            System.out.println(method.getNameAsString());
            boolean isEagerTest = false;
            ArrayList<MethodCall> methodCalls = new ArrayList<MethodCall>();
//            ArrayList<MethodCallExpr> analysedMethodCallExprs = new ArrayList<MethodCallExpr>(); //might need to use it later to avoid double work
           
            for (Expression expr: method.findAll(Expression.class)) {
            	if (expr instanceof VariableDeclarationExpr) {
            		ArrayList<MethodCall> mCalls = handleVariableDeclarationExpression((VariableDeclarationExpr) expr);
            		MethodCall.addMultipleNewMethodCall(methodCalls, mCalls);
                }
            	else if (expr instanceof MethodCallExpr) { //&& !analysedMethodCallExprs.contains(expr)            		
                	MethodCall methCall = handleMethodExpression((MethodCallExpr) expr, methodCalls);                	
                	MethodCall.addNewMethodCall(methodCalls, methCall);
                } 
                else if (expr instanceof AssignExpr) {
                	MethodCall methCall = handleAssignExpr((AssignExpr) expr);
                	MethodCall.addNewMethodCall(methodCalls, methCall);                    
                }
//                else if (!(expr instanceof MarkerAnnotationExpr)) {
//                	throw new RuntimeException ("Unknown expression when processing method! :" + expr);
//                }
                // TODO: think if other expression types are important here as well
            }

            for (MethodCall c : methodCalls) {
            	System.out.println("--------------------------------------------------");
            	System.out.println(c.toString());            	          		
            }
            
            // check if the test case is an Eager Test or not
            isEagerTest = isEagerTest(methodCalls);
            System.out.println("--------------------------------------------------");
            if (isEagerTest)
            	System.out.println("The test case: " + method.getNameAsString() + " is an Eager Test");  
            else
            	System.out.println("The test case: " + method.getNameAsString() + " is NOT an Eager Test");  
        }
    }

    private boolean isEagerTest(ArrayList<MethodCall> mCalls) {
    	ArrayList<MethodCall> assertStmts = MethodCall.getMethodCallByType(MethodCall.ASSERT_STMT, mCalls);
    	ArrayList<MethodCall> allVerifiedMethCalls = new ArrayList<MethodCall>();
    	for (MethodCall assertStmt : assertStmts) {
    		ArrayList<VerifiedInformation> allVerfiedInfo = assertStmt.getVerifiedInfo();
    		ArrayList<MethodCall> allVerifiedMethCallsPerAssertStmt = new ArrayList<MethodCall>();
    		for (VerifiedInformation verifiedInfo : allVerfiedInfo) {
    			MethodCall verifiedMC = verifiedInfo.getVerifiedMethodCall();
    			MethodCall.addNewMethodCall(allVerifiedMethCallsPerAssertStmt, verifiedMC);
    			if (allVerifiedMethCallsPerAssertStmt.size() == 2)
    				return true;
    		}
    		MethodCall.addMultipleNewMethodCall(allVerifiedMethCalls, allVerifiedMethCallsPerAssertStmt);
    		if (allVerifiedMethCalls.size() == 2)
				return true;
    	}
    	return false;
    }
    
    
     private MethodCall handleAssignExpr(AssignExpr expr) {
    	MethodCall methCall = new MethodCall();
        Expression valueExpr = expr.getValue();
        Expression targetExpr = expr.getTarget();
        String targetVar = targetExpr.toString();
        if (valueExpr instanceof MethodCallExpr) {
			methCall = handleMethodExpression((MethodCallExpr) valueExpr, null);    			
			methCall.setReturnedValue(targetVar);
			System.out.println("\tReturn value: " + methCall.getReturnedValue());
		} 
		else if (valueExpr instanceof ObjectCreationExpr) {
			methCall = handleConstructorCall((ObjectCreationExpr) valueExpr, targetVar);
	    }    	
    	return methCall;
    }
    
    private ArrayList<MethodCall> handleVariableDeclarationExpression(VariableDeclarationExpr expr) {
        ArrayList<MethodCall> methodCalls = new ArrayList<MethodCall>();
    	for (VariableDeclarator varDeclarator: expr.findAll(VariableDeclarator.class)) {
    		String varName = varDeclarator.getNameAsString();
    		Expression initializer = varDeclarator.getInitializer().get();
    		
    		if (initializer instanceof MethodCallExpr) {
    			MethodCall methCall = handleMethodExpression((MethodCallExpr) initializer, null);    			
    			methCall.setReturnedValue(varName);
    			System.out.println("\tReturn value: " + methCall.getReturnedValue());
    			MethodCall.addNewMethodCall(methodCalls, methCall);
    		} 
    		else if (initializer instanceof ObjectCreationExpr) {
    			MethodCall methCall = handleConstructorCall((ObjectCreationExpr) initializer, varName);
    			MethodCall.addNewMethodCall(methodCalls, methCall);
    	    }
    	}
    	return methodCalls;
    }  
    
    private MethodCall handleMethodExpression(MethodCallExpr call, ArrayList<MethodCall> methodCalls) {
        MethodCall methCall = new MethodCall();
        methCall.setMethodCallName(call.getTokenRange().get().toString());
        methCall.setPosition(getExpressionPosition(call));
        System.out.println("Call: " + methCall.getMethodCallName());
        boolean isFromSuperConstructor = false;
        try {
            // attempt to get the source code for the called method
            // this fails if the source code is not in javaDir (see constructor)
            // Basically, failure indicates external methods
            ResolvedMethodDeclaration declaration = call.resolve();
            if (declaration instanceof JavaParserMethodDeclaration) {
                MethodDeclaration methDeclaration = ((JavaParserMethodDeclaration) declaration).getWrappedNode();
                // TODO: classify methods
//                System.out.println("Lines: " + methDeclaration.getRange());
                // get calling object name
                String callingObject = call.getScope().get().toString();
                ArrayList<String> modifiedFields = new ArrayList<String>();
                ArrayList<String> returnedFields = new ArrayList<String>();
                ArrayList<String> retrievedFields = new ArrayList<String>();
                analyzeCalledMethod(methDeclaration, callingObject, modifiedFields, returnedFields, retrievedFields, isFromSuperConstructor);
//                methDeclaration.findAll(Expression.class).forEach(c -> System.out.println("\tCall: " + c));
                methDeclaration.findAll(MethodCallExpr.class).forEach(c -> handleInnerMethodExpression(c, callingObject, modifiedFields, returnedFields, retrievedFields, isFromSuperConstructor));
                

        		methCall.setModifiedFields(modifiedFields);
        		methCall.setRetrievedFields(retrievedFields);
        		methCall.setReturnedFields(returnedFields);
                System.out.println("\tModified Fields: " + methCall.getModifiedFields().toString());
        		System.out.println("\tReturned Fields: " + methCall.getReturnedFields().toString());
        		System.out.println("\tRetrieved Fields: " + methCall.getRetrievedFields().toString());        		
        		
        		//identify method type
                boolean[] isPotentialAccessor = new boolean[2];
                boolean isGet = false;
                boolean isInternalProducer = false;
        		isPotentialAccessor = isPotentialAccessorMethod(methDeclaration);
//        		if (isMutator) {
        		if (modifiedFields.size() > 0) {
                    System.out.println("\tis Mutator!"); 
                    methCall.setMethodType(MethodCall.MUTATOR);
                }
                else if (isPotentialAccessor[0] == isPotentialGet && returnedFields.size() == 1) {
                	isGet = true;
                	System.out.println("\tis Get!"); 
                	methCall.setMethodType(MethodCall.GET);
                }
                else if (isPotentialAccessor[1] == isPotentialInternalProducer && returnedFields.size() == 0 && retrievedFields.size() > 1) {
                	isInternalProducer = true;
                	System.out.println("\tis Internal Producer!"); 
                	methCall.setMethodType(MethodCall.INTERNAL_PRODUCER);
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
//                isAssert = true;
                methCall.setMethodType(MethodCall.ASSERT_STMT);
                ArrayList<VerifiedInformation> verifiedInfo = handleAssertStatement(call, methodCalls);
                methCall.setVerifiedInfo(verifiedInfo);
                System.out.println("Assert stmt: " + methCall.getMethodCallName() + " --- Verified Info: " + methCall.getVerifiedInfo());      	
            }
        }
        return methCall;
    }
    
    private ArrayList<VerifiedInformation> handleAssertStatement(MethodCallExpr assertCall, ArrayList<MethodCall> methodCalls) {
    	NodeList<Expression> arguments = assertCall.getArguments();
    	MethodCallPosition mcPosition = getExpressionPosition(assertCall);
    	ArrayList<VerifiedInformation> allVerifiedInfo = new ArrayList<VerifiedInformation>();    	
    	for (Expression argument : arguments) { 
			// TODO: handle argument to assertTrue(a.equals(b))
    		if (argument instanceof NameExpr) {
    			VerifiedInformation verifiedInfo = handleArgumentfromAssertStmt(argument, methodCalls, mcPosition);
    			allVerifiedInfo.add(verifiedInfo);
    		}
    		else if (argument instanceof MethodCallExpr) {
    			VerifiedInformation verifiedInfo = handleArgumentfromAssertStmt(argument, methodCalls, mcPosition);
    			allVerifiedInfo.add(verifiedInfo);
    		}
    		else if (argument instanceof BinaryExpr) {
    			BinaryExpr expr = (BinaryExpr) argument;    			
    			VerifiedInformation verifiedInfo_left = handleArgumentfromAssertStmt(expr.getLeft(), methodCalls, mcPosition);
    			if (!verifiedInfo_left.getVerifiedInfo().isEmpty())
    				allVerifiedInfo.add(verifiedInfo_left);
    			VerifiedInformation verifiedInfo_right  = handleArgumentfromAssertStmt(expr.getRight(), methodCalls, mcPosition);
    			if (!verifiedInfo_right.getVerifiedInfo().isEmpty())
    				allVerifiedInfo.add(verifiedInfo_right);
    		}
    		else {
    			System.out.println(argument.toString());
    		}
		}
    	return allVerifiedInfo;
    }
    
    private VerifiedInformation handleArgumentfromAssertStmt(Expression expr, ArrayList<MethodCall> methodCalls, 
    														 MethodCallPosition mcPosition) 
    {    	
    	VerifiedInformation verifiedInfo = new VerifiedInformation();
    	if (expr instanceof NameExpr) {		
    		//get full name of the verified info
    		NameExpr nameExpr = (NameExpr) expr;
    		String verifiedName = getVerifiedName(nameExpr);
    		
    		// search for the source method call of the verifiedName
        	MethodCall sourceMC = getSourceMethodCallOfVerifiedName(verifiedName, mcPosition, methodCalls);
        	verifiedInfo.setSourceMethodCall(sourceMC);

    		// search for the verified method call
    		if (verifiedInfo.getSourceMethodCall().getMethodType().equals(MethodCall.MUTATOR) ||
    			verifiedInfo.getSourceMethodCall().getMethodType().equals(MethodCall.CREATIONAL))
    		{	
    			verifiedInfo.setVerifiedMethodCall(verifiedInfo.getSourceMethodCall());
    			verifiedInfo.getVerifiedInfo().add(verifiedName);
    			verifiedInfo.getVerifiedInfo().addAll(verifiedInfo.getSourceMethodCall().getModifiedFields());
    		}
    		else if (verifiedInfo.getSourceMethodCall().getMethodType().equals(MethodCall.INTERNAL_PRODUCER)) {
    			verifiedInfo.setVerifiedMethodCall(verifiedInfo.getSourceMethodCall());
        		verifiedInfo.getVerifiedInfo().add(verifiedName);
        	}
    		else if (verifiedInfo.getSourceMethodCall().getMethodType().equals(MethodCall.GET) ||
    				 verifiedInfo.getSourceMethodCall().getMethodType().equals(MethodCall.EXTERNAL_PRODUCER)) 
    		{    			
    			ArrayList<String> retrievedFields = verifiedInfo.getSourceMethodCall().getRetrievedFields();
        		verifiedInfo.getVerifiedInfo().addAll(retrievedFields); //TODO: assumption: a get method has only 1 retrieved field!
        		MethodCall verifiedMethodCall = getVerifiedMethodCall(verifiedInfo, methodCalls);
				verifiedInfo.setVerifiedMethodCall(verifiedMethodCall);
    		}    		
    	}
    	else if (expr instanceof MethodCallExpr) {
			MethodCallExpr methCallExpr = (MethodCallExpr) expr;
			MethodCall methCall = handleMethodExpression(methCallExpr, null);
    		verifiedInfo.setSourceMethodCall(methCall);
    		
			String methType = methCall.getMethodType();
			if (methType.equals(MethodCall.CREATIONAL) || methType.equals(MethodCall.MUTATOR)) {
				verifiedInfo.setVerifiedMethodCall(methCall);
				verifiedInfo.getVerifiedInfo().addAll(methCall.getModifiedFields());

				methCall.setReturnedValue("return value of " + methCall.getMethodCallName());
				MethodCall.addNewMethodCall(methodCalls, methCall);				
				verifiedInfo.setVerifiedMethodCall(methCall);
				verifiedInfo.getVerifiedInfo().add(methCall.getReturnedValue());
			}
			else if (methType.equals(MethodCall.GET) || methType.equals(MethodCall.EXTERNAL_PRODUCER)) {
				verifiedInfo.getVerifiedInfo().addAll(methCall.getRetrievedFields());
				MethodCall verifiedMethodCall = getVerifiedMethodCall(verifiedInfo, methodCalls);
				verifiedInfo.setVerifiedMethodCall(verifiedMethodCall);
				
			}
			else if (methType.equals(MethodCall.INTERNAL_PRODUCER)) {
				methCall.setReturnedValue("return value of " + methCall.getMethodCallName()); // as the return value cannot be calculated on the spot!
				MethodCall.addNewMethodCall(methodCalls, methCall);				
				verifiedInfo.setVerifiedMethodCall(methCall);
				verifiedInfo.getVerifiedInfo().add(methCall.getReturnedValue());
			}				
		}
    	return verifiedInfo;
    }
    
    private MethodCall getVerifiedMethodCall (VerifiedInformation verifiedInfo, ArrayList<MethodCall> methodCalls) {
    	ArrayList<MethodCall> methCallsAboveSourceMethCall = MethodCall.getMethodCallAboveAPosition(verifiedInfo.getSourceMethodCall().getPosition(), methodCalls);
		ArrayList<String> retrievedFields = verifiedInfo.getSourceMethodCall().getRetrievedFields();
		
		for (String retrievedField : retrievedFields) {
			for (int i = methCallsAboveSourceMethCall.size()-1; i >= 0; i--) {
    			MethodCall mCall = methCallsAboveSourceMethCall.get(i);
    			if (mCall.getMethodType().equals(MethodCall.MUTATOR) ||
    				mCall.getMethodType().equals(MethodCall.CREATIONAL)) 
    			{
    				ArrayList<String> modifiedFields = mCall.getModifiedFields();
	    			if (modifiedFields.contains(retrievedField)) {
	    				return mCall; //TODO: assumption: a get method has only 1 retrieved field!
	    			}
    			}
    			    				
    		}
		}
    	return null;
    }
    
    private MethodCall getSourceMethodCallOfVerifiedName(String verifiedName, MethodCallPosition mcPosition, ArrayList<MethodCall> methodCalls) {
    	ArrayList<MethodCall> methCallsAboveAssertStmt = MethodCall.getMethodCallAboveAPosition(mcPosition, methodCalls);
		for (int i = methCallsAboveAssertStmt.size()-1; i >= 0; i--) {
			MethodCall mCall = methCallsAboveAssertStmt.get(i);
			String returnValue = mCall.getReturnedValue();
			if (verifiedName.equals(returnValue)) {
				return mCall;
			}    				
		}
		return null;
    }
    
    private String getVerifiedName(NameExpr nameExpr) {
    	String verifiedName = "";
		ResolvedValueDeclaration valueDeclaration = nameExpr.resolve();
		
		ResolvedType type = valueDeclaration.getType();
		if (type instanceof ResolvedPrimitiveType) {
			verifiedName = nameExpr.getName().getIdentifier();
		}
		else if (type instanceof ResolvedReferenceType) {    			
			ResolvedReferenceType referenceType = (ResolvedReferenceType)type;    			
			verifiedName = referenceType.getQualifiedName() + "." + nameExpr.getName().getIdentifier();
		}
		return verifiedName;
    }
    
    private void analyzeCalledMethod(MethodDeclaration method, String callingObject, ArrayList<String> modifiedFields,
    		ArrayList<String> returnedFields, ArrayList<String> retrievedFields, boolean isFromSuperConstructor) {
            
    	// check all field accesses
    	for (FieldAccessExpr expr: method.findAll(FieldAccessExpr.class)) {
    		String fieldAccess = getObjectField(expr, callingObject, isFromSuperConstructor);
    		if (fieldAccess != null)
    			updateFieldsAccessListByCalledMethod(expr, fieldAccess, modifiedFields, returnedFields, retrievedFields);        	
    	}
            
    	// check for fields which are accessed simply by name
    	for (NameExpr expr: method.findAll(NameExpr.class)) {
    		String classField = getClassFieldByNameOnly(expr, callingObject, isFromSuperConstructor); 
    		if (classField != null)
    			updateFieldsAccessListByCalledMethod(expr, classField, modifiedFields, returnedFields, retrievedFields);
        }
    }
    
    private String getObjectField (FieldAccessExpr expr, String callingObject, boolean isFromSuperConstructor) {
    	String fieldAccess = null;  
    	if (isFromSuperConstructor) {
    		fieldAccess = callingObject + "." + expr.getNameAsString();
    	}
    	else {
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
    	}
    		
        
        return fieldAccess;
    }
    
    private String getClassFieldByNameOnly (NameExpr expr, String callingObject, boolean isFromSuperConstructor) {
    	String classField = null;
    	if (isFromSuperConstructor) {
    		classField = callingObject + "." + expr.getNameAsString();
    	}
    	else {
    		ResolvedValueDeclaration value = expr.resolve();
            if (value.isField()) {
                ResolvedFieldDeclaration field = (ResolvedFieldDeclaration) value;
                ResolvedTypeDeclaration type = field.declaringType();
                if (callingObject == null)
                	classField = type.getQualifiedName() + "." + field.getName();
                else
                	classField = type.getQualifiedName() + "." + callingObject + "." + field.getName();
                System.out.println("\tClass field: " + classField);
            }
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
//        	System.out.println("\tparentNode: " + originalExpr.getParentNode().toString());
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
    
    private void handleInnerMethodExpression(MethodCallExpr call, String callingObject,	ArrayList<String> modifiedFields,
    		ArrayList<String> returnedFields, ArrayList<String> retrievedFields, boolean isFromSuperConstructor) {
        System.out.println("Inner Call: " + call.getNameAsString());
        try {
            ResolvedMethodDeclaration declaration = call.resolve();
            if (declaration instanceof JavaParserMethodDeclaration) {
                MethodDeclaration d = ((JavaParserMethodDeclaration) declaration).getWrappedNode();
//                System.out.println("Inner Lines: " + d.getRange());
                // get calling object name
//                if (call.getScope().isPresent())
//                	callingObject = call.getScope().get().toString();                	
                analyzeInnerCalledMethod(d, callingObject, modifiedFields, returnedFields, retrievedFields, isFromSuperConstructor);
//                d.findAll(Expression.class).forEach(c -> System.out.println("\tInner Call: " + c));
                d.findAll(MethodCallExpr.class).forEach(c -> handleInnerMethodExpression(c, callingObject, modifiedFields, returnedFields, retrievedFields, isFromSuperConstructor));
            } else {
                // I don't know if this will ever happen
                System.out.println("WARN: strange method found: " + declaration);
            }
        } catch(UnsolvedSymbolException e){
            System.out.println(e.getName()); 
        }
    }
    
    private void analyzeInnerCalledMethod(MethodDeclaration method, String callingObject, ArrayList<String> modifiedFields,
    		ArrayList<String> returnedFields, ArrayList<String> retrievedFields, boolean isFromSuperConstructor) {
    	
    	boolean isGet = false;
    	boolean isInternalProducer = false;
    	boolean[] isPotentialAccessor = new boolean[2];
        	
    	// check all field accesses
    	for (FieldAccessExpr expr: method.findAll(FieldAccessExpr.class)) {
    		String fieldAccess = getObjectField(expr, callingObject, isFromSuperConstructor);
    		updateFieldsAccessListByCalledMethod(expr, fieldAccess, modifiedFields, returnedFields, retrievedFields);
        }
    	
    	// check for fields which are accessed simply by name
    	for (NameExpr expr: method.findAll(NameExpr.class)) {
    		String classField = getClassFieldByNameOnly(expr, callingObject, isFromSuperConstructor); 
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
        
    private MethodCall handleConstructorCall(ObjectCreationExpr constructorCall, String varName) {
    	MethodCall methCall = new MethodCall();
    	methCall.setMethodType(MethodCall.CREATIONAL);    	
        methCall.setMethodCallName(constructorCall.getTokenRange().get().toString());
        methCall.setPosition(getExpressionPosition(constructorCall));
        System.out.println("Call: " + methCall.getMethodCallName());
        
//    	ObjectCreationExpr constructorCall = (ObjectCreationExpr) initializer;
        ResolvedConstructorDeclaration declaration = constructorCall.resolve();
        String initializedObj = declaration.getQualifiedName().substring(0, declaration.getQualifiedName().lastIndexOf(".")) + "." + varName;
        System.out.println("\tInitialized object: " + initializedObj);
        methCall.setReturnedValue(initializedObj);

        if (declaration instanceof JavaParserConstructorDeclaration) {
        	boolean isFromSuperConstructor = false;
            ConstructorDeclaration constrDeclaration = ((JavaParserConstructorDeclaration) declaration).getWrappedNode();
//        	System.out.println("Lines: " + constrDeclaration.getRange());
            ArrayList<String> modifiedFields = new ArrayList<String>();
            analyzeCalledConstructor(constrDeclaration, varName, modifiedFields, isFromSuperConstructor); // TODO: not sure how it works when replacing callingObj with initializedObj
//            constrDeclaration.findAll(Expression.class).forEach(c -> System.out.println("\tExpr: " + c));
//            constrDeclaration.findAll(MethodCallExpr.class).forEach(c -> handleInnerMethodExpression(c, varName, modifiedFields, null, null, false)); //hardcode isFromSuperConstructor = false
            for (MethodCallExpr c : constrDeclaration.findAll(MethodCallExpr.class)) {
            	handleInnerMethodExpression(c, varName, modifiedFields, null, null, isFromSuperConstructor);
            }
            	
            
            //find a call to super or this in the constructor
            for (ExplicitConstructorInvocationStmt s: constrDeclaration.findAll(ExplicitConstructorInvocationStmt.class)) {
//            	System.out.println (s.getTokenRange().get().toString());
            	if (s.getTokenRange().get().toString().contains("super"))
            		isFromSuperConstructor = true;
            	else
            		isFromSuperConstructor = false;
            	ResolvedConstructorDeclaration resolvedDeclaration = s.resolve();
            	if (resolvedDeclaration instanceof JavaParserConstructorDeclaration) {
                    ConstructorDeclaration resolvedConstrDeclaration = ((JavaParserConstructorDeclaration) resolvedDeclaration).getWrappedNode();
//                 	System.out.println("Lines: " + resolvedConstrDeclaration.getRange());
                 	analyzeCalledConstructor(resolvedConstrDeclaration, initializedObj, modifiedFields, isFromSuperConstructor); // TODO: not sure how it works when replacing callingObj with initializedObj
//                 	resolvedConstrDeclaration.findAll(Expression.class).forEach(c -> System.out.println("\tExpr: " + c));
//                 	resolvedConstrDeclaration.findAll(MethodCallExpr.class).forEach(c -> handleInnerMethodExpression(c, varName, modifiedFields, null, null, isFromSuperConstructor));           
                 	for (MethodCallExpr c : resolvedConstrDeclaration.findAll(MethodCallExpr.class)) {
                 		handleInnerMethodExpression(c, varName, modifiedFields, null, null, isFromSuperConstructor);
                 	}
            	}
            }
            methCall.setModifiedFields(modifiedFields);
            System.out.println("\tModified Fields: " + methCall.getModifiedFields()); 
        }
        return methCall;
    }
    
    private void analyzeCalledConstructor(ConstructorDeclaration constrDeclaration, String initializedObj, ArrayList<String> modifiedFields, boolean isSuperConstructor) {
        // check all field accesses
    	for (FieldAccessExpr expr: constrDeclaration.findAll(FieldAccessExpr.class)) {
    		String fieldAccess = getObjectField(expr, initializedObj, isSuperConstructor);
    		if (fieldAccess != null)
    			updateFieldsAccessListByCalledMethod(expr, fieldAccess, modifiedFields, null, null);        	
        }
            
    	// check for fields which are accessed simply by name
    	for (NameExpr expr: constrDeclaration.findAll(NameExpr.class)) {  
    		String classField = getClassFieldByNameOnly(expr, initializedObj, isSuperConstructor); 
    		if (classField != null)
    			updateFieldsAccessListByCalledMethod(expr, classField, modifiedFields, null, null);
    	}
    }

    
    private MethodCallPosition getExpressionPosition(Expression expr) {
    	int beginLine = expr.getRange().get().begin.line;
    	int endLine = expr.getRange().get().end.line;
    	return new MethodCallPosition(beginLine, endLine);
    }
}


