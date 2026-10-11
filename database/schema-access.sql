-- Microsoft Access schema for a separately obtained, licensed JDBC-ODBC bridge.
-- Run each CREATE TABLE statement individually in Access SQL View.
CREATE TABLE users (user_id AUTOINCREMENT PRIMARY KEY, username TEXT(100) NOT NULL, password_hash TEXT(100) NOT NULL);
CREATE UNIQUE INDEX idx_users_username ON users(username);
CREATE TABLE categories (category_id AUTOINCREMENT PRIMARY KEY, category_name TEXT(80) NOT NULL);
CREATE UNIQUE INDEX idx_categories_name ON categories(category_name);
CREATE TABLE videos (video_id AUTOINCREMENT PRIMARY KEY, title TEXT(150) NOT NULL, category_id LONG NOT NULL, duration LONG NOT NULL, creator_name TEXT(100) NOT NULL, upload_date DATETIME NOT NULL, views LONG NOT NULL, description MEMO, CONSTRAINT fk_videos_category FOREIGN KEY (category_id) REFERENCES categories(category_id));
-- The application inserts demo credentials and sample videos when these tables
-- are empty. To preserve the existing SQLite data, run scripts/migrate_to_access.py
-- before starting the application in ODBC mode.
