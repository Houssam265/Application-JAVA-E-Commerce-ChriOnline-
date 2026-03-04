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

## Technologies utilisées

| Technologie | Utilisation |
|-------------|-------------|
| Java 17+ | Langage principal |
| JavaFX / Swing | Interface graphique |
| TCP Sockets | Communication principale client-serveur |
| MySQL | Base de données |
| JDBC | Accès à la base de données |
| Git / GitHub | Gestion de versions |

---

## Structure du projet

```
ChriOnline/
│
├── server/
│   ├── Server.java                 # Point d'entrée serveur
│   ├── ClientHandler.java          # Gestion d'un client (1 thread / client)
│   └── UDPNotificationService.java # Envoi des notifications UDP
│
├── client/
│   └── Client.java                 # Connexion TCP + écoute UDP
│
├── model/
│   ├── User.java
│   ├── Product.java
│   ├── Category.java
│   ├── Cart.java
│   ├── CartItem.java
│   ├── Order.java
│   ├── OrderItem.java
│   ├── Payment.java
│   └── Notification.java
│
├── ui/
│   ├── LoginScreen.java
│   ├── RegisterScreen.java
│   ├── ProductScreen.java
│   ├── CartScreen.java
│   ├── CheckoutScreen.java
│   ├── OrderHistoryScreen.java
│   └── admin/
│       ├── AdminDashboard.java
│       ├── ProductManager.java
│       ├── OrderManager.java
│       └── UserManager.java
│
├── database/
│   └── DatabaseConnection.java     # Singleton de connexion MySQL
│
├── docs/
│   ├── protocole_chrionline.md     # Document protocole TCP/UDP
│   ├── chrionline_schema.sql       # Schéma de la base de données
│   └── diagramme_classes.puml      # Diagramme UML
│
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
git clone https://github.com/Houssam265/Application-JAVA-E-Commerce-ChriOnline-.git
cd Application-JAVA-E-Commerce-ChriOnline-
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

---

## Protocole TCP

Toutes les communications client-serveur suivent le format :
```
COMMANDE|param1|param2|...
```

Exemple de conversation complète :
```
Client  →  LOGIN|ali|secret123
Serveur →  OK|LOGIN_SUCCESS|1|CLIENT

Client  →  GET_PRODUCTS
Serveur →  OK|PRODUCTS|1,Laptop,12999.99,10,1;2,Mouse,299.00,50,1

Client  →  ADD_TO_CART|1|2
Serveur →  OK|CART_UPDATED

Client  →  CHECKOUT|SIMULATED
Serveur →  OK|ORDER_CREATED|ORD-550e8400|26298.00

UDP     ←  ORDER_CONFIRMED|ORD-550e8400|26298.00
```

> Consulter [`docs/protocole_chrionline.md`](docs/protocole_chrionline.md) pour la référence complète.

