# Protocole TCP ChriOnline

Le canal client/serveur est protege par TLS. Les messages JSON ci-dessous circulent
dans une session TLS et non plus sur un socket TCP en clair.

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
Les actions `VERIFY_EMAIL`, `RESEND_VERIFICATION_EMAIL`, `VERIFY_LOGIN_IP` et
`RESEND_LOGIN_IP_VERIFICATION` sont aussi publiques.

## Actions et formats

### Auth
- `LOGIN`
```json
{"action":"LOGIN","payload":{"email":"user@ex.com","password":"secret","captchaId":"<captcha_id>","captchaAnswer":"AB12CD"},"token":null}
```
Reponse possible si l'authentification reussit directement:
```json
{"success":true,"message":"LOGIN_SUCCESS","payload":{"userId":1,"username":"user","email":"user@ex.com","role":"CLIENT","emailVerified":true},"token":"<session_token>"}
```
Reponse possible si l'email est correct mais qu'une nouvelle IP doit etre verifiee:
```json
{"success":false,"message":"Nouvelle adresse IP detectee. Un code de verification a ete envoye par email.","payload":{"email":"user@ex.com","verificationType":"login_ip","message":"Verification requise pour cette nouvelle adresse IP."},"token":null}
```
- `GET_LOGIN_CAPTCHA`
```json
{"action":"GET_LOGIN_CAPTCHA","payload":{},"token":null}
```
Reponse:
```json
{"success":true,"message":"LOGIN_CAPTCHA_READY","payload":{"captchaId":"<captcha_id>","captchaText":"AB12CD","expiresInSeconds":120},"token":null}
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
- `VERIFY_LOGIN_IP`
```json
{"action":"VERIFY_LOGIN_IP","payload":{"email":"user@ex.com","code":"123456"},"token":null}
```
- `RESEND_LOGIN_IP_VERIFICATION`
```json
{"action":"RESEND_LOGIN_IP_VERIFICATION","payload":{"email":"user@ex.com"},"token":null}
```

### Verification de nouvelle IP
- Lors d'un `LOGIN`, le serveur compare l'IP source courante avec `trusted_login_ip`.
- Si l'IP est differente, il n'ouvre pas encore de session.
- Il genere un code temporaire, l'envoie par email, puis attend `VERIFY_LOGIN_IP`.
- Une fois le code valide, le serveur cree la session et remplace `trusted_login_ip`
  par la nouvelle adresse IP.

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

### Nonce serveur
- `GET_OPERATION_NONCE`
```json
{"action":"GET_OPERATION_NONCE","payload":{"operation":"PLACE_ORDER"},"token":"<session_token>"}
```
Reponse:
```json
{"success":true,"message":"OPERATION_NONCE_READY","payload":{"nonce":"<nonce>","operation":"PLACE_ORDER","scope":null,"expiresAt":1712678460000},"token":null}
```
Le `nonce` est:
- emis par le serveur
- associe a l'utilisateur connecte
- valable une seule fois
- valable pour une seule operation sensible

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
{"action":"PLACE_ORDER","payload":{"payment_method":"SIMULATED"},"token":"<session_token>","operationNonce":"<nonce>","requestId":"<uuid>","timestamp":1712678400000}
```
Pour limiter les attaques par rejeu, chaque requete `PLACE_ORDER` doit contenir :
- `requestId` : identifiant unique de requete
- `timestamp` : horodatage client en millisecondes epoch
- `operationNonce` : nonce serveur a usage unique, recupere via `GET_OPERATION_NONCE`

Le serveur rejette la commande si :
- le `requestId` est absent
- le `timestamp` est trop ancien ou incoherent
- le meme `requestId` est recu une seconde fois pour le meme utilisateur
- le `operationNonce` est absent, invalide, expire ou deja consomme
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
{"action":"PAYMENT","payload":{"order_id":123,"method":"SIMULATED","amount":199.99},"token":"<session_token>","operationNonce":"<nonce>","requestId":"<uuid>","timestamp":1712678400000}
```
Pour limiter les attaques par rejeu, chaque requete `PAYMENT` doit contenir :
- `requestId` : identifiant unique de requete
- `timestamp` : horodatage client en millisecondes epoch
- `operationNonce` : nonce serveur a usage unique, recupere via `GET_OPERATION_NONCE`

Le serveur rejette un paiement si :
- le `requestId` est absent
- le `timestamp` est trop ancien ou incoherent
- le meme `requestId` est recu une seconde fois pour le meme utilisateur
- le `operationNonce` est absent, invalide, expire ou deja consomme

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
