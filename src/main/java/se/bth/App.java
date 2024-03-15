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
//         File javaDir = new File("/Users/wesflo/Documents/people/vi/phd/javaparser/my-app/src/main/java");
//         File testDir = new File("/Users/wesflo/Documents/people/vi/phd/javaparser/my-app/src/test/java");
         File javaDir = new File("D:/ET/vi_example_app/src/main/java");
         File testDir = new File("D:/ET/vi_example_app/src/test/java");
         
//        File javaDir = new File(args[0]);
//        File testDir = new File(args[1]);

        try {
            MyParser parser = new MyParser(javaDir, testDir);

            parser.analyseMethods();
        } catch(Exception e){
            e.printStackTrace();
        }
    }
}
