CREATE DATABASE IF NOT EXISTS fitness_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fitness_db;
CREATE TABLE IF NOT EXISTS users (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(100) NOT NULL,
 email VARCHAR(190) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS sessions (
 token_hash CHAR(64) PRIMARY KEY,
 user_id BIGINT UNSIGNED NOT NULL,
 expires_at DATETIME NOT NULL,
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 INDEX session_expiry (expires_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS workouts (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT UNSIGNED NOT NULL,
 client_id CHAR(36) NOT NULL,
 type ENUM('Running','Cycling','Weightlifting','Walking') NOT NULL,
 duration INT NOT NULL,
 distance DECIMAL(8,2) NULL,
 weight DECIMAL(8,2) NULL,
 sets INT NULL,
 reps INT NULL,
 date DATE NOT NULL,
 latitude DECIMAL(10,7) NULL,
 longitude DECIMAL(10,7) NULL,
 notes VARCHAR(500) NOT NULL DEFAULT '',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 UNIQUE KEY sync_idempotency (user_id,client_id),
 INDEX history_lookup (user_id,date),
 CONSTRAINT duration_positive CHECK (duration BETWEEN 1 AND 1440),
 CONSTRAINT distance_positive CHECK (distance IS NULL OR distance > 0),
 CONSTRAINT weight_positive CHECK (weight IS NULL OR weight > 0)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS goals (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT UNSIGNED NOT NULL,
 type ENUM('Running','Cycling','Weightlifting','Walking') NOT NULL,
 target_minutes INT NOT NULL,
 start_date DATE NOT NULL,
 end_date DATE NOT NULL,
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 INDEX goal_lookup (user_id,end_date),
 CONSTRAINT target_positive CHECK (target_minutes BETWEEN 1 AND 100000),
 CONSTRAINT date_order CHECK (end_date >= start_date)
) ENGINE=InnoDB;
