package com.chrionline.server;

import java.net.*;

public class UdpTestSender {
    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName("localhost");
        int port = 9090; // même port que le client

        String[] messages = {
                "Votre commande a été validée ✅",
                "Votre colis est en cours de livraison 🚚",
                "Paiement confirmé avec succès 💳",
                "Votre commande a été expédiée 📦"
        };

        for (String msg : messages) {
            byte[] buf = msg.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
            socket.send(packet);
            System.out.println("Envoyé: " + msg);
            Thread.sleep(2000); // 2 secondes entre chaque
        }

        socket.close();
    }
}