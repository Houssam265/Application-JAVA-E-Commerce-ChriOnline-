//package com.chrionline.server;
//
//import com.chrionline.ui.notifications.UdpNotificationClient;
//
//import java.net.*;
//import java.nio.charset.StandardCharsets;
//import java.time.LocalDateTime;
//
///**
// * Quick manual test: sends 4 JSON notifications to the JavaFX client.
// * Run this WHILE the JavaFX application is already open to see the bell badge update.
// */
//public class UdpTestSender {
//    public static void main(String[] args) throws Exception {
//        DatagramSocket socket = new DatagramSocket();
//        InetAddress address = InetAddress.getByName("localhost");
//
//        // Must match UdpNotificationClient.CLIENT_PORT (9091)
//        int port = UdpNotificationClient.CLIENT_PORT;
//
//        // JSON payloads — the client parses these to show clean messages
//        String[][] messages = {
//            {"ORDER_STATUS_UPDATED", "Votre commande est maintenant : Validée.",  "order-test-001"},
//            {"ORDER_STATUS_UPDATED", "Votre commande est maintenant : Expédiée.", "order-test-002"},
//            {"PAYMENT_CONFIRMED",    "Paiement confirmé avec succès.",             "order-test-003"},
//            {"ORDER_STATUS_UPDATED", "Votre commande est maintenant : Livrée.",   "order-test-004"}
//        };
//
//        for (String[] m : messages) {
//            String json = String.format(
//                "{\"type\":\"%s\",\"message\":\"%s\",\"orderId\":\"%s\",\"timestamp\":\"%s\"}",
//                m[0], m[1], m[2], LocalDateTime.now()
//            );
//            byte[] buf = json.getBytes(StandardCharsets.UTF_8);
//            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
//            socket.send(packet);
//            System.out.println("Envoyé sur port " + port + " : " + json);
//            Thread.sleep(2000);
//        }
//
//        socket.close();
//        System.out.println("Test terminé.");
//    }
//}