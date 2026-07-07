package com.sft.ingest.link;

import java.util.List;
import java.util.Map;

/** The two link flows, ported from Python's FLOWS dict. */
public record LinkFlow(List<String> products, List<String> requiredIfSupported) {
    public static final Map<String, LinkFlow> ALL = Map.of(
        "bank", new LinkFlow(List.of("transactions"), List.of("investments", "liabilities")),
        "brokerage", new LinkFlow(List.of("investments"), List.of())
    );
}
