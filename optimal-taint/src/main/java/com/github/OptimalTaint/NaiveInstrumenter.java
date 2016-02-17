package com.github.OptimalTaint;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


/**
 * The naive instrumentation strategy, which instruments every variable at creation time
 * Currently implemented as an ugly regex match for simplicity purposes
 */
public class NaiveInstrumenter implements Instrumenter {
    static final String VAR_NAME_STUB = "x_";
    static final Pattern varDefineStart = Pattern.compile("^int\\s+" + VAR_NAME_STUB + ".*");
    static final String assignmentOp = "=";

    public boolean isVarDefinition(String code) {
        return varDefineStart.matcher(code.trim()).find();
    }

    public String getLVal(String code) {
        String typeAnnotated = code.split(assignmentOp)[0];
        return typeAnnotated.replace("int", "").replaceAll("\\s+", "");
    }

    public String getRVal(String code) {
        return code.split(assignmentOp)[1].replaceAll("[;\n]", "").trim();
    }

    public String modify(String codeFragment) {
        String lval = getLVal(codeFragment);
        String rval = getRVal(codeFragment);
        String code = "int " + lval + " = ";
        // instrumnent from start, so we can track dependencies
        code += "MultiTainter.taintedInt(" + rval + ", " + "\"" + lval + "\");\n";
        return code;
    }

    public List<String> instrument(ProgramState p,  List<String> program) {
        List<String> instrumentedProgram = new ArrayList<String>();

        for (String codeFragment : program) {
            if (isVarDefinition(codeFragment)) {
                // instrument where appropriate (i.e. each variable definition)
                codeFragment = modify(codeFragment);
            }
            // add to instrumented version
            instrumentedProgram.add(codeFragment);
        }
        return instrumentedProgram;
    }
}
