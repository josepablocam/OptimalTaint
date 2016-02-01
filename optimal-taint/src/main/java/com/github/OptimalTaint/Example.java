package com.github.OptimalTaint;

import java.util.List;

/**
 * Simple example showing how to use classes created
 */
public class Example {
    public static void main(String[] args) {
        // our random program follows a uniform distribution for each non-terminal
        Grammar g = new UniformGrammar();
        // Similarly we could have parsed in the probabilities from a file
        // using pairs of rule name and probability
        // Grammar g = new ParsedGrammar(fileName);
        RandomProgram r = new RandomProgram(g);

        // Jose: An analyzer would go here, allowing the instrumentation
        // strategy to be determined. My current thoughts are perhaps
        // doing this from information already collected (or which can be collected)
        // by ProgramState (if only to have a sanity check for a separate analyzer),
        // or by a separate analyzer that looks at the code after (rather than during)
        // random generation
        Instrumenter i;
        if (args.length > 0 && args[0].equalsIgnoreCase("naive")) {
            i = new NaiveInstrumenter();
        } else {
            i = new NoInstrumenter();
        }
        // generate code according to our distribution
        // create a new program state with a mininmum of 2 non-skip lines (might result in more)
        // and a seed for reproducibility purposes
        ProgramState p = new ProgramState(2, 10);
        List<String> code = r.sample(p);
        // perform instrumentation
        List<String> instrumented_code = i.instrument(p, code);
        // wrap code into necessary java boiler plate
        // add in import statements etc for Phosphor
        String javaCode = r.assembleCode("A", instrumented_code);
        System.out.println(javaCode);
    }

}
