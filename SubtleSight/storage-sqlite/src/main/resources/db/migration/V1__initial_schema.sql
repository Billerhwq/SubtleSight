CREATE TABLE sources (
  id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, kind TEXT NOT NULL,
  endpoint TEXT NOT NULL, schedule TEXT, cursor TEXT, tier TEXT NOT NULL,
  health TEXT NOT NULL, topics_json TEXT NOT NULL DEFAULT '[]', enabled INTEGER NOT NULL DEFAULT 1,
  version INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
  UNIQUE(type, endpoint)
);

CREATE TABLE fetch_runs (
  id TEXT PRIMARY KEY, source_id TEXT NOT NULL REFERENCES sources(id), status TEXT NOT NULL,
  fetched_count INTEGER NOT NULL DEFAULT 0, cursor_before TEXT, cursor_after TEXT, error_code TEXT,
  started_at TEXT NOT NULL, finished_at TEXT
);

CREATE TABLE raw_documents (
  id TEXT PRIMARY KEY, source_id TEXT NOT NULL REFERENCES sources(id), fetch_run_id TEXT,
  external_id TEXT, original_url TEXT, canonical_url TEXT NOT NULL UNIQUE, mime_type TEXT,
  charset TEXT, content_hash TEXT NOT NULL UNIQUE, blob_hash TEXT, content_length INTEGER NOT NULL,
  http_status INTEGER NOT NULL, license TEXT, status TEXT NOT NULL, observed_at TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE INDEX idx_raw_source_observed ON raw_documents(source_id, observed_at DESC);

CREATE TABLE document_versions (
  id TEXT PRIMARY KEY, raw_document_id TEXT NOT NULL REFERENCES raw_documents(id), title TEXT NOT NULL,
  author TEXT, published_at TEXT, language TEXT NOT NULL, canonical_url TEXT, text TEXT NOT NULL,
  text_hash TEXT NOT NULL, summary TEXT, algorithm_version TEXT NOT NULL, model_version TEXT,
  prompt_injection INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL,
  UNIQUE(raw_document_id, text_hash, algorithm_version)
);
CREATE INDEX idx_documents_created ON document_versions(created_at DESC);

CREATE TABLE stories (
  id TEXT PRIMARY KEY, title TEXT NOT NULL, summary TEXT, status TEXT NOT NULL,
  first_observed_at TEXT NOT NULL, last_observed_at TEXT NOT NULL, source_count INTEGER NOT NULL,
  source_family_count INTEGER NOT NULL, entities_json TEXT NOT NULL DEFAULT '[]',
  topics_json TEXT NOT NULL DEFAULT '[]', manual_override INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL
);

CREATE TABLE story_members (
  story_id TEXT NOT NULL REFERENCES stories(id), document_version_id TEXT NOT NULL REFERENCES document_versions(id),
  role TEXT, source_family TEXT, similarity REAL NOT NULL, added_at TEXT NOT NULL,
  PRIMARY KEY(story_id, document_version_id)
);

CREATE TABLE saved_views (
  id TEXT PRIMARY KEY, name TEXT NOT NULL, expression TEXT NOT NULL, ast_json TEXT NOT NULL,
  enabled INTEGER NOT NULL, version INTEGER NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL
);

CREATE TABLE signals (
  id TEXT PRIMARY KEY, story_id TEXT NOT NULL REFERENCES stories(id), view_type TEXT NOT NULL,
  saved_view_id TEXT NOT NULL DEFAULT '', features_json TEXT NOT NULL, score REAL NOT NULL, reason_codes_json TEXT NOT NULL,
  computed_at TEXT NOT NULL,
  UNIQUE(story_id, view_type, saved_view_id)
);
CREATE INDEX idx_signals_feed ON signals(view_type, score DESC, computed_at DESC);

CREATE TABLE interactions (
  id TEXT PRIMARY KEY, story_id TEXT, target_id TEXT, type TEXT NOT NULL, reason TEXT,
  undo_of TEXT, created_at TEXT NOT NULL
);
CREATE INDEX idx_interactions_story ON interactions(story_id, created_at DESC);

CREATE TABLE research_runs (
  id TEXT PRIMARY KEY, story_id TEXT, question TEXT NOT NULL, mode TEXT NOT NULL, status TEXT NOT NULL,
  budget_json TEXT NOT NULL, usage_json TEXT NOT NULL, scope_json TEXT, plan_json TEXT,
  checkpoint_json TEXT, gaps_json TEXT NOT NULL DEFAULT '[]', created_at TEXT NOT NULL, updated_at TEXT NOT NULL
);

CREATE TABLE claims (
  id TEXT PRIMARY KEY, research_run_id TEXT NOT NULL REFERENCES research_runs(id), statement TEXT NOT NULL,
  status TEXT NOT NULL, critical INTEGER NOT NULL, valid_from TEXT, valid_to TEXT, created_at TEXT NOT NULL
);

CREATE TABLE evidence (
  id TEXT PRIMARY KEY, document_version_id TEXT NOT NULL REFERENCES document_versions(id),
  exact_quote TEXT NOT NULL, start_offset INTEGER NOT NULL, end_offset INTEGER NOT NULL, locator TEXT,
  snapshot_hash TEXT NOT NULL, source_family TEXT, relation TEXT NOT NULL, quality REAL NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE claim_evidence (
  claim_id TEXT NOT NULL REFERENCES claims(id), evidence_id TEXT NOT NULL REFERENCES evidence(id),
  relation TEXT NOT NULL, PRIMARY KEY(claim_id, evidence_id)
);

CREATE TABLE watch_targets (
  id TEXT PRIMARY KEY, type TEXT NOT NULL, name TEXT NOT NULL, expression TEXT NOT NULL,
  baseline_json TEXT, baseline_version INTEGER NOT NULL, enabled INTEGER NOT NULL,
  cooldown_until TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL
);

CREATE TABLE change_events (
  id TEXT PRIMARY KEY, watch_target_id TEXT NOT NULL REFERENCES watch_targets(id), field TEXT NOT NULL,
  old_value TEXT, new_value TEXT, source TEXT, rule TEXT NOT NULL, confidence REAL NOT NULL,
  severity TEXT NOT NULL, status TEXT NOT NULL, detected_at TEXT NOT NULL,
  UNIQUE(watch_target_id, field, old_value, new_value, rule)
);

CREATE TABLE report_versions (
  id TEXT PRIMARY KEY, research_run_id TEXT, report_type TEXT NOT NULL, version INTEGER NOT NULL,
  title TEXT NOT NULL, content_json TEXT NOT NULL, markdown TEXT NOT NULL, html TEXT NOT NULL,
  snapshot_hash TEXT NOT NULL, citations_verified INTEGER NOT NULL, created_at TEXT NOT NULL,
  UNIQUE(research_run_id, report_type, version)
);

CREATE TABLE publications (
  id TEXT PRIMARY KEY, report_version_id TEXT NOT NULL REFERENCES report_versions(id),
  destination_id TEXT NOT NULL, idempotency_key TEXT NOT NULL UNIQUE, status TEXT NOT NULL,
  remote_id TEXT, receipt_json TEXT, error_code TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL
);

CREATE TABLE jobs (
  id TEXT PRIMARY KEY, type TEXT NOT NULL, status TEXT NOT NULL, priority INTEGER NOT NULL,
  payload_json TEXT NOT NULL, dedup_key TEXT NOT NULL, attempt INTEGER NOT NULL DEFAULT 0,
  max_attempts INTEGER NOT NULL DEFAULT 3, run_after TEXT NOT NULL, lease_until TEXT, heartbeat_at TEXT,
  checkpoint_json TEXT, cancel_requested INTEGER NOT NULL DEFAULT 0, error_code TEXT,
  created_at TEXT NOT NULL, started_at TEXT, finished_at TEXT
);
CREATE UNIQUE INDEX idx_jobs_active_dedup ON jobs(dedup_key)
  WHERE status IN ('QUEUED','RUNNING','RETRY_WAIT','PAUSED');
CREATE INDEX idx_jobs_claim ON jobs(status, run_after, priority DESC, created_at);

CREATE TABLE schedules (
  id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE, cron TEXT NOT NULL, job_type TEXT NOT NULL,
  payload_json TEXT NOT NULL, enabled INTEGER NOT NULL, last_run_at TEXT, next_run_at TEXT NOT NULL,
  missed_policy TEXT NOT NULL DEFAULT 'RUN_ONCE'
);

CREATE TABLE outbox_events (
  id TEXT PRIMARY KEY, aggregate_type TEXT NOT NULL, aggregate_id TEXT NOT NULL,
  event_type TEXT NOT NULL, payload_json TEXT NOT NULL, created_at TEXT NOT NULL,
  dispatched_at TEXT
);
CREATE INDEX idx_outbox_pending ON outbox_events(dispatched_at, created_at);

CREATE TABLE processed_events (
  handler TEXT NOT NULL, event_id TEXT NOT NULL, processed_at TEXT NOT NULL,
  PRIMARY KEY(handler, event_id)
);

CREATE TABLE audit_log (
  id TEXT PRIMARY KEY, actor TEXT NOT NULL, action TEXT NOT NULL, target_type TEXT NOT NULL,
  target_id TEXT, detail_json TEXT NOT NULL, created_at TEXT NOT NULL
);

CREATE TABLE usage_ledger (
  id TEXT PRIMARY KEY, provider TEXT NOT NULL, model TEXT, purpose TEXT NOT NULL,
  input_tokens INTEGER NOT NULL, output_tokens INTEGER NOT NULL, cost TEXT NOT NULL,
  currency TEXT NOT NULL, occurred_at TEXT NOT NULL
);

CREATE TABLE settings (
  key TEXT PRIMARY KEY, value_json TEXT NOT NULL, version INTEGER NOT NULL,
  secret_ref TEXT, updated_at TEXT NOT NULL
);

CREATE TABLE source_health_samples (
  id TEXT PRIMARY KEY, source_id TEXT NOT NULL REFERENCES sources(id), status TEXT NOT NULL,
  latency_ms INTEGER, error_class TEXT, sampled_at TEXT NOT NULL
);
