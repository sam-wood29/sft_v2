package com.sft;

import com.sft.data.Holding;
import com.sft.data.HoldingsRepository;
import com.sft.data.HoldingsUnavailableException;
import java.util.Comparator;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The HOLDINGS page: current positions read live from finance-ingest's
 * finance.db via HoldingsRepository. Kept separate from Pages.java since
 * it has real logic (three render states) rather than one static label.
 */
public final class HoldingsPage implements Page {

    private final HoldingsRepository repository;

    public HoldingsPage(HoldingsRepository repository) {
        this.repository = repository;
    }

    @Override
    public Node content() {
        try {
            List<Holding> holdings = repository.currentHoldings();
            return holdings.isEmpty() ? emptyState() : populated(holdings);
        } catch (HoldingsUnavailableException e) {
            System.err.println("[holdings] " + e.getMessage());
            return errorState(e.getMessage());
        }
    }

    private Node populated(List<Holding> holdings) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(16));

        Label asOf = new Label("As of " + holdings.get(0).asOf());
        asOf.getStyleClass().add("muted-text");
        box.getChildren().add(asOf);

        holdings.stream()
            .sorted(
                Comparator.comparing(Holding::institutionName)
                    .thenComparing(Holding::accountName)
                    .thenComparing(h -> h.tickerSymbol() == null ? "" : h.tickerSymbol())
            )
            .forEach(h -> box.getChildren().add(row(h)));

        return box;
    }

    private Node row(Holding h) {
        Label label = new Label(
            h.hasResolvedTicker() ? h.tickerSymbol() : h.securityName()
        );
        label.getStyleClass().add("holdings-ticker");

        Label sub = new Label(h.institutionName() + " · " + h.accountName());
        sub.getStyleClass().add("muted-text");

        VBox left = new VBox(2, label, sub);

        Label value = new Label(String.format("%.2f %s", h.currentValue(), h.currency()));
        Label pnl = new Label(String.format("%+.2f", h.unrealizedPnl()));
        pnl.getStyleClass().add(h.unrealizedPnl() < 0 ? "pnl-negative" : "pnl-positive");
        VBox right = new VBox(2, value, pnl);
        right.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, left, spacer, right);
        row.getStyleClass().add("holdings-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node emptyState() {
        Label label = new Label("No holdings yet");
        label.getStyleClass().add("muted-text");
        VBox box = new VBox(label);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private Node errorState(String message) {
        Label label = new Label("Holdings unavailable — " + message);
        label.getStyleClass().add("error-text");
        label.setWrapText(true);
        label.setMaxWidth(600);
        VBox box = new VBox(label);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        return box;
    }
}
