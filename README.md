# ChriOnline — Application e-commerce (Java)

> Projet réalisé dans le cadre du module **SSI-AT** — Département GI, ENSA Tétouan, 2026.

---

## Table des matières

1. [Présentation](#présentation)
2. [Fonctionnalités](#fonctionnalités)
3. [Architecture](#architecture)
4. [Technologies](#technologies)
5. [Structure du dépôt](#structure-du-dépôt)
6. [Installation pas à pas (machine vierge)](#installation-pas-à-pas-machine-vierge)
7. [Protocole applicatif (TCP / JSON)](#protocole-applicatif-tcp--json)
8. [Base de données](#base-de-données)
9. [Ressources utiles](#ressources-utiles)

---

## Présentation

ChriOnline est une application e-commerce **bureau** (desktop) en **Java**, en architecture **client-serveur** :

- **TCP + TLS** (port **8080** par défaut) : dialogue principal au format **JSON** ligne par ligne.
- **UDP** : notifications poussées du serveur vers le client (port d’écoute client **9091**).
- **MySQL** : persistance (utilisateurs, sessions, catalogue, paniers, commandes, paiements).

Une interface **JavaFX** côté client ; un serveur multi-clients (**ExecutorService** + `ClientHandler`).

---

## Fonctionnalités

### Côté client

- Inscription / connexion (dont vérification e-mail et CAPTCHA après échecs)
- Catalogue produits (filtres, recherche), détail produit
- Panier (ajout, mise à jour, suppression)
- Passage de commande puis **paiement carte simulé** (validation Luhn, expiration, CVV ; réponse sensible optionnellement chiffrée en hybride RSA→AES)
- Historique des commandes, profil, mot de passe
- Notifications dans l’UI (**UDP**)
- Connexion **admin dédiée** (challenge RSA) pour les comptes privilégiés

### Côté administrateur

- CRUD produits et catégories (dont images)
- Liste des commandes et mise à jour des statuts
- Liste des utilisateurs, suspension, changement de rôle (dont promotion admin avec clé RSA publique)


Résumé :

- Le client ouvre une session **TLS** vers le serveur, puis envoie des **requêtes JSON** (une ligne = un message).
- Le serveur traite chaque connexion dans un **`ClientHandler`** (pool de threads).
- Après certaines actions (commande, paiement, changement de statut), le serveur peut envoyer un **datagramme UDP JSON** vers le client (**9091**).

---

## Technologies

| Technologie | Usage |
|-------------|--------|
| Java 17 | Langage |
| Maven | Build, dépendances |
| JavaFX | Interface graphique |
| TLS (`SSLSocket` / `SSLServerSocket`) | Canal TCP chiffré |
| UDP (`DatagramSocket`) | Notifications |
| MySQL 8 | Base de données |
| JDBC | Accès données (pas d’ORM) |
| Gson / org.json | Sérialisation protocole |
| BCrypt | Hachage des mots de passe |
| Log4j2 | Logs |

---

## Structure du dépôt

```
Code/
├── pom.xml
├── .env.example              # Modèle variables (copier vers .env)
├── config/
│   ├── tls/
│   │   ├── setup-tls.ps1     # Génère keystore / truststore PKCS12
│   │   └── server-cert.pem   # Certificat exporté (si généré)
│   └── admin_keys/           # Exemples clés admin (local)
├── docs/
│   ├── chrionline_schema.sql # Schéma MySQL
│   ├── seed.sql              # Données de démo (optionnel ; vérifier la syntaxe si utilisé)
│   └── protocole_chrionline.md
└── src/main/java/com/chrionline/
    ├── server/               # Server, ClientHandler, SessionManager, UDP…
    ├── client/               # Couche TCP/TLS partagée (singleton Client)
    ├── protocol/             # Request, Response, MessageProtocol
    ├── security/             # TLS, RSA, AES hybride, validation…
    ├── database/             # DatabaseConnection
    ├── dao/                  # JDBC
    ├── service/              # Métier (auth, panier, commandes…)
    ├── model/                # Entités
    └── ui/                   # JavaFX + contrôleurs + FXML (resources)
```

---

## Installation pas à pas (machine vierge)

Suivez les étapes **dans l’ordre**. Adaptez les chemins si vous n’êtes pas sous Windows.


### Étape  — Cloner le projet

```powershell
git clone <URL_DU_DEPOT> ChriOnline
cd ChriOnline
```

(Remplacez par l’URL réelle du dépôt équipe.)

### Étape  — Créer la base MySQL

```powershell
mysql -u root -p < docs/chrionline_schema.sql
```

Cela crée la base **`chrionline`** et les tables.

*(Optionnel)* données de démo :

```powershell
mysql -u root -p < docs/seed.sql
```

Si `seed.sql` échoue, utilisez uniquement le schéma et créez un utilisateur via l’UI d’inscription.

### Étape 3 — Configurer JDBC (`DatabaseConnection`)

Ouvrez :

`src/main/java/com/chrionline/database/DatabaseConnection.java`

Ajustez au minimum :

- **URL** : `jdbc:mysql://localhost:3306/chrionline?...`
- **USERNAME** / **PASSWORD** : identifiants MySQL locaux

Enregistrez le fichier. Sans étape valide, le serveur ne démarrera pas correctement.

### Étape 4 — Générer les magasins TLS (PKCS12)

Les fichiers **`server-keystore.p12`** et **`client-truststore.p12`** ne sont en général **pas** versionnés. Générez-les une fois :

```powershell
powershell -ExecutionPolicy Bypass -File config/tls/setup-tls.ps1
```

Par défaut : mot de passe **`changeit`**, alias serveur **`chrionline-server`**.

Vérifiez la présence de :

- `config/tls/server-keystore.p12`
- `config/tls/client-truststore.p12`
- `config/tls/server-cert.pem`

### Étape 5 — Variables d’environnement (recommandé)

**Option A — Fichier `.env` à la racine du projet**

```powershell
copy .env.example .env
```

Éditez `.env` : SMTP (si vous voulez les e-mails de vérification), chemins TLS, mots de passe PKCS12.  
Le serveur et le client chargent ce fichier au démarrage (`EnvFileLoader`).

```powershell
$env:CHRIONLINE_TLS_KEYSTORE_PATH="config/tls/server-keystore.p12"
$env:CHRIONLINE_TLS_KEYSTORE_PASSWORD="changeit"
$env:CHRIONLINE_TLS_TRUSTSTORE_PATH="config/tls/client-truststore.p12"
$env:CHRIONLINE_TLS_TRUSTSTORE_PASSWORD="changeit"
```

Liste utile (voir aussi `.env.example`) :

| Variable | Exemple | Rôle |
|----------|---------|------|
| `CHRIONLINE_TLS_KEYSTORE_PATH` | `config/tls/server-keystore.p12` | Serveur |
| `CHRIONLINE_TLS_KEYSTORE_PASSWORD` | `changeit` | Serveur |
| `CHRIONLINE_TLS_TRUSTSTORE_PATH` | `config/tls/client-truststore.p12` | Client |
| `CHRIONLINE_TLS_TRUSTSTORE_PASSWORD` | `changeit` | Client |
| `CHRIONLINE_TLS_PROTOCOL` | `TLS` | Contexte SSL |
| `CHRIONLINE_SMTP_*` | voir `.env.example` | Envoi des codes e-mail |

Sans SMTP configuré, certaines fonctionnalités (codes par mail) restent limitées ; le reste de l’app peut quand même être testé selon les comptes en base.

### Étape 6 — Compiler le projet

```powershell
mvn -q clean compile
```

### Étape 7 — Lancer le serveur

Dans un **premier** terminal, à la racine du projet :

```powershell
mvn exec:java
```

Attendez le message indiquant que le serveur TLS écoute sur le port **8080** (ou le port passé en argument : `mvn exec:java "-Dexec.args=8080"`).

### Étape 8 — Lancer le client JavaFX

Dans un **second** terminal :

```powershell
mvn javafx:run
```

La fenêtre de connexion s’ouvre. Connectez-vous avec un compte existant en base ou inscrivez-vous.

### Étape 9 — Vérifications rapides

- Client et serveur sur la **même machine** : `localhost` et port **8080** par défaut (`Client.java`).
- Pare-feu : autoriser Java / les ports si besoin.
- Tests unitaires (optionnel) : `mvn test`

---

## Protocole applicatif (TCP / JSON)

La référence détaillée des actions (`LOGIN`, `GET_PRODUCTS`, `PAYMENT`, etc.) est dans **`docs/protocole_chrionline.md`**.

Résumé :

- Une ligne JSON par message ; champs courants : `action`, `payload`, `token`.
- Handshake applicatif : première ligne serveur **`HELLO`** avec clé publique RSA pour l’option hybride (voir code).

---

## Base de données

- Schéma : **`docs/chrionline_schema.sql`**
- Encodage recommandé : **utf8mb4**

Tables principales : utilisateurs, sessions, catégories, produits, images produits, paniers, commandes, paiements, notifications (schéma), sécurité login.

---

## Ressources utiles

| Fichier | Contenu |
|---------|---------|
| `docs/protocole_chrionline.md` | Contrat JSON actions/réponses |
| `.env.example` | Modèle configuration |
| `config/tls/setup-tls.ps1` | Bootstrap TLS développement |

