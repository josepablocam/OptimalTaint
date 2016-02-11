package com.github.OptimalTaint;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Create a grammar from a file. Useful to specify distributions in a file
 */
public class ParsedGrammar extends Grammar {
    /**
     * Parse a production name
     * @param line
     * @return
     * @throws Exception
     */
    private Production parseProduction(String line) throws Exception {
        String prodName = line.split("\\s+")[0].trim().toLowerCase();
        switch (prodName) {
            case "aasgn":
                return Production.AASGN;
            case "condition":
                return Production.CONDITION;
            case "loop":
                return Production.LOOP;
            case "nop":
                return Production.NOP;
            case "composition":
                return Production.COMPOSITION;
            case "int":
                return Production.INT;
            case "var":
                return Production.VAR;
            case "abinop":
                return Production.ABINOP;
            case "plus":
            case "+":
                return Production.PLUS;
            case "minus":
            case "-":
                return Production.MINUS;
            case "times":
            case "*":
                return Production.TIMES;
            case "div":
            case "/":
                return Production.DIV;
            case "mod":
            case "%":
                return Production.MOD;
            case "true":
                return Production.TRUE;
            case "false":
                return Production.FALSE;
            case "bbinop":
                return Production.BBINOP;
            case "lor":
            case "||":
            case "|":
                return Production.LOR;
            case "land":
            case "&&":
            case "&":
                return Production.LAND;
            case "not":
            case "!":
                return Production.NOT;
            case "lt":
            case "<":
                return Production.LT;
            case "gt":
            case ">":
                return Production.GT;
            case "le":
            case "<=":
                return Production.LE;
            case "ge":
            case ">=":
                return Production.GE;
            case "ne":
            case "!=":
            case "<>":
                return Production.NEQ;
            case "eq":
            case "==":
            case "=":
                return Production.EQ;
            default:
                throw new Exception("Undefined Production Symbol");
        }
    }

    /**
     * Parse probability associated with production
     * @param line
     * @return
     * @throws Exception
     */
    private double parseProbability(String line) throws Exception {
        return Double.parseDouble(line.split("\\s+")[1].trim());
    }

    /**
     * Create a grammar by parsing a file for probabilities
     * @param fileName File with production and probability per line, separated by white space
     */
    public ParsedGrammar(String fileName) {
        // parsed
        Path path = Paths.get(fileName);
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            Map<Production, Double> rules = new HashMap<Production, Double>();
            for (String line : lines) {
                if (!line.isEmpty()) {
                    // split into production name and probability, add to map
                    Production prod = parseProduction(line);
                    double prob = parseProbability(line);
                    rules.put(prod, prob);
                }
            }
            // set rules to parsed data
            this.rules = rules;
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}
