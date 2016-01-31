package com.github.OptimalTaint;

/**
 * A simple utility class to hold tuples
 * @param <T1>
 * @param <T2>
 */
public class Tuple2<T1, T2> {
    final T1 e1;
    final T2 e2;
    public Tuple2(T1 e1, T2 e2) {
        this.e1 = e1;
        this.e2 = e2;
    }
}
