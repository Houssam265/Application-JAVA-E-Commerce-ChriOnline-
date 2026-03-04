# Document Protocole — ChriOnline

---

## Format général

Toutes les communications se font en **texte brut** via TCP.

Chaque message est **une seule ligne** terminée par `\n`.

```
COMMANDE|param1|param2|...
```

Les réponses du serveur suivent toujours ce format :

```
OK|RESULTAT|données...
ERROR|CODE_ERREUR
```

---

## 1. Authentification

**Inscription**
```
Client  →  REGISTER|username|email|password
Serveur →  OK|REGISTER_SUCCESS
Serveur →  ERROR|USERNAME_TAKEN
Serveur →  ERROR|EMAIL_TAKEN
```

**Connexion**
```
Client  →  LOGIN|username|password
Serveur →  OK|LOGIN_SUCCESS|userId|role
Serveur →  ERROR|WRONG_CREDENTIALS
```

**Déconnexion**
```
Client  →  LOGOUT
Serveur →  OK|LOGOUT_SUCCESS
```

---

## 2. Produits

**Récupérer tous les produits**
```
Client  →  GET_PRODUCTS
Serveur →  OK|PRODUCTS|id,nom,prix,stock,categorieId;id,nom,prix,stock,categorieId;...
```

**Récupérer les détails d'un produit**
```
Client  →  GET_PRODUCT|productId
Serveur →  OK|PRODUCT|id,nom,description,prix,stock,categorieId
Serveur →  ERROR|PRODUCT_NOT_FOUND
```

**Récupérer toutes les catégories**
```
Client  →  GET_CATEGORIES
Serveur →  OK|CATEGORIES|id,nom;id,nom;...
```

**Récupérer les produits d'une catégorie**
```
Client  →  GET_PRODUCTS_BY_CATEGORY|categoryId
Serveur →  OK|PRODUCTS|id,nom,prix,stock,categorieId;...
```

---

## 3. Panier

**Voir le panier**
```
Client  →  GET_CART
Serveur →  OK|CART|cartItemId,productId,nom,quantite,prixUnitaire;...
Serveur →  ERROR|NOT_LOGGED_IN
```

**Ajouter un produit au panier**
```
Client  →  ADD_TO_CART|productId|quantity
Serveur →  OK|CART_UPDATED
Serveur →  ERROR|NOT_LOGGED_IN
Serveur →  ERROR|PRODUCT_NOT_FOUND
Serveur →  ERROR|INSUFFICIENT_STOCK
```

**Modifier la quantité d'un article**
```
Client  →  UPDATE_CART_ITEM|cartItemId|newQuantity
Serveur →  OK|CART_UPDATED
Serveur →  ERROR|ITEM_NOT_FOUND
Serveur →  ERROR|INSUFFICIENT_STOCK
```

**Supprimer un article du panier**
```
Client  →  REMOVE_FROM_CART|cartItemId
Serveur →  OK|CART_UPDATED
Serveur →  ERROR|ITEM_NOT_FOUND
```

**Vider le panier**
```
Client  →  CLEAR_CART
Serveur →  OK|CART_CLEARED
```

**Calculer le total**
```
Client  →  GET_CART_TOTAL
Serveur →  OK|TOTAL|montant
```

---

## 4. Commandes

**Valider la commande (checkout)**
```
Client  →  CHECKOUT|paymentMethod
           paymentMethod = CREDIT_CARD ou SIMULATED
Serveur →  OK|ORDER_CREATED|orderId|totalAmount
Serveur →  ERROR|CART_EMPTY
Serveur →  ERROR|INSUFFICIENT_STOCK
Serveur →  ERROR|PAYMENT_FAILED
Serveur →  ERROR|NOT_LOGGED_IN
```

**Voir l'historique des commandes**
```
Client  →  GET_ORDERS
Serveur →  OK|ORDERS|orderId,statut,total,date;orderId,statut,total,date;...
Serveur →  ERROR|NOT_LOGGED_IN
```

**Voir les détails d'une commande**
```
Client  →  GET_ORDER|orderId
Serveur →  OK|ORDER|orderId,statut,total,date|productId,nom,qte,prix;...
Serveur →  ERROR|ORDER_NOT_FOUND
```

**Annuler une commande**
```
Client  →  CANCEL_ORDER|orderId
Serveur →  OK|ORDER_CANCELLED
Serveur →  ERROR|ORDER_NOT_FOUND
Serveur →  ERROR|CANNOT_CANCEL
```

---

## 5. Admin uniquement

> Toutes ces commandes retournent `ERROR|UNAUTHORIZED` si l'utilisateur n'est pas ADMIN.

**Ajouter un produit**
```
Client  →  ADMIN_ADD_PRODUCT|nom|description|prix|stock|categoryId
Serveur →  OK|PRODUCT_ADDED|productId
```

**Modifier un produit**
```
Client  →  ADMIN_UPDATE_PRODUCT|productId|nom|description|prix|stock|categoryId
Serveur →  OK|PRODUCT_UPDATED
Serveur →  ERROR|PRODUCT_NOT_FOUND
```

**Supprimer un produit**
```
Client  →  ADMIN_DELETE_PRODUCT|productId
Serveur →  OK|PRODUCT_DELETED
Serveur →  ERROR|PRODUCT_NOT_FOUND
```

**Voir toutes les commandes**
```
Client  →  ADMIN_GET_ORDERS
Serveur →  OK|ORDERS|orderId,userId,statut,total,date;...
```

**Mettre à jour le statut d'une commande**
```
Client  →  ADMIN_UPDATE_ORDER_STATUS|orderId|newStatus
           newStatus = PENDING, VALIDATED, SHIPPED, DELIVERED, CANCELLED
Serveur →  OK|ORDER_UPDATED
Serveur →  ERROR|ORDER_NOT_FOUND
Serveur →  ERROR|INVALID_STATUS
```

**Voir tous les utilisateurs**
```
Client  →  ADMIN_GET_USERS
Serveur →  OK|USERS|userId,username,email,role,date;...
```

---

## 6. Notifications UDP

Les notifications sont envoyées **par le serveur uniquement**, de façon autonome après certains événements. Le client écoute sur le **port UDP 9090**.

```
ORDER_CONFIRMED|orderId|totalAmount
ORDER_SHIPPED|orderId
ORDER_DELIVERED|orderId
ORDER_CANCELLED|orderId
```

---

## 7. Codes d'erreur — référence rapide

| Code | Signification |
|------|--------------|
| `NOT_LOGGED_IN` | Aucune session active |
| `UNAUTHORIZED` | Action réservée à l'admin |
| `WRONG_CREDENTIALS` | Login ou mot de passe incorrect |
| `USERNAME_TAKEN` | Nom d'utilisateur déjà utilisé |
| `EMAIL_TAKEN` | Email déjà utilisé |
| `PRODUCT_NOT_FOUND` | Produit introuvable |
| `INSUFFICIENT_STOCK` | Stock insuffisant |
| `CART_EMPTY` | Panier vide lors du checkout |
| `ORDER_NOT_FOUND` | Commande introuvable |
| `CANNOT_CANCEL` | Commande déjà expédiée ou livrée |
| `PAYMENT_FAILED` | Échec du paiement simulé |
| `INVALID_STATUS` | Statut de commande invalide |
| `UNKNOWN_COMMAND` | Commande non reconnue |

---

> Ce document est la **référence centrale** du projet. Chaque membre doit s'y conformer —
> le Membre 1 l'implémente côté serveur, le Membre 4 l'utilise côté UI,
> et les Membres 2 et 3 s'assurent que la logique derrière chaque commande fonctionne correctement.
