package com.chrionline.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.*;

public class UdpTestSender {
    private static final Logger LOG = LogManager.getLogger(UdpTestSender.class);

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
            LOG.info("Envoye: {}", msg);
            Thread.sleep(2000); // 2 secondes entre chaque
        }

        socket.close();
    }
}
