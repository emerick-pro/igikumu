CREATE TABLE IF NOT EXISTS tenants (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    api_key_hash        VARCHAR(255) NOT NULL UNIQUE,
    is_admin            BOOLEAN NOT NULL DEFAULT FALSE,
    config_json         JSON,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;
