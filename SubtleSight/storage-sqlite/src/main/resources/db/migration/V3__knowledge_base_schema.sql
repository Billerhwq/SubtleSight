CREATE TABLE knowledge_folders (
  id TEXT PRIMARY KEY,
  parent_id TEXT REFERENCES knowledge_folders(id),
  name TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX idx_knowledge_folders_parent ON knowledge_folders(parent_id, name);

CREATE TABLE knowledge_files (
  id TEXT PRIMARY KEY,
  folder_id TEXT REFERENCES knowledge_folders(id),
  name TEXT NOT NULL,
  ext TEXT NOT NULL DEFAULT '',
  mime_type TEXT,
  size_bytes INTEGER NOT NULL,
  sha256 TEXT NOT NULL,
  storage_path TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX idx_knowledge_files_folder ON knowledge_files(folder_id, name);
CREATE INDEX idx_knowledge_files_created ON knowledge_files(created_at DESC);
