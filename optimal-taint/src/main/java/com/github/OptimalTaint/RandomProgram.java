package com.github.OptimalTaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Generates a random program from productions drawn from a specified probability distribution
 * (conditional on LHS of production)
 */
public class RandomProgram {
    // Map from production enumeration names to probability (used to draw rules according to
    // distribution
    Map<Production, Double> probabilities;
    // limit on randomly generated integers [0, MAX_INT)
    static final int MAX_INT = 100;
    // variable names are x_<integer>
    static final String VAR_NAME_STUB = "x_";
    // loop variable names are LOOP_VAR_<integer>
    static final String LOOP_VAR_NAME_STUB = "LOOP_VAR";
    // we use this to count loops and assign unique loop variable names
    int LOOP_COUNTER = 0;
    // we use a counter to count the number of actual commands executed in a program
    static final String COUNTER = "COUNTER";

    /*
    HELPER UTILITIES
     */
    /**
     * Pick a production according to the probability distribution associated with these rules
     * @param p Program state encapsulating various stateful pieces of information
     * @param prods list of productions to consider
     * @return production selected by random process
     */
    private Production pickProduction(ProgramState p, List<Production> prods) {
        List<Double> probs = getProbabilities(prods);
        double accumulator = 0.0;
        int n = probs.size();
        double draw = p.rng.nextDouble();
        int chosenIndex = 0;

        for (int i = 0; i < n; i++) {
            accumulator += probs.get(i);
            if (draw <= accumulator) {
                chosenIndex = i;
                break;
            }
        }

       return prods.get(chosenIndex);
    }

    /**
     * Validate that probability distribution is normalized (sum of p_i = 1 and all values are >= 0)
     * @param probabilities list of probabilities
     * @return true if the distribution is valid
     */
    private boolean validProbabilityDistribution(List<Double> probabilities) {
        double accumulator = 0.0;
        boolean allNonNegative = true;

        for (Double prob : probabilities) {
            accumulator += prob;
            allNonNegative &= prob >= 0.0;
        }

        // allow some noise in summing
        double tolerance = 0.01;
        return accumulator >= (1.0 - tolerance) && accumulator <= (1.0 + tolerance) && allNonNegative;
    }

    /**
     * Given a list of production identifiers, return
     * list of probabilities, such that position i of return value contains probability associated
     * with production represented by position i in `prods`
     * @param prods List of productions
     * @return List of probabilities (order relevant)
     */
    private List<Double> getProbabilities(List<Production> prods) {
        List<Double> probs = new ArrayList<Double>();

        // extract probabilites in the same order
        for(Production prod : prods) {
            probs.add(probabilities.get(prod));
        }

        // a quick check to make sure probabilities are normalized locally
        assert(validProbabilityDistribution(probs));

        return probs;
    }


    /**
     * Create a counter to keep track of number of branches taken in program
     * @return
     */
    private String initCounter() {
        return "long " + COUNTER + " = 0;\n";
    }

    /**
     * Useful to count number of branches actually taken in program and avoid
     * complete code elimination by optimizer (otherwise benchmarking can be useless)
     * @return code
     */
    private String increaseCounter() {
        return "\n"+ COUNTER +"++;\n";
    }

    /*
    CORE-SYNTAX (TIGHT TAINT TRACKING PAPER)
    ENCODED AS A SET OF RECURSIVE FUNCTIONS
     */

    /**
     * Create an initial environment by having `numVars` top-level variable declarations.
     * This means there is really no sense of scope in the programs, which aligns
     * with the paper's core syntax. Initial values are bounded by a multiple of the number
     * of variables (otherwise hard to obtain interesting interactions)
     * @param p Program state encoding stateful information
     * @param numVars Number of variables to declare
     * @return
     */
    private List<String> initEnvironment(ProgramState p, int numVars) {
        List<String> code = new ArrayList<String>();
        code.add("// initial environment (r0)\n");
        for (int i = 0; i < numVars; i++) {
            String varName = VAR_NAME_STUB + p.getVariableCounter();
            String def = "int " + varName + " = " + p.rng.nextInt(numVars * MAX_INT) + ";\n";
            code.add(def);
            p.pushVariableName(varName);
            p.increaseVariableCounter();
        }
        return code;
    }


