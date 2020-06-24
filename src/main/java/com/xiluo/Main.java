package com.xiluo;

import java.io.*;

/**
 * decompiler
 *
 * @author xiluo
 * @createTime 2019-04-26 22:52
 **/
public class Main {

    public static void main(String[] args) {
        if (args == null) {
            System.out.println("Please specific directories.");
        }

        for (int i = 0; i < args.length; ++i) {
            File f = new File(args[i]);
            if (f.isFile() && args[i].endsWith(".class")) {
                decompile(f);
            } else if (f.isDirectory()) {
                decompileDir(f);
            }
        }
    }

    private static void decompileDir(File file) {
        File[] files = file.listFiles();
        for (File subFile : files) {
            if (subFile.isFile() && subFile.getAbsolutePath().endsWith(".class")) {
                decompile(subFile);
            } else if (subFile.isDirectory()) {
                decompileDir(subFile);
            }
        }
    }

    private static void decompile(File classFilePath) {
        System.out.println("begin to decompile class: " + classFilePath.getAbsolutePath());
        String javaPath = javaFileName4Class(classFilePath.getAbsolutePath());
        String javaCode = Decompiler.decompile(classFilePath.getAbsolutePath(), null);
        writeFile(javaPath, javaCode);
    }

    private static void writeFile(String javaPath, String javaCode) {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(new File(javaPath)));
            writer.write(javaCode);
            writer.flush();
        } catch (IOException e) {
            System.err.println("write file error: " + javaPath);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException i) {
                }
            }
        }
    }

    private static String javaFileName4Class(String classPath) {
        return classPath.substring(0, classPath.lastIndexOf(".class")).concat(".java");
    }
}
