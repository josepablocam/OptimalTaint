package com.github.OptimalTaint;

import java.util.HashMap;
import java.util.Map;

/**
 * Predefined probabilistic grammar, with uniform conditional probabilities (conditional on LHS)
 */
public class UniformGrammar extends Grammar {
    public UniformGrammar() {
        Map<Production, Double> probs = new HashMap<Production, Double>();
        // c
        probs.put(Production.AASGN, 1.0 / 5.0);
        probs.put(Production.CONDITION, 1.0 / 5.0);
        probs.put(Production.LOOP, 1.0 / 5.0);
        probs.put(Production.NOP, 1.0 / 5.0);
        probs.put(Production.COMPOSITION, 1.0 / 5.0);
        // aexp
        probs.put(Production.INT, 1.0 / 3.0);
        probs.put(Production.VAR, 1.0 / 3.0);
        probs.put(Production.ABINOP, 1.0 / 3.0);
        // aop
        probs.put(Production.PLUS, 1.0 / 5.0);
        probs.put(Production.MINUS, 1.0 / 5.0);
        probs.put(Production.TIMES, 1.0 / 5.0);
        probs.put(Production.DIV, 1.0 / 5.0);
        probs.put(Production.MOD, 1.0 / 5.0);
        // bexps
        probs.put(Production.TRUE, 1.0 / 6.0);
        probs.put(Production.FALSE, 1.0 / 6.0);
        probs.put(Production.BBINOP, 1.0 / 6.0);
        probs.put(Production.LOR, 1.0 / 6.0);
        probs.put(Production.LAND, 1.0 / 6.0);
        probs.put(Production.NOT, 1.0 / 6.0);
        // bop
        probs.put(Production.LT, 1.0 / 6.0);
        probs.put(Production.GT, 1.0 / 6.0);
        probs.put(Production.LE, 1.0 / 6.0);
        probs.put(Production.GE, 1.0 / 6.0);
        probs.put(Production.NEQ, 1.0 / 6.0);
        probs.put(Production.EQ, 1.0 / 6.0);

        this.rules = probs;
    }
}