    /**
     * c ::= x:= aexp | if .... | while ... | skip | c; c
     * @param p Program state encoding stateful information
     * @return top-level commands code
     */
    private List<String> root(ProgramState p) {
        List<Production> nextStates = Arrays.asList(
                Production.AASGN, Production.CONDITION, Production.LOOP,
                Production.NOP, Production.COMPOSITION
        );

        Production chosenProduction = pickProduction(p, nextStates);
        List<String> code = new ArrayList<String>();

        switch(chosenProduction) {
            case AASGN:
               code.add(aasgn(p));
                break;
            case CONDITION:
                code.addAll(condition(p));
                break;
            case LOOP:
                // currently using a finite loop function to avoid
                // non-termination and javac errors for unreachable code.
                code.addAll(finiteLoop(p));
                break;
            case NOP:
                code.add(nop(p));
                break;
            case COMPOSITION:
                // composition can lead to infinite loops, so stop generating code if done
                if(!p.done()) {
                    code.addAll(composition(p));
                }
                break;
        }

        return code;
    }

    /**
     * (aasgn) x := aexp
     * Assigns a value to one of the pre-defined (top-level) variables. The value is a function
     * of available variables and integers.
     * @param p Program state representing stateful information
     * @return new variable definition code
     */
    private String aasgn(ProgramState p) {
        // Randomly pick variable to assign to
        String lval = var(p);
        List<String> rval = aexp(p);
        String assignment = lval + " = ";
        for (String fragment : rval) {
            assignment += fragment;
        }
        assignment += ";\n";
        // assignment is non-skip, meaningful, command
        assignment += increaseCounter();
        p.increaseCountNonSkip();
        return assignment;
    }

    /**
     * (condition) if (bexp) { c } else { c }
     * @param p Program state representing stateful information
     * @return if-else code
     */
    private List<String> condition(ProgramState p) {
        List<String> code = new ArrayList<String>();
        code.add("if (");
        code.addAll(bexp(p));
        code.add("){\n");
        // opening scope according to braces
        p.pushScope();
        // recursively generate code for body
        code.addAll(root(p));
        // the scope is now closed
        p.popScope();
        code.add("} else {\n");
        p.pushScope();
        code.addAll(root(p));
        p.popScope();
        code.add("}\n");
        // meaningful command
        p.increaseCountNonSkip();
        return code;
    }

    /**
     * (loop) while (bexp) { c }
     * @param p Program state representing stateful information
     * @return while-loop code
     */
    private List<String> loop(ProgramState p) {
        List<String> code = new ArrayList<String>();
        code.add("while (");
        code.addAll(bexp(p));
        code.add("){\n");
        // open scope inside braces
        p.pushScope();
        code.addAll(root(p));
        p.popScope();
        code.add("}\n");
        // meaningful command
        p.increaseCountNonSkip();
        return code;
    }

    /**
     * (nop) skip
     * @param p Program state representing stateful information
     * @return skip-comment in code
     */
    private String nop(ProgramState p) {
        // not a meaningful command, hence no call to p.increaseCountNonSkip()
        return "//skip\n";
    }

    /**
     * (composition) c := c, c
     * @param p Program state representing stateful information
     * @return composition code
     */
    private List<String> composition(ProgramState p) {
        List<String> code = new ArrayList<String>();
        code.addAll(root(p));
        code.addAll(root(p));
        // No call to p.increaseCountNonSkip() as the two c will increase this if appropriate
        // in their respective calls
        return code;
    }

    /**
     * aexp :: = n | y | y aop z
     * @param p Program state representing stateful information
     * @return aexp code (integer, variable, binary operation between variables)
     */
    private List<String> aexp(ProgramState p) {
        List<Production> nextStates = Arrays.asList(Production.INT, Production.VAR, Production.ABINOP);
        Production chosenProduction = pickProduction(p, nextStates);
        List<String> code = new ArrayList<String>();

        switch(chosenProduction) {
            case INT:
                code.add(randInt(p));
                break;
            case VAR:
                 code.add(var(p));
                break;
            case ABINOP:
                code.add(aop(p));
                break;
        }
        return code;
    }

    /**
     * (n) Generates a random integer literal between [1, MAX_INT]
     * @param p Program state representing stateful information
     * @return random integer code
     */
    private String randInt(ProgramState p) {
        int randomInt = 1 + p.rng.nextInt(MAX_INT);
        return Integer.toString(randomInt);
    }

