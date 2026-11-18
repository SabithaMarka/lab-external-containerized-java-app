package com.lab.app;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lab External - Part A
 *
 * <p>A dependency-free Java web application (JDK built-in HTTP server) that serves a
 * login page and a small JSON API. It is packaged as a Docker image and deployed by
 * the Jenkins pipeline defined in {@code Jenkinsfile}.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET  /}            - login page</li>
 *   <li>{@code POST /api/login}   - validates credentials, returns JSON</li>
 *   <li>{@code GET  /healthz}     - container health probe</li>
 *   <li>{@code GET  /api/info}    - application metadata</li>
 * </ul>
 *
 * <p>Compile: {@code javac -d out src/main/java/com/lab/app/LoginApp.java}</p>
 * <p>Run:     {@code java -cp out com.lab.app.LoginApp}</p>
 */
public final class LoginApp {

    private static final Logger LOG = Logger.getLogger(LoginApp.class.getName());

    private static final String APP_NAME = "Lab Login App";
    private static final String VERSION = "1.0.0";
    private static final String SERVICE_ID = "lab-login-app";

    private static final String APP_USER = env("APP_USER", "admin");
    private static final String APP_PASSWORD = env("APP_PASSWORD", "admin123");
    private static final int PORT = Integer.parseInt(env("PORT", "8080"));

    private static final long STARTED_AT = System.currentTimeMillis();

    /** Simple JSON helpers. */
    private static final Pattern JSON_FIELD =
            Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private LoginApp() {
        // utility class
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/", LoginApp::route);
        server.setExecutor(Executors.newFixedThreadPool(16));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutdown signal received - stopping server");
            server.stop(0);
        }, "shutdown-hook"));

        server.start();
        LOG.info(() -> String.format(
                "%s v%s started on http://0.0.0.0:%d (user=%s)",
                APP_NAME, VERSION, PORT, APP_USER));
    }

    // ---------------------------------------------------------------- routing

    private static void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            switch (path) {
                case "/", "/index.html" -> {
                    if (isGet(method)) {
                        serveLoginPage(exchange);
                    } else {
                        sendJson(exchange, 405, error("Method not allowed"));
                    }
                }
                case "/api/login" -> {
                    if ("POST".equalsIgnoreCase(method)) {
                        handleLogin(exchange);
                    } else {
                        sendJson(exchange, 405, error("Use POST for /api/login"));
                    }
                }
                case "/healthz" -> {
                    if (isGet(method)) {
                        sendJson(exchange, 200, healthJson());
                    } else {
                        sendJson(exchange, 405, error("Method not allowed"));
                    }
                }
                case "/api/info" -> {
                    if (isGet(method)) {
                        sendJson(exchange, 200, infoJson());
                    } else {
                        sendJson(exchange, 405, error("Method not allowed"));
                    }
                }
                default -> sendJson(exchange, 404, error("Not found: " + path));
            }
        } catch (Exception ex) {
            LOG.severe("Request failed: " + method + " " + path + " -> " + ex);
            sendJson(exchange, 500, error("Internal server error"));
        } finally {
            exchange.close();
        }
    }

    private static void serveLoginPage(HttpExchange exchange) throws IOException {
        byte[] page;
        try (InputStream in = LoginApp.class.getResourceAsStream("/public/index.html")) {
            if (in == null) {
                throw new IOException("Classpath resource /public/index.html not found");
            }
            page = in.readAllBytes();
        }
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/html; charset=UTF-8");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, page.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(page);
        }
    }

    private static void handleLogin(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = parseCredentials(body, exchange.getRequestHeaders().getFirst("Content-Type"));

        String username = form.getOrDefault("username", "");
        String password = form.getOrDefault("password", "");

        if (username.isBlank() || password.isBlank()) {
            sendJson(exchange, 400, "{\"success\":false,\"message\":\"Username and password are required\"}");
            return;
        }

        if (APP_USER.equals(username) && APP_PASSWORD.equals(password)) {
            String token = UUID.randomUUID().toString();
            LOG.info(() -> "Login SUCCESS for user '" + username + "'");
            sendJson(exchange, 200, String.format(
                    "{\"success\":true,\"message\":\"Login successful\",\"user\":\"%s\",\"token\":\"%s\",\"timestamp\":\"%s\"}",
                    escape(username), token, Instant.now()));
        } else {
            LOG.info(() -> "Login FAILED for user '" + username + "'");
            sendJson(exchange, 401,
                    "{\"success\":false,\"message\":\"Invalid username or password\"}");
        }
    }

    private static Map<String, String> parseCredentials(String body, String contentType) {
        Map<String, String> values = new LinkedHashMap<>();
        String type = contentType == null ? "" : contentType.toLowerCase();

        if (type.contains("application/json")) {
            values.put("username", jsonValue(body, "username"));
            values.put("password", jsonValue(body, "password"));
            return values;
        }

        // Default: application/x-www-form-urlencoded
        for (String pair : body.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            values.put(decode(key), decode(value));
        }
        return values;
    }

    private static String jsonValue(String json, String field) {
        Matcher matcher = Pattern
                .compile(String.format(JSON_FIELD.pattern(), Pattern.quote(field)))
                .matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- payloads

    private static String healthJson() {
        long uptime = (System.currentTimeMillis() - STARTED_AT) / 1000;
        return String.format(
                "{\"status\":\"UP\",\"service\":\"%s\",\"version\":\"%s\",\"uptimeSeconds\":%d,\"timestamp\":\"%s\"}",
                SERVICE_ID, VERSION, uptime, Instant.now());
    }

    private static String infoJson() {
        return String.format(
                "{\"application\":\"%s\",\"version\":\"%s\",\"java\":\"%s\",\"runtime\":\"%s\",\"os\":\"%s\"}",
                APP_NAME, VERSION, System.getProperty("java.version"),
                System.getProperty("java.vendor"), System.getProperty("os.name"));
    }

    private static String error(String message) {
        return "{\"success\":false,\"message\":\"" + escape(message) + "\"}";
    }

    // ---------------------------------------------------------------- responses

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=UTF-8");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static boolean isGet(String method) {
        return "GET".equalsIgnoreCase(method);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
