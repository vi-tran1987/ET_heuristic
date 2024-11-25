package se.bth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import static java.util.Comparator.comparing;

public class MethodCall {
	
	public final static String GET = "Get method";
	public final static String MUTATOR = "Mutator method";
	public final static String CREATIONAL = "Creational method";
	public final static String INTERNAL_PRODUCER = "Internal producer method";
	public final static String EXTERNAL_PRODUCER = "External producer method";
	public final static String ASSERT_STMT = "assert statement";	
	
	private int id = -1;
	private MethodCallPosition position = new MethodCallPosition();
	private String methodCallName = "";
	private String methodType = "";
//	private ArrayList<String> initializedFields = new ArrayList<String>();
	private ArrayList<String> retrievedFields = new ArrayList<String>();
	private ArrayList<String> modifiedFields = new ArrayList<String>(); //for constructor, simply considered all initialized fields as modified fields as it is not necessary to distinguish them
	private ArrayList<String> returnedFields = new ArrayList<String>();
	private String returnedValue = ""; // returnedValue and returnedFields together provide FULL info of the return data
//	private boolean isAssertStmt = false;
	private ArrayList<VerifiedInformation> verifiedInfo = new ArrayList<VerifiedInformation>();
	
	
	public MethodCallPosition getPosition() {
		return position;
	}
	public void setPosition(MethodCallPosition position) {
		this.position = position;
	}
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	
	public String getMethodCallName() {
		return methodCallName;
	}
	public void setMethodCallName(String methodCall) {
		this.methodCallName = methodCall;
	}
	
	public String getMethodType() {
		return methodType;
	}
	public void setMethodType(String methodType) {
		this.methodType = methodType;
	}
	
	public ArrayList<String> getRetrievedFields() {
		return retrievedFields;
	}
	public void setRetrievedFields(ArrayList<String> retrievedFields) {
		this.retrievedFields = retrievedFields;
	}
	
	public ArrayList<String> getModifiedFields() {
		return modifiedFields;
	}
	public void setModifiedFields(ArrayList<String> modifiedFields) {
		this.modifiedFields = modifiedFields;
	}
	
	public ArrayList<String> getReturnedFields() {
		return returnedFields;
	}
	public void setReturnedFields(ArrayList<String> returnedFields) {
		this.returnedFields = returnedFields;
	}
	
	public String getReturnedValue() {
		return returnedValue;
	}
	public void setReturnedValue(String returnedValue) {
		this.returnedValue = returnedValue;
	}
	
//	public boolean isAssertStmt() {
//		return isAssertStmt;
//	}
//	public void setIsAssertStmt(boolean isAssertStmt) {
//		this.isAssertStmt = isAssertStmt;
//	}

//	public ArrayList<String> getInitializedFields() {
//		return initializedFields;
//	}
//	public void setInitializedFields(ArrayList<String> initializedFields) {
//		this.initializedFields = initializedFields;
//	}	

	public ArrayList<VerifiedInformation> getVerifiedInfo() {
		return verifiedInfo;
	}
	public void setVerifiedInfo(ArrayList<VerifiedInformation> verifiedInfo) {
		this.verifiedInfo = verifiedInfo;
	}
	
	public static boolean addNewMethodCall(ArrayList<MethodCall> callList, MethodCall methCall, boolean updateID) {
		for (MethodCall c : callList) {
			if (c.getMethodCallName().equals(methCall.getMethodCallName()) &&
				c.getPosition().equals(methCall.getPosition()))
				return false;
		}
		if (updateID)
			methCall.setId(callList.size());
		callList.add(methCall);
		return true;
	}
	
	public static void addMultipleNewMethodCall(ArrayList<MethodCall> callList, ArrayList<MethodCall> methCallsToAdd, boolean updateID) {
		outer: for (MethodCall mc : methCallsToAdd) {
			for (MethodCall c : callList) {
				if (c.getMethodCallName().equals(mc.getMethodCallName()) &&
					c.getPosition().equals(mc.getPosition()))
					continue outer;
			}
			if (updateID)				
				mc.setId(callList.size());
			callList.add(mc);
		}		
	}
	
    public static ArrayList<MethodCall> getMethodCallBeforeID (int id, ArrayList<MethodCall> methCalls) {
    	ArrayList<MethodCall> returnCalls = new ArrayList<MethodCall>();
    	for (MethodCall c : methCalls) {
    		if (c.getId() < id)
    			returnCalls.add(c);
    		else
    			break;
    	}
    	
    	return returnCalls;
    }
    
    public static MethodCall getMethodCallbyNameNPosition(ArrayList<MethodCall> mCallList, String mCallName, String mCallPosition) {
    	for (MethodCall mCall : mCallList) {
    		if (mCall.getMethodCallName().equals(mCallName) && mCall.getPosition().equals(mCallPosition))
    			return mCall;
    	}    	
    	return null;
    }
 
    public static ArrayList<MethodCall> getMethodCallsAboveAPosition (MethodCallPosition mcPosition, ArrayList<MethodCall> methCalls) {
    	ArrayList<MethodCall> returnCalls = new ArrayList<MethodCall>();
    	for (MethodCall c : methCalls) {
    		if (c.getPosition().getEndLine() < mcPosition.getBeginLine())
    			returnCalls.add(c);
//    		else
//    			break;
    	}
    	ArrayList<MethodCall> sortedReturnCalls = MethodCall.sortMethodCallsByPosition(returnCalls);
    	
    	return sortedReturnCalls;
    }
    
    public static ArrayList<MethodCall> sortMethodCallsByPosition(ArrayList<MethodCall> methCalls) {    	
    	ArrayList<MethodCall> sort = new ArrayList<MethodCall>(methCalls);
        Collections.sort(sort, new Comparator<MethodCall>() {
                public int compare(MethodCall o1, MethodCall o2) {
                	return o1.getPosition().compareTo(o2.getPosition());
//                       return o1.getPosition().getBeginLine() < o2.getPosition().getBeginLine() ? -1 : 
//                    	   	  o1.getPosition().getBeginLine() == o2.getPosition().getBeginLine() ? 0 : 1;
                }
          }
       );
       return sort;
    }
    
    @Override
    public String toString() {
    	String s = "";
    	if (this.getMethodType().equals(ASSERT_STMT)) {
    		s = "ID: " + this.getId() + " ----- Assert statement: " + this.getMethodCallName() + "\n" +
        		"\tPosition: " + this.getPosition() + "\n" +
        		"\tVerified info: " + this.getVerifiedInfo().toString(); 
    	}
    	else {
    		s = "ID: " + this.getId() + " ----- Method call: " + this.getMethodCallName() + " ----- " + this.getMethodType() + "\n" +
        		"\tPosition: " + this.getPosition() + "\n" +
        		"\tRetrieved fields: " + this.getRetrievedFields() + "\n" +
        		"\tModified fields: " + this.getModifiedFields() + "\n" +
        		"\tReturned fields: " + this.getReturnedFields() + "\n" +
        		"\tReturn value: " + this.getReturnedValue(); 
    	}            	          		
		return s;    	
    }

    public static ArrayList<MethodCall> getMethodCallByType(String type, ArrayList<MethodCall> methCalls) {
    	ArrayList<MethodCall> returnCalls = new ArrayList<MethodCall>();
    	for (MethodCall c : methCalls) {
    		if (c.getMethodType().equals(type))
    			returnCalls.add(c);
    	}
    	return returnCalls;
    }
}
