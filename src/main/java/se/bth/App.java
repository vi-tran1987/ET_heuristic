package se.bth;

/**
 * Hello world!
 *
 */
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Scanner;

import se.bth.MyParser;

public class App
{
    public static void main( String[] args )
    {
//        if (args.length != 2) {
//            System.out.println("usage: App.java <main-path> <test-path>");
//        }
//    	File javaDir = new File(args[0]);
//    	File testDir = new File(args[1]);
    	
    	
    	File javaDir = new File("D:/ET/example_app/src/main/java");
    	File testDir = new File("D:/ET/example_app/src/test/java");    	
    	
//    	String inputFile = "D:/ET/SF110_top10/project101_short.csv";
    	try {
//        	ArrayList<MethodEntity> methEntities = getMethodEntities(inputFile);
//        	ArrayList<String> allFilePaths = MethodEntity.getAllFilePaths(methEntities);
//            MyParser parser = new MyParser(javaDir, testDir, allFilePaths);

            MyParser parser = new MyParser(javaDir, testDir);
            
//            File file = new File("D:/ET/SF110_top10/ET_result_project101_v4.txt");
          File file = new File("D:/ET/example_app/EagerID_result.txt");
            FileWriter fw = new FileWriter(file,true);
            PrintWriter output = new PrintWriter(fw, true);
            output.println(testDir.toString());
            
//            File errorReport = new File("D:/ET/SF110_top10/ET_error_report_project101_v4.txt");
            File errorReport = new File("D:/ET/example_app/EagerID_error_report.txt");
            FileWriter eRfw = new FileWriter(errorReport,true);
            PrintWriter eRoutput = new PrintWriter(eRfw, true);
            eRoutput.println(testDir.toString());
            
            
//            parser.analyseMethods(methEntities, output, eRoutput);
            parser.analyseMethods(output, eRoutput);
            
            output.close();
            eRoutput.close();
        } catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public static ArrayList<MethodEntity> getMethodEntities(String fileName) {
    	ArrayList<MethodEntity> methEntities = new ArrayList<MethodEntity>();
    	try {
    		File file = new File(fileName);
    	    Scanner myReader = new Scanner(file);
    	    myReader.hasNextLine();
    	    while (myReader.hasNextLine()) {
    	    	String line = myReader.nextLine();
    	    	String[] tokens=line.split(";"); 
    	    	MethodEntity methEntity = new MethodEntity();
    	    	methEntity.setFilePath(tokens[2]);
    	    	methEntity.setClassName(tokens[4]);
    	    	methEntity.setMethodName(tokens[5]);
    	    	methEntities.add(methEntity);
    	    }
    	    myReader.close();
    	} catch (FileNotFoundException e) {
    	    e.printStackTrace();
    	}
		return methEntities;
    }
    
    
    
}
