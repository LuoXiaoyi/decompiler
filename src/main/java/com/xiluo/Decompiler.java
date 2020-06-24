package com.xiluo;

import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.relationship.MemberNameResolver;
import org.benf.cfr.reader.state.*;
import org.benf.cfr.reader.util.AnalysisType;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.getopt.GetOptParser;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * decompiler
 *
 * @author xiluo
 * @createTime 2019-04-26 22:54
 **/
public class Decompiler {

    public static String decompile(String classFilePath, String methodName) {
        String result = "";

        //构造参数
        List<String> argList = new ArrayList<String>();
        argList.add(classFilePath);
        if (methodName != null) {
            argList.add("--methodname");
            argList.add(methodName);
        }
        String[] args = argList.toArray(new String[0]);

        //反编译
        GetOptParser getOptParser = new GetOptParser();

        Options options = null;
        List<String> files = null;
        try {
            Pair<List<String>, Options> processedArgs = getOptParser.parse(args, OptionsImpl.getFactory());
            files = (List) processedArgs.getFirst();
            options = (Options) processedArgs.getSecond();
            if (files.size() == 0) {
                throw new IllegalArgumentException("Insufficient unqualified parameters - provide at least one filename.");
            }
        } catch (Exception e) {
            getOptParser.showHelp(e);
            System.exit(1);
        }

        if ((options.optionIsSet(OptionsImpl.HELP)) || (files.isEmpty())) {
            getOptParser.showOptionHelp(OptionsImpl.getFactory(), options, OptionsImpl.HELP);
            return "";
        }

        //CfrDriver cfrDriver = new CfrDriver.Builder().withBuiltOptions(options).build();
        //cfrDriver.analyse(files);

        result = analyse(files, options, null, null);


        return result;
    }

    private static String analyse(List<String> toAnalyse, Options options, ClassFileSource classFileSource, OutputSinkFactory outputSinkFactory) {
        String result = "";
        if (classFileSource == null) {
            classFileSource = new ClassFileSourceImpl(options);
        }

        //boolean skipInnerClass = (toAnalyse.size() > 1) && (((Boolean)options.getOption(OptionsImpl.SKIP_BATCH_INNER_CLASSES)).booleanValue());
        boolean skipInnerClass = toAnalyse.size() > 1 && (Boolean) options.getOption(OptionsImpl.SKIP_BATCH_INNER_CLASSES);

        Collections.sort(toAnalyse);
        for (String path : toAnalyse) {

            classFileSource.informAnalysisRelativePathDetail(null, null);

            DCCommonState dcCommonState = new DCCommonState(options, classFileSource);


            DumperFactory dumperFactory = outputSinkFactory != null ? new SinkDumperFactory(outputSinkFactory, options) : new InternalDumperFactoryImpl(options);

            AnalysisType type = (AnalysisType) options.getOption(OptionsImpl.ANALYSE_AS);
            if ((type == null) || (type == AnalysisType.DETECT)) {
                type = dcCommonState.detectClsJar(path);
            }

            if ((type == AnalysisType.JAR) || (type == AnalysisType.WAR)) {
                //doJar(dcCommonState, path, dumperFactory);
            } else if (type == AnalysisType.CLASS) {
                result = doClass(dcCommonState, path, skipInnerClass, dumperFactory);
            }
        }

        return result;
    }

    private static String doClass(DCCommonState dcCommonState, String path, boolean skipInnerClass, DumperFactory dumperFactory) {
        StringBuilder result = new StringBuilder(8192);

        Options options = dcCommonState.getOptions();
        org.benf.cfr.reader.util.output.IllegalIdentifierDump illegalIdentifierDump = IllegalIdentifierDump.Factory.get(options);
        Dumper d = new org.benf.cfr.reader.util.output.ToStringDumper();
        ExceptionDumper ed = dumperFactory.getExceptionDumper();
        try {
            SummaryDumper summaryDumper = new org.benf.cfr.reader.util.output.NopSummaryDumper();
            ClassFile c = dcCommonState.getClassFileMaybePath(path);
            if ((skipInnerClass) && (c.isInnerClass())) {
                return "";
            }
            dcCommonState.configureWith(c);
            dumperFactory.getProgressDumper().analysingType(c.getClassType());

            try {
                c = dcCommonState.getClassFile(c.getClassType());
            } catch (CannotLoadClassException localCannotLoadClassException) {
            }

            if ((Boolean) options.getOption(OptionsImpl.DECOMPILE_INNER_CLASSES)) {
                c.loadInnerClasses(dcCommonState);
            }
            if ((Boolean) options.getOption(OptionsImpl.RENAME_DUP_MEMBERS)) {
                MemberNameResolver.resolveNames(dcCommonState, ListFactory.newList(dcCommonState.getClassCache().getLoadedTypes()));
            }

            c.analyseTop(dcCommonState);


            TypeUsageCollector collectingDumper = new TypeUsageCollectorImpl(c);
            c.collectTypeUsages(collectingDumper);

            //output: org.benf.cfr.reader.util.output.StdIODumper@7f046d60
            //d = dumperFactory.getNewTopLevelDumper(c.getClassType(), summaryDumper, collectingDumper.getTypeUsageInformation(), illegalIdentifierDump);

            d = new StringDumper(collectingDumper.getTypeUsageInformation(), options, illegalIdentifierDump);

            String methname = (String) options.getOption(OptionsImpl.METHODNAME);
            if (methname == null) {
                c.dump(d);
            } else {
                try {
                    for (Method method : c.getMethodByName(methname)) {
                        method.dump(d, true);
                    }
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException("No such method '" + methname + "'.");
                }
            }
            d.print("");
            result.append(d.toString());
        } catch (Exception e) {
            //ed.noteException(path, null, e);
            result.append(e.toString()).append("\n");
        } finally {
            if (d != null) d.close();
        }

        return result.toString();
    }

    public static class StringDumper extends StreamDumper {
        private StringWriter sw = new StringWriter();

        public StringDumper(TypeUsageInformation typeUsageInformation, Options options,
                            IllegalIdentifierDump illegalIdentifierDump) {
            super(typeUsageInformation, options, illegalIdentifierDump);
        }

        public void addSummaryError(Method paramMethod, String paramString) {

        }

        public void close() {
            try {
                sw.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void write(String source) {
            sw.write(source);
        }

        public String toString() {
            return sw.toString();
        }
    }
}
