CREATE TABLE knowledge_documents (
  id TEXT PRIMARY KEY,
  folder_id TEXT REFERENCES knowledge_folders(id) ON DELETE SET NULL,
  title TEXT NOT NULL,
  content_html TEXT NOT NULL DEFAULT '',
  drawing_json TEXT NOT NULL DEFAULT '{"nodes":[],"edges":[]}',
  version INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE INDEX idx_knowledge_documents_folder
  ON knowledge_documents(folder_id, updated_at DESC);

CREATE TABLE knowledge_document_versions (
  document_id TEXT NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
  version INTEGER NOT NULL,
  title TEXT NOT NULL,
  content_html TEXT NOT NULL,
  drawing_json TEXT NOT NULL,
  change_summary TEXT NOT NULL DEFAULT '',
  created_at TEXT NOT NULL,
  PRIMARY KEY (document_id, version)
);

CREATE INDEX idx_knowledge_document_versions_created
  ON knowledge_document_versions(document_id, created_at DESC);
