package com.github.OptimalTaint;

import java.util.List;
import java.util.regex.Pattern;


/**
 * The naive instrumentation strategy, which instruments every variable at creation time
 * Currently implemented as an ugly regex match for simplicity purposes
 */
public class NaiveInstrumenter implements Instrumenter {
    Pattern varDefineStart = Pattern.compile("int.*");
    String assignmentOp = "=";

    public boolean isVarDefinition(String code) {
        return varDefineStart.matcher(code).find();
    }

    public String getLVal(String code) {
        String typeAnnotated = code.split(assignmentOp)[0];
        String lval = typeAnnotated.replace("int", "").replaceAll("\\s+", "");
        return lval;
    }

    public String getRVal(String code) {
        return code.split(assignmentOp)[1];
    }

    public String modify(String codeFragment) {
        String lval = getLVal(codeFragment);
        String rval = getRVal(codeFragment);
        String code = "int " + lval + " = ";
        // instrumnent from start, so we can track dependencies
        code += "MultiTainter.getTaintedInt(0, " + "\"" + lval + "\");\n";
        // assign original definition nwo that we have instrumented
        code += lval + " = " + rval;
        return code;
    }

    public List<String> instrument(ProgramState p,  List<String> program) {
        for (int i = 0 ; i < program.size(); i++) {
            String codeFragment = program.get(i);

            if (isVarDefinition(codeFragment)) {
                String instrumentedCode =  modify(codeFragment);
                // replace code with instrumented version
                program.set(i, instrumentedCode);
            }
        }

        return program;
    }
}
