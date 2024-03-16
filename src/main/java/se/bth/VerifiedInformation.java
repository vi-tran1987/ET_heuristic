package se.bth;

import java.util.ArrayList;

public class VerifiedInformation {
	private ArrayList<String> verifiedInfo = new ArrayList<String>();
	private MethodCall sourceMethodCall = new MethodCall(); // this method provides the verified information. It can either be a helper method or the verified method
	private ArrayList<MethodCall> verifiedMethodCalls = new ArrayList<MethodCall>(); // this method is the one that the assert stmt wants to verified
//	private boolean isVerifiedMethodCallIdentified = true;
	
	public ArrayList<String> getVerifiedInfo() {
		return verifiedInfo;
	}
	public void setVerifiedInfo(ArrayList<String> verifiedInfo) {
		this.verifiedInfo = verifiedInfo;
	}
	
	public MethodCall getSourceMethodCall() {
		return sourceMethodCall;
	}
	public void setSourceMethodCall(MethodCall mc) {
		this.sourceMethodCall = mc;
	}
	
	public ArrayList<MethodCall> getVerifiedMethodCalls() {
		return verifiedMethodCalls;
	}
	public void setVerifiedMethodCalls(ArrayList<MethodCall> verifiedMethodCalls) {
		this.verifiedMethodCalls = verifiedMethodCalls;
	}

//	public boolean isVerifiedMethodCallIdentified() {
//		return isVerifiedMethodCallIdentified;
//	}
//	public void setIsVerifiedMethodCallIdentified(boolean isVerifiedMethodCallIdentified) {
//		this.isVerifiedMethodCallIdentified = isVerifiedMethodCallIdentified;
//	}
	
	public String printVerifiedMethodCalls(ArrayList<MethodCall> vmcs) {
		String s = "";
		for (MethodCall vmc: vmcs) {
			s += "name - " + vmc.getMethodCallName() + "; position: " + vmc.getPosition() + " --- ";
		}
		return s;
	}
	
	@Override
	public String toString() {
		String s = "verified info: " + this.getVerifiedInfo() + "\n" +
				   "\t\t| source method call: name - " + this.getSourceMethodCall().getMethodCallName() +
				   "; position - " + this.getSourceMethodCall().getPosition() + "\n" +
				   "\t\t| verified method call: " + printVerifiedMethodCalls(this.getVerifiedMethodCalls());	
		return s;
	}
}
