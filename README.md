# ChriOnline â€” Application E-Commerce

> Projet rÃ©alisÃ© dans le cadre du module SSI-AT â€” DÃ©partement GI, UAE ENSATÃ© 2026

---

## Table des matiÃ¨res

1. [PrÃ©sentation](#prÃ©sentation)
2. [FonctionnalitÃ©s](#fonctionnalitÃ©s)
3. [Architecture](#architecture)
4. [Technologies utilisÃ©es](#technologies-utilisÃ©es)
5. [Structure du projet](#structure-du-projet)
6. [Installation & Lancement](#installation--lancement)
7. [Protocole TCP](#protocole-tcp)
8. [Base de donnÃ©es](#base-de-donnÃ©es)
9. [Ã‰quipe & RÃ©partition des tÃ¢ches](#Ã©quipe--rÃ©partition-des-tÃ¢ches)

---

## PrÃ©sentation

ChriOnline est une application e-commerce desktop dÃ©veloppÃ©e en Java, basÃ©e sur une architecture **client-serveur native** utilisant **TLS sur TCP** et **UDP** pour les notifications.

Elle permet aux utilisateurs de consulter des produits, gÃ©rer leur panier et effectuer des achats en ligne. Un panel administrateur permet la gestion complÃ¨te des produits, commandes et utilisateurs.

---

## FonctionnalitÃ©s

### CÃ´tÃ© Client
- Inscription et connexion
- Consultation des produits par catÃ©gorie
- Gestion du panier (ajout, suppression, modification)
- Validation de commande avec paiement simulÃ©
- Historique des commandes
- Notifications de confirmation en temps rÃ©el (UDP)

### CÃ´tÃ© Admin
- Ajout, modification et suppression de produits
- Gestion des statuts de commandes
- Gestion des utilisateurs

---

## Architecture

```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”         TCP (port 8080)        â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚                     â”‚ â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â–º â”‚                      â”‚
â”‚    Client Java      â”‚                                 â”‚    Serveur Java      â”‚
â”‚    (UI JavaFX)      â”‚ â—„â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ â”‚    (Multi-threads)   â”‚
â”‚                     â”‚                                 â”‚                      â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜                                 â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
         â”‚                  UDP (port 9090)                       â”‚
         â”‚ â—„â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ â”‚
         â”‚               Notifications                            â”‚
         â”‚                                                        â”‚
         â”‚                                              â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â–¼â”€â”€â”€â”€â”€â”€â”€â”€â”
         â”‚                                              â”‚   Base de donnÃ©es â”‚
         â”‚                                              â”‚   MySQL           â”‚
         â”‚                                              â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```

- Le **client** envoie des commandes texte via TCP et Ã©coute les notifications via UDP
- Le **serveur** gÃ¨re chaque client dans un thread indÃ©pendant (`ClientHandler`)
- Les **notifications** de commande sont envoyÃ©es par UDP aprÃ¨s chaque checkout

---

## Technologies utilisÃ©es

| Technologie | Utilisation |
|-------------|-------------|
| Java 17+ | Langage principal |
| JavaFX / Swing | Interface graphique |
| TLS sur TCP | Communication principale client-serveur chiffree |
| UDP DatagramSocket | Notifications temps rÃ©el |
| MySQL | Base de donnÃ©es |
| JDBC | AccÃ¨s Ã  la base de donnÃ©es |
| Git / GitHub | Gestion de versions |

---

## Structure du projet

```
ChriOnline/
â”‚
â”œâ”€â”€ server/                          
â”‚   â”œâ”€â”€ Server.java                  
â”‚   â”œâ”€â”€ ClientHandler.java           
â”‚   â”œâ”€â”€ SessionManager.java          
â”‚   â””â”€â”€ UDPNotificationService.java  
â”‚
â”œâ”€â”€ client/
â”‚   â””â”€â”€ Client.java                  â† Couche rÃ©seau (partagÃ©e)
â”‚
â”œâ”€â”€ protocol/
â”‚   â”œâ”€â”€ Request.java
â”‚   â””â”€â”€ Response.java
â”‚
â”‚
â”‚   â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—
â”‚   â•‘         MVC CLIENT           â•‘
â”‚   â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
â”‚
â”œâ”€â”€ model/                           â† â‘  MODEL
â”‚   â”œâ”€â”€ User.java                    â”‚  DonnÃ©es pures
â”‚   â”œâ”€â”€ Product.java                 â”‚  (pas de logique rÃ©seau
â”‚   â”œâ”€â”€ Category.java                â”‚   ni d'accÃ¨s DB ici)
â”‚   â”œâ”€â”€ Cart.java                    â”‚
â”‚   â”œâ”€â”€ CartItem.java                â”‚
â”‚   â”œâ”€â”€ Order.java                   â”‚
â”‚   â”œâ”€â”€ OrderItem.java               â”‚
â”‚   â”œâ”€â”€ OrderStatus.java             â”‚
â”‚   â”œâ”€â”€ Payment.java                 â”‚
â”‚   â””â”€â”€ Notification.java            â”‚
â”‚
â”œâ”€â”€ dao/                             â† â‘  MODEL (persistance)
â”‚   â”œâ”€â”€ UserDAO.java                 â”‚  AccÃ¨s base de donnÃ©es
â”‚   â”œâ”€â”€ ProductDAO.java              â”‚  JDBC uniquement
â”‚   â”œâ”€â”€ CartDAO.java                 â”‚
â”‚   â””â”€â”€ OrderDAO.java                â”‚
â”‚
â”œâ”€â”€ service/                         â† â‘  MODEL (logique mÃ©tier)
â”‚   â”œâ”€â”€ AuthService.java             â”‚  RÃ¨gles business
â”‚   â”œâ”€â”€ ProductService.java          â”‚  (stock, UUID, statuts...)
â”‚   â”œâ”€â”€ CartService.java             â”‚
â”‚   â”œâ”€â”€ OrderService.java            â”‚
â”‚   â””â”€â”€ PaymentService.java          â”‚
â”‚
â”œâ”€â”€ database/
â”‚   â””â”€â”€ DatabaseConnection.java      â† â‘  MODEL (infrastructure)
â”‚
â”‚
â”œâ”€â”€ ui/
â”‚   â”œâ”€â”€ MainApp.java                 â† Point d'entrÃ©e JavaFX
â”‚   â”‚
â”‚   â”œâ”€â”€ controller/                  â† â‘¢ CONTROLLER
â”‚   â”‚   â”œâ”€â”€ LoginController.java     â”‚  GÃ¨re Ã©vÃ©nements UI
â”‚   â”‚   â”œâ”€â”€ RegisterController.java  â”‚  Appelle les services
â”‚   â”‚   â”œâ”€â”€ ProductController.java   â”‚  Met Ã  jour la vue
â”‚   â”‚   â”œâ”€â”€ CartController.java      â”‚  NE contient PAS
â”‚   â”‚   â”œâ”€â”€ CheckoutController.java  â”‚  de logique mÃ©tier
â”‚   â”‚   â”œâ”€â”€ ProfileController.java   â”‚
â”‚   â”‚   â”œâ”€â”€ OrderHistoryController   â”‚
â”‚   â”‚   â””â”€â”€ admin/                   â”‚
â”‚   â”‚       â”œâ”€â”€ AdminDashboardController.java
â”‚   â”‚       â”œâ”€â”€ ProductManagerController.java
â”‚   â”‚       â”œâ”€â”€ OrderManagerController.java
â”‚   â”‚       â””â”€â”€ UserManagerController.java
â”‚   â”‚
â”‚   â””â”€â”€ resources/
â”‚       â”œâ”€â”€ fxml/                    â† â‘¡ VIEW
â”‚       â”‚   â”œâ”€â”€ Login.fxml           â”‚  Affichage pur
â”‚       â”‚   â”œâ”€â”€ Register.fxml        â”‚  Aucune logique
â”‚       â”‚   â”œâ”€â”€ Product.fxml         â”‚
â”‚       â”‚   â”œâ”€â”€ Cart.fxml            â”‚
â”‚       â”‚   â”œâ”€â”€ Checkout.fxml        â”‚
â”‚       â”‚   â”œâ”€â”€ Profile.fxml         â”‚
â”‚       â”‚   â”œâ”€â”€ OrderHistory.fxml    â”‚
â”‚       â”‚   â””â”€â”€ admin/               â”‚
â”‚       â”‚       â”œâ”€â”€ AdminDashboard.fxml
â”‚       â”‚       â”œâ”€â”€ ProductManager.fxml
â”‚       â”‚       â”œâ”€â”€ OrderManager.fxml
â”‚       â”‚       â””â”€â”€ UserManager.fxml
â”‚       â”‚
â”‚       â””â”€â”€ css/
â”‚           â””â”€â”€ style.css            â† â‘¡ VIEW (style)
â”‚
â”œâ”€â”€ docs/
â”‚   â”œâ”€â”€ protocole_chrionline.md
â”‚   â”œâ”€â”€ chrionline_schema.sql
â”‚   â””â”€â”€ diagramme_classes.puml
â”‚
â”œâ”€â”€ pom.xml
â””â”€â”€ README.md
```

---

## Installation & Lancement

### PrÃ©requis
- Java 17 ou supÃ©rieur
- MySQL 8.0 ou supÃ©rieur
- IDE recommandÃ© : IntelliJ IDEA

### 1. Cloner le dÃ©pÃ´t
```bash
git clone https://github.com/votre-equipe/chrionline.git
cd chrionline
```

### 2. Configurer la base de donnÃ©es
```bash
mysql -u root -p < docs/chrionline_schema.sql
```

Puis modifier les identifiants dans `DatabaseConnection.java` :
```java
private static final String URL      = "jdbc:mysql://localhost:3306/chrionline";
private static final String USERNAME = "root";
private static final String PASSWORD = "votre_mot_de_passe";
```

### Configuration SMTP pour la verification email
Definir les variables d'environnement suivantes avant de lancer le serveur :
```powershell
$env:CHRIONLINE_SMTP_HOST="smtp.gmail.com"
$env:CHRIONLINE_SMTP_PORT="587"
$env:CHRIONLINE_SMTP_USERNAME="votre.compte@gmail.com"
$env:CHRIONLINE_SMTP_PASSWORD="mot-de-passe-app"
$env:CHRIONLINE_SMTP_FROM="votre.compte@gmail.com"
```

Sans cette configuration, l'inscription avec verification email ne pourra pas envoyer le code.

Alternative: creer un fichier `.env` a la racine du projet en partant de `.env.example`.
Le serveur charge automatiquement ce fichier au demarrage.

### Configuration TLS client/serveur
Le projet embarque deja les fichiers TLS de developpement suivants :

- `config/tls/server-keystore.p12`
- `config/tls/client-truststore.p12`
- `config/tls/server-cert.pem`

Le serveur et le client lisent ces fichiers via les variables `.env` suivantes :

```properties
CHRIONLINE_TLS_PROTOCOL=TLS
CHRIONLINE_TLS_KEYSTORE_PATH=config/tls/server-keystore.p12
CHRIONLINE_TLS_KEYSTORE_PASSWORD=changeit
CHRIONLINE_TLS_KEYSTORE_TYPE=PKCS12
CHRIONLINE_TLS_TRUSTSTORE_PATH=config/tls/client-truststore.p12
CHRIONLINE_TLS_TRUSTSTORE_PASSWORD=changeit
CHRIONLINE_TLS_TRUSTSTORE_TYPE=PKCS12
```

### 3. Lancer le serveur
```bash
mvn exec:java
# Serveur TLS demarre sur le port 8080
```

### 4. Lancer le client
```bash
mvn javafx:run
```


