package com.sft;

import com.sft.data.AllocationAggregator;
import com.sft.data.AllocationSlice;
import com.sft.data.Holding;
import com.sft.data.HoldingsRepository;
import com.sft.data.HoldingsUnavailableException;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * The ALLOCATION page: pie charts over current holdings - by position
 * (including cash), by sector, by industry (all current-value weighted),
 * and by position at cost basis (cash excluded - it has no purchase cost).
 * Separate charts for now, not a combined/overlaid view.
 */
public final class AllocationPage implements Page {

    private final HoldingsRepository repository;

    public AllocationPage(HoldingsRepository repository) {
        this.repository = repository;
    }

    @Override
    public Node content() {
        try {
            List<Holding> holdings = repository.currentHoldings();
            return holdings.isEmpty() ? emptyState() : populated(holdings);
        } catch (HoldingsUnavailableException e) {
            System.err.println("[allocation] " + e.getMessage());
            return errorState(e.getMessage());
        }
    }

    // Screen is 800x400 (Pi 7" DSI). Each chart gets nearly the full
    // viewport width/height so one scroll position shows one chart at
    // (close to) full size, instead of a small chart floating in a sea of
    // unused margin.
    private static final double CHART_WIDTH = 760;
    private static final double CHART_HEIGHT = 380;

    private Node populated(List<Holding> holdings) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(8));
        box.setAlignment(Pos.TOP_CENTER);

        box.getChildren().add(chart("Current value by position", AllocationAggregator.byPosition(holdings)));
        box.getChildren().add(chart("Current value by sector", AllocationAggregator.bySector(holdings)));
        box.getChildren().add(chart("Current value by industry", AllocationAggregator.byIndustry(holdings)));
        box.getChildren().add(
            chart("Cost basis by position (excl. cash)", AllocationAggregator.byPositionCostBasis(holdings))
        );

        return box;
    }

    private Node chart(String title, List<AllocationSlice> slices) {
        if (slices.isEmpty()) {
            Label empty = new Label(title + " — no data");
            empty.getStyleClass().add("muted-text");
            return empty;
        }
        PieChart chart = new PieChart();
        chart.setTitle(title);
        // Legend would just repeat what's already on each slice's label
        // (name + %) - skip it so the pie itself gets the vertical room.
        chart.setLegendVisible(false);
        chart.setLabelsVisible(true);
        chart.setLabelLineLength(12);
        for (AllocationSlice slice : slices) {
            chart.getData().add(new PieChart.Data(String.format("%s (%.1f%%)", slice.label(), slice.percentage()), slice.amount()));
        }
        chart.setPrefSize(CHART_WIDTH, CHART_HEIGHT);
        chart.setMinSize(CHART_WIDTH, CHART_HEIGHT);
        chart.setMaxSize(CHART_WIDTH, CHART_HEIGHT);
        return chart;
    }

    private Node emptyState() {
        Label label = new Label("No holdings yet");
        label.getStyleClass().add("muted-text");
        VBox box = new VBox(label);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private Node errorState(String message) {
        Label label = new Label("Allocation unavailable — " + message);
        label.getStyleClass().add("error-text");
        label.setWrapText(true);
        label.setMaxWidth(600);
        VBox box = new VBox(label);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        return box;
    }
}
