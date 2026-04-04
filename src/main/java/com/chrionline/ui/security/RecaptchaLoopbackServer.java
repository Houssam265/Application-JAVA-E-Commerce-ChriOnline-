package com.chrionline.ui.security;

import com.chrionline.service.RecaptchaConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class RecaptchaLoopbackServer {

    private static HttpServer server;
    private static String widgetUrl;

    private RecaptchaLoopbackServer() {}

    public static synchronized String startIfNeeded() throws IOException {
        if (widgetUrl != null) {
            return widgetUrl;
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", RecaptchaLoopbackServer::handlePage);
        server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "recaptcha-loopback");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        widgetUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        return widgetUrl;
    }

    private static void handlePage(HttpExchange exchange) throws IOException {
        URI requestUri = exchange.getRequestURI();
        if (requestUri == null || !"/".equals(requestUri.getPath())) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        byte[] body = buildHtml().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static String buildHtml() {
        String siteKey = escapeHtml(RecaptchaConfig.getSiteKey());
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <script src="https://www.google.com/recaptcha/api.js" async defer></script>
              <style>
                html, body {
                  margin: 0;
                  padding: 0;
                  overflow: hidden;
                  background: transparent;
                  font-family: Arial, sans-serif;
                }
                body {
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  min-height: 90px;
                }
                .captcha-shell {
                  width: 304px;
                  min-width: 304px;
                  padding: 0;
                }
              </style>
            </head>
            <body>
              <div class="captcha-shell">
                <div class="g-recaptcha"
                     data-sitekey="%s"
                     data-callback="onCaptchaSuccess"
                     data-expired-callback="onCaptchaExpired"
                     data-error-callback="onCaptchaError"></div>
              </div>
              <script>
                function onCaptchaSuccess(token) {
                  if (window.javaRecaptcha && window.javaRecaptcha.onToken) {
                    window.javaRecaptcha.onToken(token);
                  }
                }
                function onCaptchaExpired() {
                  if (window.javaRecaptcha && window.javaRecaptcha.onExpired) {
                    window.javaRecaptcha.onExpired();
                  }
                }
                function onCaptchaError() {
                  if (window.javaRecaptcha && window.javaRecaptcha.onError) {
                    window.javaRecaptcha.onError("reCAPTCHA error");
                  }
                }
              </script>
            </body>
            </html>
            """.formatted(siteKey);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
