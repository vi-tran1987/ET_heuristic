package se.bth;

import java.util.ArrayList;

public class MethodEntity {
	private String filePath = "";
	private String className = new String();
	private String methodName = new String();
	
	public String getFilePath() {
		return filePath;
	}
	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}
	
	public String getClassName() {
		return className;
	}
	public void setClassName(String className) {
		this.className = className;
	}
	
	public String getMethodName() {
		return methodName;
	}
	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}
    public static ArrayList<String> getAllFilePaths(ArrayList<MethodEntity> methEntities) {
    	ArrayList<String> allFilePaths = new ArrayList<String>();    	
    	for (MethodEntity methEntity : methEntities) {
    		allFilePaths.add(methEntity.getFilePath());
    	}    	
    	return allFilePaths;
    }
	
	
}
