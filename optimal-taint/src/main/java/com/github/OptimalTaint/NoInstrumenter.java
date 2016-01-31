package com.github.OptimalTaint;

import java.util.List;


/**
 * A dummy instrumenter, which doesn't do anything, but rather returns the code
 * without instrumentation
 */
public class NoInstrumenter implements Instrumenter {
    public List<String>  instrument(ProgramState p,  List<String> program) {
        // just return the program directly
        return program;
    }
}
