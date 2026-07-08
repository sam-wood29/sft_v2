package com.sft.data;

/**
 * A single position, joined for display from finance-ingest's holdings/
 * securities/accounts/items tables. tickerSymbol, costBasis, and
 * currentPrice are nullable — cash positions and some Plaid securities
 * carry no ticker, and cost basis isn't always reported. sector/industry
 * come from Plaid's security raw_json (not every security has them - cash
 * equivalents never do).
 */
public record Holding(
    String institutionName,
    String accountName,
    String tickerSymbol,
    String securityName,
    double quantity,
    Double costBasis,
    double currentValue,
    Double currentPrice,
    String currency,
    String asOf,
    String securityType,
    String sector,
    String industry
) {
    public double unrealizedPnl() {
        return costBasis == null ? 0.0 : currentValue - costBasis;
    }

    public boolean hasResolvedTicker() {
        return tickerSymbol != null && !tickerSymbol.isBlank();
    }

    public boolean isCash() {
        return "cash".equalsIgnoreCase(securityType);
    }
}
