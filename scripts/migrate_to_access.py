"""Copy the current SQLite library into an empty Access database through ODBC.

Run after creating the Access tables in database/schema-access.sql. This keeps
the SQLite source unchanged and refuses to overwrite an Access database that
already contains records.
"""

from __future__ import annotations

import datetime as dt
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / ".tools" / "python"))

import pyodbc  # noqa: E402


def main() -> None:
    source = ROOT / "database" / "videos.db"
    target = ROOT / "database" / "videos-odbc.accdb"
    if not source.is_file() or not target.is_file():
        raise SystemExit("Both database/videos.db and database/videos-odbc.accdb must exist.")

    sqlite = sqlite3.connect(f"file:{source.as_posix()}?mode=ro", uri=True)
    sqlite.row_factory = sqlite3.Row
    access = pyodbc.connect(
        "Driver={Microsoft Access Driver (*.mdb, *.accdb)};"
        f"DBQ={target};",
        autocommit=False,
    )
    tables = {
        "users": ("user_id", "username", "password_hash"),
        "categories": ("category_id", "category_name"),
        "videos": (
            "video_id", "title", "category_id", "duration", "creator_name",
            "upload_date", "views", "description",
        ),
    }
    try:
        for table in tables:
            if access.execute(f"SELECT COUNT(*) FROM [{table}]").fetchone()[0]:
                raise RuntimeError(f"Access table {table} is not empty; migration stopped.")

        for table, columns in tables.items():
            names = ", ".join(f"[{column}]" for column in columns)
            marks = ", ".join("?" for _ in columns)
            query = f"SELECT {names} FROM [{table}] ORDER BY [{columns[0]}]"
            insert = f"INSERT INTO [{table}] ({names}) VALUES ({marks})"
            rows = sqlite.execute(query).fetchall()
            for row in rows:
                values = [row[column] for column in columns]
                if table == "videos":
                    values[5] = dt.datetime.fromisoformat(values[5])
                access.execute(insert, values)
            copied = access.execute(f"SELECT COUNT(*) FROM [{table}]").fetchone()[0]
            if copied != len(rows):
                raise RuntimeError(f"Count mismatch for {table}: {copied} != {len(rows)}")
            print(f"{table}: copied {copied}")
        access.commit()
        print(f"Migration complete: {target}")
    except Exception:
        access.rollback()
        raise
    finally:
        access.close()
        sqlite.close()


if __name__ == "__main__":
    main()
