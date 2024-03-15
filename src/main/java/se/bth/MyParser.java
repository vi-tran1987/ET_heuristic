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
import com.github.javaparser.Position;
import com.github.javaparser.ParseResult;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
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
import com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedArrayType;
import com.github.javaparser.resolution.types.ResolvedPrimitiveType;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserConstructorDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserParameterDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserVariableDeclaration;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionMethodDeclaration;
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
        	System.out.println("--------------------------------------------------");
            System.out.println("TEST CASE: " + method.getNameAsString());
            
            boolean isEagerTest = false;
            ArrayList<MethodCall> methodCalls = new ArrayList<MethodCall>();
//            ArrayList<MethodCallExpr> analysedMethodCallExprs = new ArrayList<MethodCallExpr>(); //might need to use it later to avoid double work
           
            for (Expression expr: method.findAll(Expression.class)) {
            	if (expr instanceof VariableDeclarationExpr) {
            		ArrayList<MethodCall> mCalls = handleVariableDeclarationExpression((VariableDeclarationExpr) expr);
            		MethodCall.addMultipleNewMethodCall(methodCalls, mCalls, true);
                }
            	else if (expr instanceof MethodCallExpr) { //&& !analysedMethodCallExprs.contains(expr)            		
                	MethodCall methCall = handleMethodExpression((MethodCallExpr) expr, methodCalls);                	
                	MethodCall.addNewMethodCall(methodCalls, methCall, true);
                } 
                else if (expr instanceof AssignExpr) {
                	MethodCall methCall = handleAssignExpr((AssignExpr) expr, methodCalls);
                	if (methCall != null)
                		MethodCall.addNewMethodCall(methodCalls, methCall, true);                    
                }
            	// TODO: think if other expression types are important here as well
//                else if (!(expr instanceof MarkerAnnotationExpr)) {
//                	throw new RuntimeException ("Unknown expression when processing method! :" + expr);
//                }
                
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
    			ArrayList<MethodCall> verifiedMCs = verifiedInfo.getVerifiedMethodCalls();
    			MethodCall.addMultipleNewMethodCall(allVerifiedMethCallsPerAssertStmt, verifiedMCs, false);
    			if (allVerifiedMethCallsPerAssertStmt.size() == 2)
    				return true;
    		}
    		MethodCall.addMultipleNewMethodCall(allVerifiedMethCalls, allVerifiedMethCallsPerAssertStmt, false);
    		if (allVerifiedMethCalls.size() == 2)
				return true;
    	}
    	return false;
    }    
    
    private MethodCall handleAssignExpr(AssignExpr expr, ArrayList<MethodCall> methodCalls) {
    	MethodCall methCall = new MethodCall();
        Expression valueExpr = expr.getValue();
        Expression targetExpr = expr.getTarget();
        String targetVar = targetExpr.toString();
        String valueVar = "";
        // get type and name of the target variable if possible
        if (targetExpr instanceof NameExpr)
        	targetVar = getTypeNNameOfNameExpr((NameExpr) targetExpr);        
        else if (targetExpr instanceof ArrayAccessExpr) {
        	ArrayAccessExpr arrayExpr = (ArrayAccessExpr) targetExpr;
        	Expression name = arrayExpr.getName();
        	Expression index = arrayExpr.getIndex();
        	//TODO: assumption: handle only simple cases of array: name is name expression and index is integer
        	if (name instanceof NameExpr && index instanceof IntegerLiteralExpr) {
//        		targetVar = getTypeNNameOfNameExpr((NameExpr) name) + "." + arrayExpr.getIndex();  
//        		targetVar = name.toString() + "." + arrayExpr.getIndex();  
        		targetVar = arrayExpr.toString();
        	}        	    
        }
        
        if (valueExpr instanceof MethodCallExpr) {
			methCall = handleMethodExpression((MethodCallExpr) valueExpr, null);    			
			methCall.setReturnedValue(targetVar);
			System.out.println("\tReturn value: " + methCall.getReturnedValue());
		} 
		else if (valueExpr instanceof ObjectCreationExpr) {
			methCall = handleConstructorCall((ObjectCreationExpr) valueExpr, targetVar);
	    }  
		else if (valueExpr instanceof ArrayAccessExpr) {
			ArrayAccessExpr arrayExpr = (ArrayAccessExpr) valueExpr;
        	Expression name = arrayExpr.getName();
        	Expression index = arrayExpr.getIndex();
        	//TODO: assumption: handle only simple cases of array: name is name expression and index is integer
        	if (name instanceof NameExpr && index instanceof IntegerLiteralExpr) {
        		valueVar = arrayExpr.toString();
        		MethodCallPosition pos = getExpressionPosition(expr);
        		String s = getTypeNNameOfNameExpr((NameExpr) name);
        		s = s.substring(0, s.lastIndexOf(".")) + "." + valueVar;
        		MethodCall mCall = getSourceMethodCallOfVerifiedName (s, pos, methodCalls);
        		if (mCall != null) {
        			updateFieldsDueToArrayAssign (mCall.getModifiedFields(), targetVar, valueVar);
            		updateFieldsDueToArrayAssign (mCall.getRetrievedFields(), targetVar, valueVar);
            		updateFieldsDueToArrayAssign (mCall.getReturnedFields(), targetVar, valueVar);
            		mCall.setReturnedValue(targetVar);
        		}
        	}
        	return null;
		}
    	return methCall;
    }
    
    private ArrayList<MethodCall> handleVariableDeclarationExpression(VariableDeclarationExpr expr) {
        ArrayList<MethodCall> methodCalls = new ArrayList<MethodCall>();
    	for (VariableDeclarator varDeclarator: expr.findAll(VariableDeclarator.class)) {
    		String varName = varDeclarator.getNameAsString();
//    		ResolvedValueDeclaration reVarDeclarator = varDeclarator.resolve();
//    		varName = getTypeNNameOfVariable(reVarDeclarator, varName);   		
    		   		
    		Expression initializer = varDeclarator.getInitializer().get();    		
    		if (initializer instanceof MethodCallExpr) {
    			MethodCall methCall = handleMethodExpression((MethodCallExpr) initializer, null);  
    			ResolvedValueDeclaration reVarDeclarator = varDeclarator.resolve();
        		varName = getTypeNNameOfVariable(reVarDeclarator.getType(), varName);
    			methCall.setReturnedValue(varName);
    			System.out.println("\tReturn value: " + methCall.getReturnedValue());
    			MethodCall.addNewMethodCall(methodCalls, methCall, true);
    		} 
    		else if (initializer instanceof ObjectCreationExpr) {
    			MethodCall methCall = handleConstructorCall((ObjectCreationExpr) initializer, varName);
    			MethodCall.addNewMethodCall(methodCalls, methCall, true);
    	    }
    		else if (initializer instanceof ArrayCreationExpr) { 
    			MethodCall methCall = handleArrayCreationExpression((ArrayCreationExpr) initializer, null, varName); 
    			ResolvedValueDeclaration reVarDeclarator = varDeclarator.resolve();
        		varName = getTypeNNameOfVariable(reVarDeclarator.getType(), varName);
    			methCall.setReturnedValue(varName);
    			System.out.println("\tReturn value: " + methCall.getReturnedValue());
    			MethodCall.addNewMethodCall(methodCalls, methCall, true);
    		}
    	}
    	return methodCalls;
    }  
    
    private MethodCall handleArrayCreationExpression (ArrayCreationExpr arrayCreationExpr, ArrayList<MethodCall> methodCalls, String varName) {
    	MethodCall methCall = new MethodCall();    	
        if (arrayCreationExpr.getElementType().isClassOrInterfaceType()) {
        	methCall.setMethodCallName(arrayCreationExpr.getTokenRange().get().toString());
            methCall.setPosition(getExpressionPosition(arrayCreationExpr));
            methCall.setMethodType(MethodCall.CREATIONAL);
            System.out.println("Call: " + methCall.getMethodCallName());
        }
    	return methCall;
    }
    
    private String getTypeNNameOfArgument (Expression arg) {
    	String typeNName = "";
    	
    	if (arg instanceof NameExpr) {
			typeNName = getTypeNNameOfNameExpr((NameExpr)arg);
		}    			
		else if (arg instanceof MethodCallExpr) {
			MethodCallExpr mcExpr = (MethodCallExpr) arg;
			MethodCall mCall = handleMethodExpression(mcExpr, null);
			// assumption: the method call must return one field only, typically: get()
			if (mCall.getReturnedFields().size() == 1) { 
				typeNName = mCall.getReturnedFields().get(0);
			}			    				
		}
		//TODO: don't have to handle constructor call as an argument as the initialized object cannot be
    	// referred to later on, right?
		else if (arg instanceof ObjectCreationExpr) {
			throw new RuntimeException ("Constructor call as an argument of a normal method call! :" + arg);
		} 
    	return typeNName;
    }
    
    private void updateFieldsDueToArrayAssign(ArrayList<String> fields, String targetVar, String valueVar) {
    	for (int i = 0; i < fields.size(); i++) {
        	String mField = fields.get(i);
        	if (mField.contains(valueVar + ".")) {
    			String arrayPos = valueVar + ".";
    			int p = mField.indexOf(arrayPos) + arrayPos.length();
//    			String updatedMField = mField.substring(0, mField.indexOf(arrayPos)) + "." + targetVar + mField.substring(p+1);
    			String updatedMField = targetVar + "." + mField.substring(p);
    			fields.add(i, updatedMField);
    			fields.remove(i+1);
    		}
        }
    }
    
    private void updateFieldsDueToArgParaChanges(ArrayList<String> fields, ArrayList<ArrayList<String>> argParaTraces, int methLayer) {
    	for (int i = 0; i < fields.size(); i++) {
        	String mField = fields.get(i);
        	for (ArrayList<String> argParaTrace : argParaTraces) {
        		if (mField.contains(argParaTrace.get(methLayer) + ".")) {
        			String para = argParaTrace.get(methLayer) + ".";
        			int pos = mField.indexOf(para) + para.length();
        			String updatedMField = argParaTrace.get(0) + "." + mField.substring(pos);
        			fields.add(i, updatedMField);
        			fields.remove(i+1);
        			break;
        		}
        	}
        }
    }
    
    private MethodCall handleMethodExpression(MethodCallExpr methCallExpr, ArrayList<MethodCall> methodCalls) {
        MethodCall methCall = new MethodCall();
        methCall.setMethodCallName(methCallExpr.getTokenRange().get().toString());
        methCall.setPosition(getExpressionPosition(methCallExpr));
        System.out.println("Call: " + methCall.getMethodCallName());
        boolean isFromSuperConstructor = false;
      	int methodLayer = 0;
        try {
            // attempt to get the source code for the called method
            // this fails if the source code is not in javaDir (see constructor)
            // Basically, failure indicates external methods: assert and external producer
            ResolvedMethodDeclaration declaration = methCallExpr.resolve();
            if (declaration instanceof JavaParserMethodDeclaration) {
                MethodDeclaration methDeclaration = ((JavaParserMethodDeclaration) declaration).getWrappedNode();
                // get the name of the calling object OR class
                String callingObjOrClass = methCallExpr.getScope().get().toString();
//                NameExpr ne = (NameExpr) methCallExpr.getScope().get(); 
//                ResolvedType typeOfCallingObj = ne.calculateResolvedType();
//                boolean isClassName = false;
//                if (typeOfCallingObj.isReferenceType()) {
//                	String s = typeOfCallingObj.describe().substring(typeOfCallingObj.describe().lastIndexOf(".")+1);
//                	if (callingObjOrClass.equals(s))
//                		isClassName = true;
//                }           
                	
                ArrayList<String> modifiedFields = new ArrayList<String>();
                ArrayList<String> returnedFields = new ArrayList<String>();
                ArrayList<String> retrievedFields = new ArrayList<String>();
                collectAllFields(methDeclaration, callingObjOrClass, modifiedFields, returnedFields, retrievedFields, isFromSuperConstructor);
                
                // deal with parameters
                ArrayList<ArrayList<String>> argParaTraces = new ArrayList<ArrayList<String>>();
                if (methDeclaration.getParameters().isNonEmpty()) {
                	// get parameters
        			ArrayList<String> parameters = new ArrayList<String>(); 
                	for (Parameter para : methDeclaration.getParameters()) {
            			ResolvedParameterDeclaration paraDecl = para.resolve();
            			if (paraDecl.getType().isReferenceType()) {
//            				String paraType = paraDecl.describeType();
//            				String paraTypeNName = paraType + "." + para.getNameAsString();
//            				parameters.add(paraTypeNName);
            				String paraName = para.getNameAsString();
            				parameters.add(paraName);
            			}
            			else
            				parameters.add(""); // meaning not an object
                	}
                	
                	for (int i = 0; i < parameters.size(); i++) {
                		String paraName = parameters.get(i);
                		if (!paraName.equals("")) {
                			ArrayList<String> argParaTrace = new ArrayList<String>();
                			Expression arg = methCallExpr.getArgument(i);
                			String argTypeNName = getTypeNNameOfArgument(arg);
                			argParaTrace.add(argTypeNName);
                			argParaTrace.add(paraName);
                			argParaTraces.add(argParaTrace);
                		}                    	
                    }
                }
                
                //update related fields based on the arguments
                updateFieldsDueToArgParaChanges(modifiedFields, argParaTraces, methodLayer);
                updateFieldsDueToArgParaChanges(retrievedFields, argParaTraces, methodLayer);
                updateFieldsDueToArgParaChanges(returnedFields, argParaTraces, methodLayer);
                
                // handle inner method calls
                methodLayer++;
                for (MethodCallExpr c: methDeclaration.findAll(MethodCallExpr.class)) {
                	handleInnerMethodExpression(c, callingObjOrClass, modifiedFields, returnedFields, retrievedFields, isFromSuperConstructor, argParaTraces, methodLayer);
                }
//                methDeclaration.findAll(MethodCallExpr.class).forEach(
//                		c -> handleInnerMethodExpression(c, callingObjOrClass, modifiedFields, returnedFields, retrievedFields, isFromSuperConstructor, argParaTraces, methodLayer++));
                
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
        		
        		
            } 
            else if (declaration instanceof ReflectionMethodDeclaration) {
            	methCall.setMethodType(MethodCall.EXTERNAL_PRODUCER);
            	handleExternalProducerMethodCall((ReflectionMethodDeclaration) declaration, methCall, methCallExpr, methodCalls);            	           	
            }
            else {
                // I don't know if this will ever happen
                System.out.println("WARN: strange method found: " + declaration);
            }
        } catch(UnsolvedSymbolException e){
            // since junit methods are not part of the source, resolving junit methods will fail
            // then, we can identify asserts by their location in the Assert package (I hope)
            // TODO: there are other test frameworks and folders, e.g., jupiter in junit-5
            if (e.getName().equals("org.junit.Assert")) {
                methCall.setMethodType(MethodCall.ASSERT_STMT);
                ArrayList<VerifiedInformation> verifiedInfo = handleAssertStatement(methCallExpr, methodCalls);
                methCall.setVerifiedInfo(verifiedInfo);
                System.out.println("Assert stmt: " + methCall.getMethodCallName() + " --- Verified Info: " + methCall.getVerifiedInfo());      	
            }
            // assumption: if this is not an assert stmt, then it is an method from an external library
            else {
//            	methCall.setMethodType(MethodCall.EXTERNAL_PRODUCER);
//            	handleExternalMethodCall(call, methodCalls);
            }
        }
        return methCall;
    }
    
    // assumption: an external producer retrieves one single field from the calling object such as length, size, etc.
    private void handleExternalProducerMethodCall(ReflectionMethodDeclaration d, MethodCall methCall, MethodCallExpr methCallExpr, ArrayList<MethodCall> methodCalls) {
    	System.out.println("WARNING: External producer method!");
    	// get calling object 
    	String callingObject = "";
        for (NameExpr nameExpr : methCallExpr.findAll(NameExpr.class)) {
        	ResolvedValueDeclaration value = nameExpr.resolve();
            if (value.isField()) {
                ResolvedFieldDeclaration field = (ResolvedFieldDeclaration) value;
                ResolvedTypeDeclaration type = field.declaringType();
                callingObject = type.getQualifiedName() + "." + field.getName();
            }
        }        
        System.out.println("\tCalling object: " + callingObject);
        
        //extract method name
    	String methName = methCall.getMethodCallName().substring(methCall.getMethodCallName().lastIndexOf(".")+1, methCall.getMethodCallName().indexOf("("));
    	//listing cases that we can predict
    	switch(methName) {
    	case "toString":
    		//do nothing
    		break;    	
    	case "length":
    	case "isEmpty":
    		methCall.getRetrievedFields().add(callingObject + ".length");
    		break;
    	case "size":
    		methCall.getRetrievedFields().add(callingObject + ".size");
    		break;
    	default:
    		//assumption: this external producer method call retrieves all the fields modified by mutators(constructors) above 
    		ArrayList<MethodCall> methCallsAboveExternalProducerCall = MethodCall.getMethodCallAboveAPosition(methCall.getPosition(), methodCalls);
    		for (int i = methCallsAboveExternalProducerCall.size()-1; i >= 0; i--) {
    			MethodCall mCall = methCallsAboveExternalProducerCall.get(i);
    			if (mCall.getMethodType().equals(MethodCall.MUTATOR) ||
    				mCall.getMethodType().equals(MethodCall.CREATIONAL)) 
    			{
    				ArrayList<String> modifiedFields = mCall.getModifiedFields();
    				for (String modifiedField : modifiedFields) {
    					String callingObj = modifiedField.substring(0, modifiedField.lastIndexOf("."));
    					if (callingObj.equals(callingObject)) {
    			    		methCall.getRetrievedFields().add(modifiedField);
    					}    						
    				}    					
    			}    			    				
    		}    		
    		break;
    	}    	
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
    		String verifiedName = getTypeNNameOfNameExpr(nameExpr);
    		
    		// search for the source method call of the verifiedName
        	MethodCall sourceMC = getSourceMethodCallOfVerifiedName(verifiedName, mcPosition, methodCalls);
        	verifiedInfo.setSourceMethodCall(sourceMC);

    		// search for the verified method call
    		if (verifiedInfo.getSourceMethodCall().getMethodType().equals(MethodCall.MUTATOR) ||
    			verifiedInfo.getSourceMethodCall().getMethodType().equals(MethodCall.CREATIONAL))
    		{	
    			verifiedInfo.getVerifiedMethodCalls().add(verifiedInfo.getSourceMethodCall());
    			verifiedInfo.getVerifiedInfo().add(verifiedName);
    			verifiedInfo.getVerifiedInfo().addAll(verifiedInfo.getSourceMethodCall().getModifiedFields());
    		}
    		else if (verifiedInfo.getSourceMethodCall().getMethodType().equals(MethodCall.INTERNAL_PRODUCER)) {
    			verifiedInfo.getVerifiedMethodCalls().add(verifiedInfo.getSourceMethodCall());
        		verifiedInfo.getVerifiedInfo().add(verifiedName);
        	}
    		else if (verifiedInfo.getSourceMethodCall().getMethodType().equals(MethodCall.GET) ||
    				verifiedInfo.getSourceMethodCall().getMethodType().equals(MethodCall.EXTERNAL_PRODUCER)) 
    		{    			
    			ArrayList<String> retrievedFields = verifiedInfo.getSourceMethodCall().getRetrievedFields();
        		verifiedInfo.getVerifiedInfo().addAll(retrievedFields); 
        		//TODO: assumption: a get method has only 1 retrieved field!
        		ArrayList<MethodCall> verifiedMethodCalls = getVerifiedMethodCalls(verifiedInfo, methodCalls);
        		MethodCall.addMultipleNewMethodCall(verifiedInfo.getVerifiedMethodCalls(), verifiedMethodCalls, false);
//				verifiedInfo.getVerifiedMethodCalls().add(verifiedMethodCall);
    		}    		
    	}
    	else if (expr instanceof MethodCallExpr) {
			MethodCallExpr methCallExpr = (MethodCallExpr) expr;
			MethodCall methCall = handleMethodExpression(methCallExpr, null);
    		verifiedInfo.setSourceMethodCall(methCall);
    		
			String methType = methCall.getMethodType();
			if (methType.equals(MethodCall.CREATIONAL) || methType.equals(MethodCall.MUTATOR)) {
				verifiedInfo.getVerifiedMethodCalls().add(methCall);
				verifiedInfo.getVerifiedInfo().addAll(methCall.getModifiedFields());

				methCall.setReturnedValue("return value of " + methCall.getMethodCallName());
				MethodCall.addNewMethodCall(methodCalls, methCall, true);				
				verifiedInfo.getVerifiedMethodCalls().add(methCall);
				verifiedInfo.getVerifiedInfo().add(methCall.getReturnedValue());
			}
			else if (methType.equals(MethodCall.GET) || methType.equals(MethodCall.EXTERNAL_PRODUCER)) {
				verifiedInfo.getVerifiedInfo().addAll(methCall.getRetrievedFields());
				ArrayList<MethodCall> verifiedMethodCalls = getVerifiedMethodCalls(verifiedInfo, methodCalls);
				MethodCall.addMultipleNewMethodCall(verifiedInfo.getVerifiedMethodCalls(), verifiedMethodCalls, false);
//				verifiedInfo.setVerifiedMethodCall(verifiedMethodCall);
				
			}
			else if (methType.equals(MethodCall.INTERNAL_PRODUCER)) {
				methCall.setReturnedValue("return value of " + methCall.getMethodCallName()); // as the return value cannot be calculated on the spot!
				MethodCall.addNewMethodCall(methodCalls, methCall, true);				
				verifiedInfo.getVerifiedMethodCalls().add(methCall);
				verifiedInfo.getVerifiedInfo().add(methCall.getReturnedValue());
			}				
		}
    	return verifiedInfo;
    }
    
    private ArrayList<MethodCall> getVerifiedMethodCalls (VerifiedInformation verifiedInfo, ArrayList<MethodCall> methodCalls) {
    	ArrayList<MethodCall> verifiedMethCalls = new ArrayList<MethodCall>();
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
	    				//TODO: assumption: a get method has only 1 retrieved field!
	    				MethodCall.addNewMethodCall(verifiedMethCalls, mCall, false);
	    				break;
	    			}
    			}    			    				
    		}
		}
    	return verifiedMethCalls;
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
        
    private void collectAllFields(MethodDeclaration method, String callingObjOrClass, ArrayList<String> modifiedFields,
    		ArrayList<String> returnedFields, ArrayList<String> retrievedFields, boolean isFromSuperConstructor) {
            
    	// check all field accesses
    	for (FieldAccessExpr expr: method.findAll(FieldAccessExpr.class)) {
    		String fieldAccess = getObjectField(expr, callingObjOrClass, isFromSuperConstructor);
    		if (fieldAccess != null)
    			updateFieldsAccessListByCalledMethod(expr, fieldAccess, modifiedFields, returnedFields, retrievedFields);        	
    	}
            
    	// check for fields which are accessed simply by name
    	for (NameExpr expr: method.findAll(NameExpr.class)) {
    		String fieldByName = getVariableByNameOnly(expr, callingObjOrClass, isFromSuperConstructor); 
    		if (fieldByName != null && fieldByName.contains("."))
    			updateFieldsAccessListByCalledMethod(expr, fieldByName, modifiedFields, returnedFields, retrievedFields);
        }
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
        	Node parentNode = originalExpr.getParentNode().get();
        	if (parentNode instanceof ReturnStmt) {
        		expr = originalExpr;
        		fromReturnStmt = true;
        	}
        	else if (parentNode instanceof Expression) {
        		expr = (Expression) parentNode;
        	}
        	
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
    
    private void handleInnerMethodExpression(MethodCallExpr call, String outerCallingObjectOrClass,	ArrayList<String> modifiedFields,
    										 ArrayList<String> returnedFields, ArrayList<String> retrievedFields,
    										 boolean isFromSuperConstructor, ArrayList<ArrayList<String>> argParaTrace,
    										 int methodLayer) {
        System.out.println("Inner Call: " + call.getNameAsString());
        String innerCallingObjOrClass = "";
        try {
        	ResolvedMethodDeclaration declaration = call.resolve();
            if (declaration instanceof JavaParserMethodDeclaration) {
                MethodDeclaration d = ((JavaParserMethodDeclaration) declaration).getWrappedNode();
                
                if (methodLayer > 0)  {//meaning dont handle this issue with inner method inside a constructor call
	                //check if any argument of the outer method call is used as an argument of this inner method call            	
	                for (int i = 0; i < d.getParameters().size(); i++) {
	                	Parameter para = d.getParameter(i);
	                	Expression arg = call.getArgument(i);
	                	for (ArrayList<String> argPara : argParaTrace) {
	                		String outerPara = argPara.get(methodLayer);
	                		if (outerPara.equals(arg.toString())) {
	                			argPara.add(para.getNameAsString());
	                		}
	                	}                	
	                }                
                }
                // get calling object name
                if (call.getScope().isPresent())
                	innerCallingObjOrClass = call.getScope().get().toString();     
                else 
                	innerCallingObjOrClass = outerCallingObjectOrClass;
                System.out.println(innerCallingObjOrClass);
                
                analyzeInnerCalledMethod(d, innerCallingObjOrClass, modifiedFields, returnedFields, retrievedFields, isFromSuperConstructor);
                
                if (methodLayer > 0)  {//meaning dont handle this issue with inner method inside a constructor call
                	//update fields
                    updateFieldsDueToArgParaChanges(modifiedFields, argParaTrace, methodLayer);
                    updateFieldsDueToArgParaChanges(returnedFields, argParaTrace, methodLayer);
                    updateFieldsDueToArgParaChanges(retrievedFields, argParaTrace, methodLayer);
                }
                
                
                // handle inner method calls
                if (methodLayer > 0)
                	methodLayer++;
                for (MethodCallExpr c: d.findAll(MethodCallExpr.class)) {
                	handleInnerMethodExpression(c, innerCallingObjOrClass, modifiedFields, returnedFields, retrievedFields, isFromSuperConstructor, argParaTrace, methodLayer);
                }
//                d.findAll(MethodCallExpr.class).forEach(c -> handleInnerMethodExpression(c, callingObject, modifiedFields, returnedFields, retrievedFields, isFromSuperConstructor, outerArgParaTrace));
            }
            //TODO: external producer does not affect anything
            // outer is mutator + inner is external producer -> this e. producer cannot help to modify field(s)
            // outer is creational + inner is external producer -> this e. producer does not initialize any field
            // outer is internal producer + inner is external producer -> it does not matter as we care about the return value of the outer method only.
            else if (declaration instanceof ReflectionMethodDeclaration) {
            	// does nothing
            	System.out.println("the inner method is an external producer method: " + declaration);
            }
            else {
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
    	
    	collectAllFields(method, callingObject, modifiedFields, returnedFields, retrievedFields, isFromSuperConstructor);

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
        String initializedObj = declaration.getQualifiedName().substring(0, declaration.getQualifiedName().lastIndexOf(".")) +
        						"." + varName;
        System.out.println("\tInitialized object: " + initializedObj);
        methCall.setReturnedValue(initializedObj);

        if (declaration instanceof JavaParserConstructorDeclaration) {
        	boolean isFromSuperConstructor = false;
            ConstructorDeclaration constrDeclaration = ((JavaParserConstructorDeclaration) declaration).getWrappedNode();
            ArrayList<String> modifiedFields = new ArrayList<String>();
            analyzeCalledConstructor(constrDeclaration, varName, modifiedFields, isFromSuperConstructor);
            for (MethodCallExpr c : constrDeclaration.findAll(MethodCallExpr.class)) {
            	handleInnerMethodExpression(c, varName, modifiedFields, null, null, isFromSuperConstructor, null, -1);
            }
            	
            
            //find a call to super or this in the constructor
            for (ExplicitConstructorInvocationStmt s: constrDeclaration.findAll(ExplicitConstructorInvocationStmt.class)) {
            	if (s.getTokenRange().get().toString().contains("super"))
            		isFromSuperConstructor = true;
            	else
            		isFromSuperConstructor = false;
            	ResolvedConstructorDeclaration resolvedDeclaration = s.resolve();
            	if (resolvedDeclaration instanceof JavaParserConstructorDeclaration) {
                    ConstructorDeclaration resolvedConstrDeclaration = ((JavaParserConstructorDeclaration) resolvedDeclaration).getWrappedNode();
                 	analyzeCalledConstructor(resolvedConstrDeclaration, initializedObj, modifiedFields, isFromSuperConstructor); // TODO: not sure how it works when replacing callingObj with initializedObj
                 	for (MethodCallExpr c : resolvedConstrDeclaration.findAll(MethodCallExpr.class)) {
                 		handleInnerMethodExpression(c, varName, modifiedFields, null, null, isFromSuperConstructor, null, -1);
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
    		String classField = getVariableByNameOnly(expr, initializedObj, isSuperConstructor); 
    		if (classField != null)
    			updateFieldsAccessListByCalledMethod(expr, classField, modifiedFields, null, null);
    	}
    }    
    
    private MethodCallPosition getExpressionPosition(Expression expr) {
    	int beginLine = expr.getRange().get().begin.line;
    	int endLine = expr.getRange().get().end.line;
    	return new MethodCallPosition(beginLine, endLine);
    }
    
    private String getObjectField (FieldAccessExpr expr, String callingObjOrClass, boolean isFromSuperConstructor) {
    	String fieldAccess = null;  
    	if (isFromSuperConstructor) {
    		fieldAccess = callingObjOrClass + "." + expr.getNameAsString();
    	}
    	else {
    		Expression scope = expr.getScope();
            if (scope instanceof ThisExpr) {
                ThisExpr thisExpr = (ThisExpr) scope;
                ResolvedTypeDeclaration type = thisExpr.resolve();
                fieldAccess = type.getQualifiedName() + "." + callingObjOrClass + "." + expr.getNameAsString();
                System.out.println("\tField Access: " + fieldAccess);
            } else if (scope instanceof NameExpr) {
                NameExpr nameExpr = (NameExpr) scope;
                ResolvedValueDeclaration value = nameExpr.resolve();
                ResolvedReferenceType type = value.getType().asReferenceType();
                if (type.getQualifiedName().substring(type.getQualifiedName().lastIndexOf(".")+1).equals(callingObjOrClass))
//                if (callingObjOrClass == null) 
                	// meaning this is a class name
                	fieldAccess = type.getQualifiedName() + "." + value.getName()
      			  				  + "." + expr.getNameAsString();
                else
                	fieldAccess = type.getQualifiedName() + "." + callingObjOrClass + "." + value.getName()
                			      + "." + expr.getNameAsString();
                System.out.println("\tField Access: " + fieldAccess);
            } else {
                throw new RuntimeException("Unknown instance for: " + scope);
            }
    	}
        return fieldAccess;
    }
       
    private String getVariableByNameOnly (NameExpr expr, String callingObjOrClass, boolean isFromSuperConstructor) {
    	String fieldbyName = null;
    	if (isFromSuperConstructor) {
    		fieldbyName = callingObjOrClass + "." + expr.getNameAsString();
    	}
    	else {
    		ResolvedValueDeclaration value = expr.resolve();
            if (value.isField()) {
                ResolvedFieldDeclaration field = (ResolvedFieldDeclaration) value;
                ResolvedTypeDeclaration type = field.declaringType();
                //TODO: when is it null???
                //case 2: when the callingObjOrClass is a class name
                if (callingObjOrClass == null ||
                	type.getQualifiedName().substring(type.getQualifiedName().lastIndexOf(".")+1).equals(callingObjOrClass))
                	fieldbyName = type.getQualifiedName() + "." + field.getName();
                else
                	fieldbyName = type.getQualifiedName() + "." + callingObjOrClass + "." + field.getName();
                System.out.println("\tField by name: " + fieldbyName);
            }
            else if (value instanceof JavaParserParameterDeclaration) {
            	Parameter para = ((JavaParserParameterDeclaration) value).getWrappedNode();
            	ResolvedParameterDeclaration reParaDecl = para.resolve();
        		ResolvedType resolvedType = reParaDecl.getType();
            	fieldbyName = getTypeNNameOfVariable(resolvedType, expr.getNameAsString());
//            	ResolvedReferenceType resolvedType = reParaDecl.getType().asReferenceType();
//            	fieldbyName = type.getQualifiedName() + "." + expr.getNameAsString();
            }
    	}        
        return fieldbyName;
    }
    
    private String getTypeNNameOfNameExpr(NameExpr nameExpr) {
    	String typeNName = "";
    	String varName = nameExpr.getName().getIdentifier();
		ResolvedValueDeclaration valueDeclaration = nameExpr.resolve();	
		ResolvedType type = valueDeclaration.getType();
		typeNName = getTypeNNameOfVariable(type, varName);
		return typeNName;
    }
    
    private String getTypeNNameOfVariable(ResolvedType type, String varName) {
    	String typeNName = "";
		if (type instanceof ResolvedPrimitiveType) {
			typeNName = varName;
		}
		else if (type instanceof ResolvedReferenceType) {    			
			ResolvedReferenceType referenceType = (ResolvedReferenceType)type;    			
			typeNName = referenceType.getQualifiedName() + "." + varName;
		}
		else if (type instanceof ResolvedArrayType) {
			ResolvedArrayType arrayType = (ResolvedArrayType)type;
			boolean isPrimitivetype = arrayType.getComponentType().isPrimitive();
			if (isPrimitivetype) {
				typeNName = varName;
			}
			else if (arrayType.getComponentType().isReferenceType()) {
				typeNName = arrayType.getComponentType().describe() + "." + varName;
			}
			
		}
		return typeNName;
    }
}




	

