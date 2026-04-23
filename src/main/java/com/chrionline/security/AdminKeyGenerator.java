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
            System.out.print("Veuillez entrer le nom d'utilisateur de l'administrateur (ex: paul, julie) : ");
            String username = scanner.nextLine().trim();
            if (username.isEmpty()) {
                username = "admin_anonyme";
            }
            
            System.out.println("Génération d'une nouvelle paire de clés RSA pour '" + username + "'...");
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
            
            // Création du répertoire de stockage "config/admin_keys" s'il n'existe pas
            Path keysDir = Paths.get("config", "admin_keys");
            if (!Files.exists(keysDir)) {
                Files.createDirectories(keysDir);
            }

            String pemFileName = username + "_private_key.pem";
            Path targetPemFile = keysDir.resolve(pemFileName);

            if (Files.exists(targetPemFile)) {
                System.out.println("\n[ATTENTION] Une clé existe déjà pour '" + username + "' !");
                System.out.print("Voulez-vous écraser l'ancienne clé et la recréer ? (o/N) : ");
                String confirm = scanner.nextLine().trim().toLowerCase();
                if (!confirm.equals("o") && !confirm.equals("oui")) {
                    System.out.println("Génération annulée.");
                    return;
                }
            }

            // Sauvegarder dans le répertoire dédié
            Files.writeString(targetPemFile, pemFormat.toString());
            
            System.out.println("\n--- SUCCES ! ---");
            System.out.println("Le fichier contenant votre CLE PRIVEE a été sauvegardé ici : ");
            System.out.println(targetPemFile.toAbsolutePath());
            System.out.println("\nIMPORTANT : Ne donnez JAMAIS ce fichier à personne ! Il devra être gardé secrètement par " + username + ".");
            
            System.out.println("\n--------------------------------------------------------------------------------------------------");
            System.out.println("Étape 2: Demandez au responsable de la base de données d'exécuter cette requête SQL :");
            System.out.println("--------------------------------------------------------------------------------------------------");
            String query = "UPDATE users SET role = 'ADMIN', public_key = '" + publicKeyBase64 + "' WHERE username = '" + username + "';";
            System.out.println(query);
            
            Path targetSqlFile = keysDir.resolve(username + "_query.sql");
            Files.writeString(targetSqlFile, query);
            System.out.println("\n(Une copie de cette requête a également été sauvegardée dans : " + targetSqlFile.getFileName() + ")");
            System.out.println("--------------------------------------------------------------------------------------------------\n");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
