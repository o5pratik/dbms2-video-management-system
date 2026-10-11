PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS users (user_id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS categories (category_id INTEGER PRIMARY KEY AUTOINCREMENT, category_name TEXT NOT NULL UNIQUE);
CREATE TABLE IF NOT EXISTS videos (video_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, category_id INTEGER NOT NULL, duration INTEGER NOT NULL CHECK(duration > 0), creator_name TEXT NOT NULL, upload_date TEXT NOT NULL, views INTEGER NOT NULL DEFAULT 0 CHECK(views >= 0), description TEXT NOT NULL DEFAULT '', FOREIGN KEY (category_id) REFERENCES categories(category_id));
CREATE INDEX IF NOT EXISTS idx_videos_category ON videos(category_id);
CREATE INDEX IF NOT EXISTS idx_videos_title ON videos(title);
CREATE INDEX IF NOT EXISTS idx_videos_date ON videos(upload_date);

