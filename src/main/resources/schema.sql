CREATE TABLE IF NOT EXISTS files (
    seq INTEGER PRIMARY KEY AUTOINCREMENT,
    id TEXT NOT NULL UNIQUE,
    kind TEXT NOT NULL CHECK (kind IN ('text', 'file')),
    text TEXT,
    name TEXT,
    mime TEXT,
    size INTEGER,
    device TEXT,
    created_at INTEGER NOT NULL
);