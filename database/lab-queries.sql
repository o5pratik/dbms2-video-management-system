-- Video Management System: SQL operations demonstrated by the application.
-- Bind parameters (?) with PreparedStatement; never concatenate user input.

-- SELECT + INNER JOIN: full video records with category name.
SELECT v.video_id, v.title, c.category_name, v.duration, v.creator_name, v.upload_date, v.views
FROM videos v INNER JOIN categories c ON c.category_id = v.category_id
ORDER BY v.upload_date DESC, v.video_id DESC;

-- INSERT: add a video. category_id must already exist.
INSERT INTO videos(title, category_id, duration, creator_name, upload_date, views, description)
VALUES (?, ?, ?, ?, ?, ?, ?);

-- UPDATE: edit a selected record.
UPDATE videos SET title=?, category_id=?, duration=?, creator_name=?, upload_date=?, views=?, description=?
WHERE video_id=?;

-- DELETE: remove a selected record.
DELETE FROM videos WHERE video_id=?;

-- WHERE: search by exact ID or a partial title/category/creator.
SELECT * FROM videos WHERE video_id=?;
SELECT v.video_id, v.title, c.category_name FROM videos v
INNER JOIN categories c ON c.category_id=v.category_id
WHERE v.title LIKE ?;

-- ORDER BY: most viewed videos.
SELECT video_id, title, views FROM videos ORDER BY views DESC;

-- GROUP BY + COUNT + LEFT JOIN: include categories with zero videos.
SELECT c.category_name, COUNT(v.video_id) AS video_count
FROM categories c LEFT JOIN videos v ON v.category_id=c.category_id
GROUP BY c.category_id, c.category_name ORDER BY video_count DESC;

-- Date range report.
SELECT v.video_id, v.title, c.category_name, v.upload_date FROM videos v
INNER JOIN categories c ON c.category_id=v.category_id
WHERE v.upload_date BETWEEN ? AND ? ORDER BY v.upload_date DESC;

