CREATE TABLE knowledge_units (
  id TEXT PRIMARY KEY,
  resource_type TEXT NOT NULL,
  resource_id TEXT NOT NULL,
  resource_version TEXT NOT NULL,
  folder_id TEXT,
  unit_type TEXT NOT NULL,
  stable_locator TEXT NOT NULL,
  text TEXT NOT NULL,
  context_text TEXT NOT NULL DEFAULT '',
  metadata_json TEXT NOT NULL DEFAULT '{}',
  content_hash TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE(resource_type, resource_id, resource_version, stable_locator)
);
CREATE INDEX idx_knowledge_units_resource ON knowledge_units(resource_type, resource_id, resource_version);
CREATE INDEX idx_knowledge_units_folder ON knowledge_units(folder_id, resource_type);

CREATE TABLE knowledge_index_state (
  resource_type TEXT NOT NULL,
  resource_id TEXT NOT NULL,
  resource_version TEXT NOT NULL,
  status TEXT NOT NULL,
  error_code TEXT,
  unit_count INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL,
  PRIMARY KEY(resource_type, resource_id, resource_version)
);

CREATE TABLE embedding_profiles (
  id TEXT PRIMARY KEY,
  provider TEXT NOT NULL,
  model TEXT NOT NULL,
  model_revision TEXT NOT NULL,
  dimensions INTEGER NOT NULL,
  distance TEXT NOT NULL,
  chunk_schema_version INTEGER NOT NULL,
  active INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE(provider, model, model_revision, dimensions, chunk_schema_version)
);

CREATE TABLE unit_embedding_state (
  unit_id TEXT NOT NULL REFERENCES knowledge_units(id),
  profile_id TEXT NOT NULL REFERENCES embedding_profiles(id),
  content_hash TEXT NOT NULL,
  external_point_id TEXT NOT NULL,
  status TEXT NOT NULL,
  error_code TEXT,
  indexed_at TEXT,
  PRIMARY KEY(unit_id, profile_id)
);

CREATE TABLE qa_conversations (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE qa_answers (
  id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL REFERENCES qa_conversations(id) ON DELETE CASCADE,
  parent_answer_id TEXT REFERENCES qa_answers(id),
  question TEXT NOT NULL,
  status TEXT NOT NULL,
  scope_snapshot_json TEXT NOT NULL,
  direct_answer TEXT NOT NULL DEFAULT '',
  gaps_json TEXT NOT NULL DEFAULT '[]',
  provider TEXT,
  model TEXT,
  error_code TEXT,
  created_at TEXT NOT NULL,
  completed_at TEXT
);
CREATE INDEX idx_qa_answers_conversation ON qa_answers(conversation_id, created_at);

CREATE TABLE qa_answer_units (
  answer_id TEXT NOT NULL REFERENCES qa_answers(id) ON DELETE CASCADE,
  unit_id TEXT NOT NULL REFERENCES knowledge_units(id),
  PRIMARY KEY(answer_id, unit_id)
);

CREATE TABLE qa_claims (
  id TEXT PRIMARY KEY,
  answer_id TEXT NOT NULL REFERENCES qa_answers(id) ON DELETE CASCADE,
  ordinal INTEGER NOT NULL,
  claim_type TEXT NOT NULL,
  statement TEXT NOT NULL,
  verification_status TEXT NOT NULL,
  UNIQUE(answer_id, ordinal)
);

CREATE TABLE qa_citations (
  id TEXT PRIMARY KEY,
  claim_id TEXT NOT NULL REFERENCES qa_claims(id) ON DELETE CASCADE,
  relation TEXT NOT NULL,
  resource_type TEXT NOT NULL,
  resource_id TEXT NOT NULL,
  resource_version TEXT NOT NULL,
  unit_id TEXT NOT NULL REFERENCES knowledge_units(id),
  locator_json TEXT NOT NULL,
  exact_quote TEXT NOT NULL,
  snapshot_hash TEXT NOT NULL,
  source_family TEXT,
  rank INTEGER NOT NULL,
  created_at TEXT NOT NULL
);
CREATE INDEX idx_qa_citations_claim ON qa_citations(claim_id, rank);
