package se.bth;

/**
 * Hello world!
 *
 */
import java.io.File;

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
    	
    	
//        File javaDir = new File("D:/ET/vi_example_app/src/main/java");
//        File testDir = new File("D:/ET/vi_example_app/src/test/java");
//    	File javaDir = new File("D:/ET/real_cases/19_jmca/src/main/java");
//    	File testDir = new File("D:/ET/real_cases/19_jmca/evosuite-tests/com/soops/CEN4010/JMCA/JParser");
//    	File javaDir = new File("D:/ET/real_cases/104_vuze/src/main/java");
//    	File testDir = new File("D:/ET/real_cases/104_vuze/evosuite-tests/com/aelitis/azureus/core/instancemanager/impl");
//    	File javaDir = new File("D:/ET/real_cases/107_weka/src/main/java");
//    	File testDir = new File("D:/ET/real_cases/107_weka/evosuite-tests/weka/associations");
    	File javaDir = new File("D:/ET/real_cases/108_liferay/src/main/java");
    	File testDir = new File("D:/ET/real_cases/108_liferay/evosuite-tests/com/liferay/portal/kernel/scheduler/");
    	         

        try {
            MyParser parser = new MyParser(javaDir, testDir);

            parser.analyseMethods();
        } catch(Exception e){
            e.printStackTrace();
        }
    }
}
