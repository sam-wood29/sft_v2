package com.sft.ingest.link;

import com.google.gson.JsonObject;
import com.plaid.client.model.CountryCode;
import com.plaid.client.model.LinkTokenCreateRequest;
import com.plaid.client.model.LinkTokenCreateRequestUser;
import com.plaid.client.model.LinkTokenCreateResponse;
import com.plaid.client.model.Products;
import com.plaid.client.request.PlaidApi;
import com.sft.ingest.config.PlaidConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import retrofit2.Response;

/**
 * Local, single-operator Plaid Link web flow. Uses the JDK's built-in
 * HttpServer rather than a servlet container - this is a 3-route, manual,
 * short-lived tool, not a service.
 */
public final class LinkServer {

    public static void runInteractive(PlaidConfig config, PlaidApi client, Path dbPath) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(config.linkHost(), config.linkPort()), 0);
        server.createContext("/", LinkServer::serveIndex);
        server.createContext("/create_link_token", exchange -> createLinkToken(exchange, client, config));
        server.createContext("/exchange", exchange -> ExchangeHandler.handle(exchange, client, dbPath));
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();

        String url = "http://" + config.linkHost() + ":" + config.linkPort();
        System.out.println("sft link · " + url);
        System.out.println("opening browser… (Ctrl-C to stop)");
        openBrowser(url);

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            server.stop(0);
        }
    }

    private static void serveIndex(HttpExchange exchange) throws IOException {
        byte[] body = LinkHtml.INDEX.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static void createLinkToken(HttpExchange exchange, PlaidApi client, PlaidConfig config) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String flow = queryParam(query, "flow", "bank");
        LinkFlow spec = LinkFlow.ALL.get(flow);
        if (spec == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "unknown flow: " + flow);
            HttpResponses.sendJson(exchange, 400, err);
            return;
        }
        try {
            List<Products> products = spec.products().stream().map(Products::fromValue).toList();
            List<CountryCode> countryCodes = config.countryCodes().stream().map(CountryCode::fromValue).toList();

            LinkTokenCreateRequest request = new LinkTokenCreateRequest()
                .user(new LinkTokenCreateRequestUser().clientUserId("local-user"))
                .clientName("sft")
                .products(products)
                .countryCodes(countryCodes)
                .language("en");
            if (!spec.requiredIfSupported().isEmpty()) {
                List<Products> requiredIfSupported = spec.requiredIfSupported().stream().map(Products::fromValue).toList();
                request = request.requiredIfSupportedProducts(requiredIfSupported);
            }

            Response<LinkTokenCreateResponse> response = client.linkTokenCreate(request).execute();
            if (!response.isSuccessful()) {
                JsonObject err = new JsonObject();
                err.addProperty("error", "link_token_create failed: HTTP " + response.code());
                HttpResponses.sendJson(exchange, 500, err);
                return;
            }
            JsonObject result = new JsonObject();
            result.addProperty("link_token", response.body().getLinkToken());
            HttpResponses.sendJson(exchange, 200, result);
        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.addProperty("error", String.valueOf(e.getMessage()));
            HttpResponses.sendJson(exchange, 500, err);
        }
    }

    private static String queryParam(String query, String key, String defaultValue) {
        if (query == null) {
            return defaultValue;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq >= 0 && pair.substring(0, eq).equals(key)) {
                return pair.substring(eq + 1);
            }
        }
        return defaultValue;
    }

    private static void openBrowser(String url) {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(url));
                } else {
                    System.out.println("open this URL manually: " + url);
                }
            } catch (Exception ignored) {
                System.out.println("open this URL manually: " + url);
            }
        }).start();
    }

    private LinkServer() {}
}
