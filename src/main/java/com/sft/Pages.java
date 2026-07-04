package com.sft;

import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * The carousel's pages live here — one method per page. Edit the bodies to
 * change what each page shows; this file has nothing to do with the carousel's
 * sliding/wrapping (that's all in App.java).
 *
 * Each page is a Page (a functional interface — Node content()): a factory that
 * builds a FRESH node every time it's called. That matters because the carousel
 * builds invisible clones of the
 * first and last pages for its seamless wrap, and a JavaFX node can't appear in
 * the scene twice. As long as each method returns a new node, cloning is free.
 *
 * App.java wraps whatever you return here in a full-size, centered, dark frame,
 * so you only need to return the content (a label, an HBox of widgets, etc.).
 *
 * Still don't fully understand - sw
 */
public final class Pages {

    // The page order. Add, remove, or reorder entries here.
    public static final List<Page> ALL = List.of(
        Pages::page1,
        Pages::page2,
        Pages::page3,
        Pages::page4,
        Pages::page5
    );

    /* *** Sketch of what to put on seperate pages ->

   - info
        - info logs (important shit)
   - news
   - focused here (?)
        - focused news, how it may impact holdings/ strategy
        - click to bring up chart, or chart on right, articles on left, navigation within articles. and summaries on top
            - derived price impact / derived price impact gradient(s) over time(s) <- interesting
   - holdings (focused)
        - view performance per holding
            - Custom views
                - position(s) over times(s) pnl/total capital
   - allocation
        - custom views
                - position(s) allocation(s) (allocations is interesting, not sure on point, what could have been) & capitol overtime(s)
                - beta (w what if, if edited allocation)
                - what if analysis:
                    - impact on positin p if lever up/lever down/ hedge
                    - chances of price moving to price p based on gradients (p)
                    - timelines (catalysts and volumes)
   - quick research
            - whats up with this.
   - quick analysis
            comparisons/regressions
            financials/ projections/ aggretated predictions on t (ex. forecasts)
   - debug
        - debug logs (what ran, what pulled when)

    That is probably enough for now..... info, debug, holdings, and allocation seem like the reasonable starting place
    */

    private static Node page1() {
        return bigText("INFO");
    }

    private static Node page2() {
        return bigText("NEWS");
    }

    private static Node page3() {
        return bigText("HOLDINGS");
    }

    private static Node page4() {
        return bigText("ALLOCATION");
    }

    private static Node page5() {
        return bigText("DEBUG");
    }

    // Shared helper: one big centered label. Replace per page with real content.
    // Nice. Claude built a class for placeholder text within a node
    private static Node bigText(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 64px; -fx-font-weight: bold;");
        VBox box = new VBox(label);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private Pages() {} // static-only; never instantiated; not sure why thats important. -sw
}
