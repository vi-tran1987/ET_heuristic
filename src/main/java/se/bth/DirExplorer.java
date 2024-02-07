package se.bth;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
//import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.File;

public class DirExplorer {

    public DirExplorer() {
    }

    public void explore(File root) {
        explore(0, "", root);
    }

    private void explore(int level, String path, File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                explore(level + 1, path + "/" + child.getName(), child);
            }
        } else {
            if (this.isInterested(level, path, file)) {
                this.handleFile(level, path, file);
            }
        }
    }

    private boolean isInterested(int level, String path, File file) {
        return path.endsWith(".java");
    }

    private void handleFile(int level, String path, File file) {
        System.out.println(path);
        System.out.println("==========");
/*        try {
            new VoidVisitorAdapter<Object>() {
                @Override
                public void visit(MethodCallExpr n, Object arg) {
                    super.visit(n, arg);
                    System.out.println(n);
                }
            }.visit(JavaParser.parse(file), null);;
        } catch(Exception e){
            e.printStackTrace();
        }
*/
    }
}
