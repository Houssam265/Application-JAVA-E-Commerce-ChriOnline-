# Protocole TCP ChriOnline

## Format general
Chaque message TCP est un objet JSON sur une seule ligne (newline-delimited).

Champs obligatoires:
- `action`: string, identifie l'operation.
- `payload`: object ou array, contient les donnees.
- `token`: string ou null, jeton de session.

Exemple:
```json
{"action":"LOGIN","payload":{"email":"user@ex.com","password":"secret"},"token":null}
```

Le `token` est requis pour toutes les actions sauf `LOGIN` et `REGISTER`.
Les actions `VERIFY_EMAIL` et `RESEND_VERIFICATION_EMAIL` sont aussi publiques.

## Actions et formats

### Auth
- `LOGIN`
```json
{"action":"LOGIN","payload":{"email":"user@ex.com","password":"secret"},"token":null}
```
- `REGISTER`
```json
{"action":"REGISTER","payload":{"username":"user","email":"user@ex.com","password":"secret"},"token":null}
```
- `LOGOUT`
```json
{"action":"LOGOUT","payload":{},"token":"<session_token>"}
```
- `VERIFY_EMAIL`
```json
{"action":"VERIFY_EMAIL","payload":{"email":"user@ex.com","code":"123456"},"token":null}
```
- `RESEND_VERIFICATION_EMAIL`
```json
{"action":"RESEND_VERIFICATION_EMAIL","payload":{"email":"user@ex.com"},"token":null}
```

### Catalogue
- `GET_PRODUCTS` (optionnel: filtrage par categorie)
```json
{"action":"GET_PRODUCTS","payload":{"category_id":2},"token":"<session_token>"}
```
- `GET_PRODUCT`
```json
{"action":"GET_PRODUCT","payload":{"product_id":10},"token":"<session_token>"}
```
- `GET_CATEGORIES`
```json
{"action":"GET_CATEGORIES","payload":{},"token":"<session_token>"}
```

### Panier
- `GET_CART`
```json
{"action":"GET_CART","payload":{},"token":"<session_token>"}
```
- `ADD_TO_CART`
```json
{"action":"ADD_TO_CART","payload":{"product_id":10,"quantity":2},"token":"<session_token>"}
```
- `UPDATE_CART_ITEM`
```json
{"action":"UPDATE_CART_ITEM","payload":{"product_id":10,"quantity":3},"token":"<session_token>"}
```
- `REMOVE_FROM_CART`
```json
{"action":"REMOVE_FROM_CART","payload":{"product_id":10},"token":"<session_token>"}
```
- `CLEAR_CART`
```json
{"action":"CLEAR_CART","payload":{},"token":"<session_token>"}
```

### Commandes
- `PLACE_ORDER`
```json
{"action":"PLACE_ORDER","payload":{"payment_method":"SIMULATED"},"token":"<session_token>"}
```
- `GET_ORDERS`
```json
{"action":"GET_ORDERS","payload":{},"token":"<session_token>"}
```
- `GET_ORDER_DETAILS`
```json
{"action":"GET_ORDER_DETAILS","payload":{"order_id":123},"token":"<session_token>"}
```
- `UPDATE_ORDER_STATUS`
```json
{"action":"UPDATE_ORDER_STATUS","payload":{"order_id":123,"status":"SHIPPED"},"token":"<session_token>"}
```

### Paiement
- `PAYMENT`
```json
{"action":"PAYMENT","payload":{"order_id":123,"method":"SIMULATED","amount":199.99},"token":"<session_token>"}
```

### Notifications
- `GET_NOTIFICATIONS`
```json
{"action":"GET_NOTIFICATIONS","payload":{},"token":"<session_token>"}
```
- `MARK_NOTIFICATION_READ`
```json
{"action":"MARK_NOTIFICATION_READ","payload":{"notification_id":5},"token":"<session_token>"}
```

### Admin
- `ADMIN_CREATE_PRODUCT`
```json
{"action":"ADMIN_CREATE_PRODUCT","payload":{"category_id":1,"name":"Laptop","description":"...","price":999.99,"stock":5,"image_url":"..."},"token":"<session_token>"}
```
- `ADMIN_UPDATE_PRODUCT`
```json
{"action":"ADMIN_UPDATE_PRODUCT","payload":{"product_id":10,"name":"Laptop Pro","price":1199.99,"stock":3},"token":"<session_token>"}
```
- `ADMIN_DELETE_PRODUCT`
```json
{"action":"ADMIN_DELETE_PRODUCT","payload":{"product_id":10},"token":"<session_token>"}
```
- `ADMIN_LIST_USERS`
```json
{"action":"ADMIN_LIST_USERS","payload":{},"token":"<session_token>"}
```
- `ADMIN_UPDATE_USER_ROLE`
```json
{"action":"ADMIN_UPDATE_USER_ROLE","payload":{"user_id":7,"role":"ADMIN"},"token":"<session_token>"}
```
