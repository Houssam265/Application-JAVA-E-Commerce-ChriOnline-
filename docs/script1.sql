-- Supprimer la colonne role de users
ALTER TABLE users DROP COLUMN role;

-- Créer la table admin
CREATE TABLE admin (
    admin_id        INT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50) UNIQUE NOT NULL,
    public_key      TEXT NOT NULL,          -- clé publique RSA stockée ici
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Mettre à jour la table des sessions pour prendre en charge les administrateurs
ALTER TABLE sessions MODIFY COLUMN user_id INT NULL;
ALTER TABLE sessions ADD COLUMN admin_id INT DEFAULT NULL;
ALTER TABLE sessions ADD COLUMN role VARCHAR(50) DEFAULT 'CLIENT';
