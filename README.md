# ChriOnline — Application E-Commerce

> Projet réalisé dans le cadre du module SSI-AT — Département GI, UAE ENSATé 2026

---

## Table des matières

1. [Présentation](#présentation)
2. [Fonctionnalités](#fonctionnalités)
3. [Architecture](#architecture)
4. [Technologies utilisées](#technologies-utilisées)
5. [Structure du projet](#structure-du-projet)
6. [Installation & Lancement](#installation--lancement)
7. [Protocole TCP](#protocole-tcp)
8. [Base de données](#base-de-données)
9. [Équipe & Répartition des tâches](#équipe--répartition-des-tâches)

---

## Présentation

ChriOnline est une application e-commerce desktop développée en Java, basée sur une architecture **client-serveur native** utilisant les **sockets TCP/UDP**.

Elle permet aux utilisateurs de consulter des produits, gérer leur panier et effectuer des achats en ligne. Un panel administrateur permet la gestion complète des produits, commandes et utilisateurs.

---

## Fonctionnalités

### Côté Client
- Inscription et connexion
- Consultation des produits par catégorie
- Gestion du panier (ajout, suppression, modification)
- Validation de commande avec paiement simulé
- Historique des commandes
- Notifications de confirmation en temps réel (UDP)

### Côté Admin
- Ajout, modification et suppression de produits
- Gestion des statuts de commandes
- Gestion des utilisateurs

---

## Architecture

```
┌─────────────────────┐         TCP (port 8080)        ┌──────────────────────┐
│                     │ ─────────────────────────────► │                      │
│    Client Java      │                                 │    Serveur Java      │
│    (UI JavaFX)      │ ◄───────────────────────────── │    (Multi-threads)   │
│                     │                                 │                      │
└─────────────────────┘                                 └──────────────────────┘
         │                  UDP (port 9090)                       │
         │ ◄──────────────────────────────────────────────────── │
         │               Notifications                            │
         │                                                        │
         │                                              ┌─────────▼────────┐
         │                                              │   Base de données │
         │                                              │   MySQL           │
         │                                              └──────────────────┘
```

- Le **client** envoie des commandes texte via TCP et écoute les notifications via UDP
- Le **serveur** gère chaque client dans un thread indépendant (`ClientHandler`)
- Les **notifications** de commande sont envoyées par UDP après chaque checkout

---

## Technologies utilisées

| Technologie | Utilisation |
|-------------|-------------|
| Java 17+ | Langage principal |
| JavaFX / Swing | Interface graphique |
| TCP Sockets | Communication principale client-serveur |
| UDP DatagramSocket | Notifications temps réel |
| MySQL | Base de données |
| JDBC | Accès à la base de données |
| Git / GitHub | Gestion de versions |

---

## Structure du projet

```
ChriOnline/
│
├── server/                          
│   ├── Server.java                  
│   ├── ClientHandler.java           
│   ├── SessionManager.java          
│   └── UDPNotificationService.java  
│
├── client/
│   └── Client.java                  ← Couche réseau (partagée)
│
├── protocol/
│   ├── Request.java
│   └── Response.java
│
│
│   ╔══════════════════════════════╗
│   ║         MVC CLIENT           ║
│   ╚══════════════════════════════╝
│
├── model/                           ← ① MODEL
│   ├── User.java                    │  Données pures
│   ├── Product.java                 │  (pas de logique réseau
│   ├── Category.java                │   ni d'accès DB ici)
│   ├── Cart.java                    │
│   ├── CartItem.java                │
│   ├── Order.java                   │
│   ├── OrderItem.java               │
│   ├── OrderStatus.java             │
│   ├── Payment.java                 │
│   └── Notification.java            │
│
├── dao/                             ← ① MODEL (persistance)
│   ├── UserDAO.java                 │  Accès base de données
│   ├── ProductDAO.java              │  JDBC uniquement
│   ├── CartDAO.java                 │
│   └── OrderDAO.java                │
│
├── service/                         ← ① MODEL (logique métier)
│   ├── AuthService.java             │  Règles business
│   ├── ProductService.java          │  (stock, UUID, statuts...)
│   ├── CartService.java             │
│   ├── OrderService.java            │
│   └── PaymentService.java          │
│
├── database/
│   └── DatabaseConnection.java      ← ① MODEL (infrastructure)
│
│
├── ui/
│   ├── MainApp.java                 ← Point d'entrée JavaFX
│   │
│   ├── controller/                  ← ③ CONTROLLER
│   │   ├── LoginController.java     │  Gère événements UI
│   │   ├── RegisterController.java  │  Appelle les services
│   │   ├── ProductController.java   │  Met à jour la vue
│   │   ├── CartController.java      │  NE contient PAS
│   │   ├── CheckoutController.java  │  de logique métier
│   │   ├── ProfileController.java   │
│   │   ├── OrderHistoryController   │
│   │   └── admin/                   │
│   │       ├── AdminDashboardController.java
│   │       ├── ProductManagerController.java
│   │       ├── OrderManagerController.java
│   │       └── UserManagerController.java
│   │
│   └── resources/
│       ├── fxml/                    ← ② VIEW
│       │   ├── Login.fxml           │  Affichage pur
│       │   ├── Register.fxml        │  Aucune logique
│       │   ├── Product.fxml         │
│       │   ├── Cart.fxml            │
│       │   ├── Checkout.fxml        │
│       │   ├── Profile.fxml         │
│       │   ├── OrderHistory.fxml    │
│       │   └── admin/               │
│       │       ├── AdminDashboard.fxml
│       │       ├── ProductManager.fxml
│       │       ├── OrderManager.fxml
│       │       └── UserManager.fxml
│       │
│       └── css/
│           └── style.css            ← ② VIEW (style)
│
├── docs/
│   ├── protocole_chrionline.md
│   ├── chrionline_schema.sql
│   └── diagramme_classes.puml
│
├── pom.xml
└── README.md
```

---

## Installation & Lancement

### Prérequis
- Java 17 ou supérieur
- MySQL 8.0 ou supérieur
- IDE recommandé : IntelliJ IDEA

### 1. Cloner le dépôt
```bash
git clone https://github.com/votre-equipe/chrionline.git
cd chrionline
```

### 2. Configurer la base de données
```bash
mysql -u root -p < docs/chrionline_schema.sql
```

Puis modifier les identifiants dans `DatabaseConnection.java` :
```java
private static final String URL      = "jdbc:mysql://localhost:3306/chrionline";
private static final String USERNAME = "root";
private static final String PASSWORD = "votre_mot_de_passe";
```

### 3. Lancer le serveur
```bash
javac server/Server.java
java server.Server
# Serveur démarré sur le port 8080
```

### 4. Lancer le client
```bash
javac client/Client.java
java client.Client
```