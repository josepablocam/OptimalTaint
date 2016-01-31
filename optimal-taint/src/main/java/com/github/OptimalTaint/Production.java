package com.github.OptimalTaint;

/**
 * Define productions in Core-Syntax of Tight Taint Tracking
 */
public enum Production {
    // c productions
    AASGN, CONDITION, LOOP, NOP, COMPOSITION,
    // aexp
    INT, VAR, ABINOP,
    // aop
    PLUS, MINUS, TIMES, DIV, MOD,
    // bexps
    TRUE, FALSE, BBINOP,
    // lop
    LOR, LAND, NOT,
    // bop
    LT, GT, LE, GE, NEQ, EQ
}
