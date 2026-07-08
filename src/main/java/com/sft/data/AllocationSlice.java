package com.sft.data;

/** One pie-chart wedge: a label, its summed amount, and its % of the chart's total. */
public record AllocationSlice(String label, double amount, double percentage) {}
