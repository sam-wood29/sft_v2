package com.sft.ingest.check;

public record CheckResult(String name, CheckStatus status, String summary) {}
