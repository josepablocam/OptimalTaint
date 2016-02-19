package com.github.OptimalTaint;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Stack;

/**
 * ProgramState is a class useful to keep track of stateful information needed when generating
 * random programs. This information includes things like an RNG (for reproducibility),
 * a symbol table for variable names in scope, a variable counter to generate fresh variable names,
 * amongst others.
 */
public class ProgramState {
    private static final int DEFAULT_LEN = 10;
    private static final long DEFAULT_SEED = 10;

    // used in random program generation
    Stack<List<String>> symbolTable;
    int countNonSkip;
    int minNonSkip;
    int variableCounter;
    Random rng;

    List<Tuple2<Boolean, Integer>> branchPoints;

    /**
     * Performs a check to determine if we have generated enough non-skip commands
     * for our random program
     * @return
     */
    public boolean done() {
        return countNonSkip >= minNonSkip;
    }

    /**
     * Increase count of non-skip comands
     */
    public void increaseCountNonSkip() {
        countNonSkip++;
    }

    /**
     * Create a new scope for variables
     */
    public void pushScope() {
        List<String> newScope = new ArrayList<String>();
        symbolTable.push(newScope);
    }

    /**
     * Remove inner-most scope for variables
     */
    public void popScope() {
        symbolTable.pop();
    }

    /**
     * Obtain list of all variables available and in-scope
     * @return
     */
    public List<String> getInScopeVars() {
        List<String> inScopeVars = new ArrayList<String>();
        for(List<String> scope : symbolTable) {
            inScopeVars.addAll(scope);
        }
        return inScopeVars;
    }

    /**
     * Return variable counter (used to keep track of fresh variable name)
     * @return
     */
    public int getVariableCounter() {
        return variableCounter;
    }

    /**
     * Increase variable counter
     */
    public void increaseVariableCounter() {
        variableCounter++;
    }

    /**
     * Push a variable name into inner-most scope
     * @param name
     */
    public void pushVariableName(String name) {
        List<String> currentScope = symbolTable.peek();
        currentScope.add(name);
    }

    /**
     * Creates a Tuple2 representing a branching point and adds to the internal list of such
     * points
     * @param b boolean representing the branch condition
     * @param pt integer representing the counter of such points
     */
    public void addBranchPoint(boolean b, int pt) {
        Tuple2<Boolean, Integer> branchPoint = new Tuple2<Boolean, Integer>(b, pt);
        branchPoints.add(branchPoint);
    }

    /**
     * Create a program state instance with a count of non-skip commands and given seed
     * for random number generation
     * @param minNonSkip minimum number of non-skip commands
     * @param seed seed for random number generation
     */
    public ProgramState(int minNonSkip, long seed) {
        this.minNonSkip = minNonSkip;
        this.countNonSkip = 0;
        this.variableCounter = 0;
        this.symbolTable = new Stack<List<String>>();
        this.branchPoints = new ArrayList<Tuple2<Boolean, Integer>>();
        this.rng = new Random(seed);
    }


    public ProgramState(int minNonSkip) {
        this(minNonSkip, DEFAULT_SEED);
    }
    public ProgramState() {
        this(DEFAULT_LEN, DEFAULT_SEED);
    }

}