    /**
     * (y) Retrieves an in-scope variable name. If no such variable exists (meaning, no
     * aasgn commands have taken place), it returns a random integer instead.
     * @param p Program state representing stateful information
     * @return variable reference (r-val) code, or random integer if no variable available in-scope
     */
    private String var(ProgramState p) {
        List<String> inScopeVars = p.getInScopeVars();
        if (inScopeVars.isEmpty()) {
            // we don't have a variable, so return a random int for now
            return randInt(p);
        } else {
            // they are all equally likely
            int chosenIndex = p.rng.nextInt(inScopeVars.size());
            return inScopeVars.get(chosenIndex);
        }
    }

    /**
     * aop ::= + | - | * | / | %
     * @param p Program state representing stateful information
     * @return integer binary operator
     */
    private String aop(ProgramState p) {
        List<Production> nextStates = Arrays.asList(
                Production.PLUS, Production.MINUS, Production.TIMES,
                Production.DIV, Production.MOD
        );
        Production chosenProduction = pickProduction(p, nextStates);
        String arg1 = var(p);
        String arg2 = var(p);
        switch(chosenProduction) {
            case PLUS:
                return arg1 + " + " + arg2;
            case MINUS:
                return arg1 +  " - " + arg2;
            case TIMES:
                return arg1 + " * " + arg2;
            case DIV:
            case MOD:
                return safeOp(chosenProduction, arg1, arg2);
            default:
                return "UNDEFINED AEOP";
        }
    }

    /**
     * bexp ::= true | false | x bop y | bexp \lor bexp | bexp \land bexp | \neg bexp
     * @param p Program state representing stateful information
     * @return Boolean constant, binary boolean operation, unary boolean operation code
     */
    private List<String> bexp(ProgramState p) {
        List<Production> nextStates = Arrays.asList(
                Production.TRUE, Production.FALSE, Production.BBINOP,
                Production.LOR, Production.LAND, Production.NOT
        );
        Production chosenProduction = pickProduction(p, nextStates);
        List<String> code = new ArrayList<String>();

        switch(chosenProduction) {
            case TRUE:
                code.add("true");
                break;
            case FALSE:
                code.add("false");
                break;
            case BBINOP:
                code.add(var(p));
                code.add(bop(p));
                code.add(var(p));
                break;
            // we parenthesize expressions using \lor \land \neg for clarity-sake
            case LOR:
                code.add("(");
                code.addAll(bexp(p));
                code.add(") || (");
                code.addAll(bexp(p));
                code.add(")");
                break;
            case LAND:
                code.add("(");
                code.addAll(bexp(p));
                code.add(") && (");
                code.addAll(bexp(p));
                code.add(")");
                break;
            case NOT:
                code.add("!(");
                code.addAll(bexp(p));
                code.add(")");
                break;
        }
        return code;
    }

    /**
     * bop ::= < | > | <= | >= | != | ==
     * @param p Program state representing stateful information
     * @return binary boolean operators
     */
    private String bop(ProgramState p) {
        List<Production> nextStates = Arrays.asList(
                Production.LT, Production.GT, Production.LE,
                Production.GE, Production.NEQ, Production.EQ
        );
        Production chosenProduction = pickProduction(p, nextStates);
        switch(chosenProduction) {
            case LT:
                return "<";
            case GT:
                return ">";
            case LE:
                return "<=";
            case GE:
                return ">=";
            case NEQ:
                return "!=";
            case EQ:
                return "==";
            default:
                return "UNDEFINED BOP";
        }
    }


    /**
     * javac complains (error) about unreachable code, which can often happen with randomly
     * generated conditions (not to mention that could also result in non-termination for loops).
     * This function generates "finite-loops" by using a loop variable (a unique variable is
     * created for each loop). The loop variable is originally defined in terms of an aexp, so
     * it can be a function of existing top-level variables.
     * The loop conditions tests if the variable is less than some randomly
     * generated integer threshold. Code is added to increment the loop variable by 1 in each
     * iteration, guaranteeing termination
     * @param p Program state
     * @return code needed for a finite loop
     */
    private List<String> finiteLoop(ProgramState p) {
        List<String> code = new ArrayList<String>();

        // Create a loop variable
        String loopVar = LOOP_VAR_NAME_STUB + "_" + LOOP_COUNTER++;
        String initLoopVar = "int " + loopVar + " = ";
        for (String fragment : aexp(p)) {
            initLoopVar += fragment;
        }
        initLoopVar += ";\n";
        // generate condition and increment code
        List<String> conditionAndVar = loopControlFlow(p, loopVar);

        code.add(initLoopVar);
        code.add("while (");
        code.add(conditionAndVar.get(0));
        code.add("){\n");
        // open scope inside braces
        p.pushScope();
        code.addAll(root(p));
        code.add(conditionAndVar.get(1));
        p.popScope();
        code.add(increaseCounter());
        code.add("}\n");
        // meaningful command
        p.increaseCountNonSkip();
        return code;
    }

