package com.sft.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Pure aggregation over already-fetched holdings - no DB access, so it's
 * unit-testable the same way Carousel.java's logic is kept separate from
 * JavaFX rendering.
 */
public final class AllocationAggregator {

    public static List<AllocationSlice> byPosition(List<Holding> holdings) {
        return group(holdings, h -> h.isCash() ? "Cash" : positionLabel(h), Holding::currentValue);
    }

    public static List<AllocationSlice> bySector(List<Holding> holdings) {
        return group(holdings, h -> h.isCash() ? "Cash" : orUnknown(h.sector()), Holding::currentValue);
    }

    public static List<AllocationSlice> byIndustry(List<Holding> holdings) {
        return group(holdings, h -> h.isCash() ? "Cash" : orUnknown(h.industry()), Holding::currentValue);
    }

    /** Cost-basis-weighted, cash excluded entirely - cash has no purchase cost. */
    public static List<AllocationSlice> byPositionCostBasis(List<Holding> holdings) {
        List<Holding> purchased = holdings.stream().filter(h -> !h.isCash() && h.costBasis() != null).toList();
        return group(purchased, AllocationAggregator::positionLabel, Holding::costBasis);
    }

    private static List<AllocationSlice> group(
        List<Holding> holdings,
        Function<Holding, String> labelFn,
        Function<Holding, Double> valueFn
    ) {
        Map<String, Double> totals = new LinkedHashMap<>();
        double grandTotal = 0;
        for (Holding h : holdings) {
            Double value = valueFn.apply(h);
            if (value == null) {
                continue;
            }
            totals.merge(labelFn.apply(h), value, Double::sum);
            grandTotal += value;
        }
        double total = grandTotal;
        List<AllocationSlice> slices = new ArrayList<>();
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            double pct = total == 0 ? 0 : (entry.getValue() / total) * 100.0;
            slices.add(new AllocationSlice(entry.getKey(), entry.getValue(), pct));
        }
        slices.sort(Comparator.comparingDouble(AllocationSlice::amount).reversed());
        return slices;
    }

    private static String positionLabel(Holding h) {
        return h.hasResolvedTicker() ? h.tickerSymbol() : orUnknown(h.securityName());
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private AllocationAggregator() {}
}
