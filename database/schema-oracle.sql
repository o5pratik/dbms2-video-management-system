-- Run as the dedicated application user in SQL*Plus. Compatible with Oracle XE 11g.
-- The setup utility creates these objects and migrates the existing SQLite records.
CREATE TABLE users (
  user_id NUMBER(19) PRIMARY KEY,
  username VARCHAR2(100 CHAR) NOT NULL UNIQUE,
  password_hash VARCHAR2(255 CHAR) NOT NULL
);

CREATE TABLE categories (
  category_id NUMBER(19) PRIMARY KEY,
  category_name VARCHAR2(100 CHAR) NOT NULL UNIQUE
);

CREATE TABLE videos (
  video_id NUMBER(19) PRIMARY KEY,
  title VARCHAR2(150 CHAR) NOT NULL,
  category_id NUMBER(19) NOT NULL REFERENCES categories(category_id),
  duration NUMBER(10) NOT NULL CHECK (duration > 0),
  creator_name VARCHAR2(100 CHAR) NOT NULL,
  upload_date VARCHAR2(10 CHAR) NOT NULL,
  views NUMBER(19) DEFAULT 0 NOT NULL CHECK (views >= 0),
  description VARCHAR2(2000 CHAR)
);

CREATE INDEX idx_videos_category ON videos(category_id);
CREATE INDEX idx_videos_title ON videos(title);
CREATE INDEX idx_videos_date ON videos(upload_date);

CREATE SEQUENCE users_seq START WITH 1;
CREATE SEQUENCE categories_seq START WITH 1;
CREATE SEQUENCE videos_seq START WITH 1;