    /**
     * Returns a condition for a loop (with termination), and the code needed to increment
     * the loop variable
     * @param p Program state
     * @param varName variable name to be used for loop variable
     * @return length 2 list, position 0 holds condition code, position 1 increment code
     */
    private List<String> loopControlFlow(ProgramState p, String varName) {
        String conditionCode = varName + " < " + randInt(p);
        String changeVar = varName + "++;";
        return Arrays.asList(conditionCode, changeVar);
    }

    /**
     * We have issues with division by zero in randomly generated code. Therefore,
     * for now, I'm using a function (which we dump a definition for into the wrapper code)
     * that checks that denominator is non-zero for divisions and mods
     * @param op
     * @param arg1
     * @param arg2
     * @return
     */
    private String safeOp(Production op, String arg1, String arg2) {
        switch(op) {
            case DIV:
                return "safeDiv(" + arg1 + ", " + arg2 + ")";
            case MOD:
                return "safeMod(" + arg1 + ", " + arg2 + ")";
            default:
                return "UNDEFINED SAFEOP";
        }
    }

    private String defineSafeOps() {
        String divDefinition = "\tpublic static int safeDiv(int x, int y)" +
                "{if (y != 0){ return x / y; } else { return x / (y + 1); } }\n";
        String modDefinition = "\tpublic static int safeMod(int x, int y)" +
                "{if (y != 0){ return x % y; } else { return x % (y + 1); } }\n";
        return divDefinition + modDefinition;
    }

    /*
    USER API
     */
    /**
     * Sample a random program according to probabilistic context free grammar. If recursive
     * calls finish and we still don't have the minimum number of non skip commands,
     * it will continue to sample. Thus the programs generated may exceed the desired length.
     * @param p Program state representing stateful information
     * @param numVars Number of variables in environment
     * @return The body of a sampled program (not compilable until wrapped in class and main)
     */
    public List<String> sample(ProgramState p, int numVars) {
        List<String> program = new ArrayList<String>();
        // initialize our scope information
        p.pushScope();
        // initialize variable declarations
        program.addAll(initEnvironment(p, numVars));
        // repeatedly sample commands as needed
        while (!p.done()) {
            List<String> codeFragment = root(p);
            program.addAll(codeFragment);
        }
        // clean up scope information
        p.popScope();
        return program;
    }

    /**
     * Add necessary import statements
     * @param typeOfInstrumentation name of instrumentation [none, naive]
     * @return necessary import statements for self-contained program
     */
    public String importStatements(String typeOfInstrumentation) {
        if (typeOfInstrumentation != null && typeOfInstrumentation.equals("naive")) {
            return "import edu.columbia.cs.psl.phosphor.runtime.MultiTainter;\n";
        } else {
            return "";
        }
    }


    /**
     * Wrap a sampled program body into a generic class name, wrap code into a method
     * called run, which returns a long (we want a return value to avoid eliminating code
     * in optimization), and add a main method so can be run standalone
     * @param className class name to use for wrapping
     * @param code sample code
     * @param typeOfInstrumentation name of instrumentation type (for import statements)[none, naive]
     * @return compilable java code
     */
    public String assembleCode(String className, List<String> code, String typeOfInstrumentation) {
        String program = importStatements(typeOfInstrumentation);
        program += "class " + className + " {\n";
        program += defineSafeOps();

        // run method wrapper
        program += "\tpublic static long run(){\n";
        program += "\t" + initCounter();
        StringBuilder body = new StringBuilder();
        for (String fragment : code) {
            body.append(fragment);
        }
        program += body;
        program += "\n\treturn "+ COUNTER + ";\n";
        program += "\t}\n";

        // main method wrapper
        program += "\tpublic static void main(String[] args){\n";
        program += "\tSystem.out.println(" + className + ".run());\n";
        program += "\t}\n}";
        return program;
    }

    /**
     * A Grammar object to use in our random program generation
     * @param grammar
     */
    public RandomProgram(Grammar grammar) {
        this.probabilities = grammar.getRules();
    }
}