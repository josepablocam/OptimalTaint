package com.github.OptimalTaint;

import java.util.List;


/**
 * Generic interface used to create different instrumentation strategies
 */
public interface Instrumenter {
    public List<String> instrument(ProgramState p, List<String> program);
}
