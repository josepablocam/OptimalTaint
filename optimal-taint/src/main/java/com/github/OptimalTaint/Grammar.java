package com.github.OptimalTaint;

import java.util.Map;

/**
 * Generic abstract class for grammars to extend, just returns probability distribution over rules
 */
public abstract class Grammar {
    Map<Production, Double> rules;
    public Map<Production, Double> getRules() {
        return this.rules;
    }
}
