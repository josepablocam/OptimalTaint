package com.github.OptimalTaint;

import java.util.List;


/**
 * Generic interface used to create different instrumentation strategies
 */
public interface Instrumenter {
    /**
     * Instrumentation function. Should NOT modify original program, but rather return
     * a new list
     * @param p
     * @param program
     * @return
     */
    public List<String> instrument(ProgramState p, List<String> program);
}
