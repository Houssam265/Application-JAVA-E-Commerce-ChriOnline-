package com.chrionline.server;

import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.service.ProductService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles one TCP client: reads JSON requests, dispatches catalogue actions
 * (GET_PRODUCTS, GET_PRODUCT, GET_CATEGORIES) via ProductService, returns JSON responses.
 */
public class ClientHandler implements Runnable {

    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());
    private static final Gson GSON = new GsonBuilder().create();
    private static final SessionManager SESSION_MANAGER = new SessionManager();
    private static final ProductService PRODUCT_SERVICE = new ProductService();

    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        String clientId = socket.getRemoteSocketAddress().toString();
        LOG.info("Client connecte: " + clientId);

        try (Socket s = socket;
             BufferedReader in = new BufferedReader(
                 new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                 new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                String msg = line.trim();
                if (msg.isEmpty()) {
                    continue;
                }
                LOG.info("[" + clientId + "] " + msg);

                Response response = processRequest(msg);
                String jsonOut = GSON.toJson(response);
                out.println(jsonOut);
            }
        } catch (IOException e) {
            LOG.log(Level.INFO, "Connexion terminee avec " + clientId + ": " + e.getMessage());
        } finally {
            LOG.info("Client deconnecte: " + clientId);
        }
    }

    /**
     * Parses JSON into Request, validates token for protected actions,
     * dispatches catalogue actions to ProductService, returns Response.
     */
    private Response processRequest(String jsonLine) {
        Request req;
        try {
            req = GSON.fromJson(jsonLine, Request.class);
        } catch (JsonSyntaxException e) {
            LOG.warning("Invalid JSON: " + e.getMessage());
            return Response.error("Invalid JSON");
        }
        if (req == null || req.getAction() == null || req.getAction().isBlank()) {
            return Response.error("Missing action");
        }

        String action = req.getAction().trim();

        // Catalogue actions require valid token (per protocol)
        if (isCatalogueAction(action) && !SESSION_MANAGER.isTokenValid(req.getToken())) {
            return Response.error("Unauthorized: valid token required");
        }

        switch (action) {
            case MessageProtocol.ACTION_GET_PRODUCTS:
                return handleGetProducts(req);
            case MessageProtocol.ACTION_GET_PRODUCT:
                return handleGetProduct(req);
            case MessageProtocol.ACTION_GET_CATEGORIES:
                return handleGetCategories(req);
            default:
                return Response.error("Unknown or unsupported action: " + action);
        }
    }

    private boolean isCatalogueAction(String action) {
        return MessageProtocol.ACTION_GET_PRODUCTS.equals(action)
            || MessageProtocol.ACTION_GET_PRODUCT.equals(action)
            || MessageProtocol.ACTION_GET_CATEGORIES.equals(action);
    }

    /**
     * GET_PRODUCTS — optional payload.category_id for filter.
     * Returns list of products (all or by category).
     */
    private Response handleGetProducts(Request req) {
        Integer categoryId = req.getPayloadInt("category_id");
        List<?> products = PRODUCT_SERVICE.getProducts(categoryId);
        return Response.ok(products);
    }

    /**
     * GET_PRODUCT — payload.product_id required.
     * Returns product details: nom, prix, description, stock, catégorie.
     */
    private Response handleGetProduct(Request req) {
        Integer productId = req.getPayloadInt("product_id");
        if (productId == null) {
            return Response.error("Missing product_id in payload");
        }
        return PRODUCT_SERVICE.getProductDetails(productId)
            .map(Response::ok)
            .orElse(Response.error("Product not found: " + productId));
    }

    /**
     * GET_CATEGORIES — no payload required.
     * Returns list of categories.
     */
    private Response handleGetCategories(Request req) {
        List<?> categories = PRODUCT_SERVICE.getCategories();
        return Response.ok(categories);
    }
}
