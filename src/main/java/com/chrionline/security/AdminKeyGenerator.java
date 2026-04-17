package com.chrionline.security;

import com.chrionline.security.RSAUtil;
import java.security.KeyPair;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class AdminKeyGenerator {
    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);
            String username = "superadmin";
            
            System.out.println("Génération d'une nouvelle paire de clés RSA...");
            KeyPair keyPair = RSAUtil.generateKeyPair();
            
            String publicKeyBase64 = RSAUtil.encodePublicKey(keyPair.getPublic());
            String privateKeyBase64 = RSAUtil.encodePrivateKey(keyPair.getPrivate());
            
            // Format PEM pour la clé privée
            StringBuilder pemFormat = new StringBuilder();
            pemFormat.append("-----BEGIN PRIVATE KEY-----\n");
            
            // Lignes de 64 caractères max pour la lisibilité
            int index = 0;
            while (index < privateKeyBase64.length()) {
                pemFormat.append(privateKeyBase64.substring(index, Math.min(index + 64, privateKeyBase64.length()))).append("\n");
                index += 64;
            }
            pemFormat.append("-----END PRIVATE KEY-----\n");
            
            // Sauvegarder dans un fichier .pem dans le dossier du projet
            Path targetFile = Paths.get("superadmin_private_key.pem");
            Files.writeString(targetFile, pemFormat.toString());
            
            System.out.println("\n--- SUCCES ! ---");
            System.out.println("Le fichier contenant votre CLE PRIVEE a été sauvegardé ici : ");
            System.out.println(targetFile.toAbsolutePath());
            System.out.println("\nIMPORTANT : Ne donnez JAMAIS ce fichier à personne ! C'est votre pass pour vous connecter.\n");
            
            System.out.println("--------------------------------------------------------------------------------------------------");
            System.out.println("Maintenant, exécutez cette requête SQL dans votre base de données pour enregistrer cet administrateur :");
            System.out.println("--------------------------------------------------------------------------------------------------");
            String query = "INSERT INTO admin (username, public_key, is_active) VALUES ('" + username + "', '" + publicKeyBase64 + "', TRUE);";
            System.out.println(query);
            Files.writeString(Paths.get("superadmin_query.sql"), query);
            System.out.println("--------------------------------------------------------------------------------------------------\n");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
