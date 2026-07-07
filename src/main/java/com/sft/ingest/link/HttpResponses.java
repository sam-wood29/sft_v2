package com.sft.ingest.link;

import com.sft.ingest.sync.PlaidJson;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Tiny shared JSON-response writer, used by both HTTP handlers in this package. */
final class HttpResponses {

    static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = PlaidJson.dump(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private HttpResponses() {}
}
