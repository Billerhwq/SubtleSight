CREATE TABLE calendar_sources (
  id TEXT PRIMARY KEY,
  source_key TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  type TEXT NOT NULL,
  tier TEXT NOT NULL,
  endpoint TEXT NOT NULL,
  schedule TEXT NOT NULL,
  enabled INTEGER NOT NULL,
  min_interval_seconds INTEGER NOT NULL,
  daily_budget INTEGER NOT NULL,
  next_allowed_at TEXT,
  last_attempt_at TEXT,
  last_success_at TEXT,
  health TEXT NOT NULL,
  parser_version TEXT NOT NULL,
  warning TEXT NOT NULL DEFAULT '',
  last_http_status INTEGER NOT NULL DEFAULT 0,
  last_parsed_count INTEGER NOT NULL DEFAULT 0,
  last_inserted_count INTEGER NOT NULL DEFAULT 0,
  last_updated_count INTEGER NOT NULL DEFAULT 0,
  countries_json TEXT NOT NULL DEFAULT '[]',
  categories_json TEXT NOT NULL DEFAULT '[]',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX idx_calendar_sources_enabled ON calendar_sources(enabled, next_allowed_at);

CREATE TABLE calendar_raw_snapshots (
  id TEXT PRIMARY KEY,
  source_id TEXT NOT NULL REFERENCES calendar_sources(id),
  source_key TEXT NOT NULL,
  url TEXT NOT NULL,
  final_url TEXT NOT NULL,
  http_status INTEGER NOT NULL,
  content_type TEXT NOT NULL,
  etag TEXT,
  last_modified TEXT,
  fetched_at TEXT NOT NULL,
  content_hash TEXT NOT NULL,
  content_length INTEGER NOT NULL,
  parser_version TEXT NOT NULL,
  body TEXT NOT NULL,
  UNIQUE(source_id, content_hash)
);
CREATE INDEX idx_calendar_snapshots_source_time ON calendar_raw_snapshots(source_id, fetched_at DESC);

CREATE TABLE calendar_events (
  id TEXT PRIMARY KEY,
  event_key TEXT NOT NULL UNIQUE,
  indicator_code TEXT NOT NULL,
  name_zh TEXT NOT NULL,
  name_original TEXT NOT NULL,
  country_code TEXT NOT NULL,
  region TEXT NOT NULL,
  currency TEXT NOT NULL,
  category TEXT NOT NULL,
  importance TEXT NOT NULL,
  importance_source TEXT NOT NULL,
  scheduled_at_utc TEXT,
  source_timezone TEXT NOT NULL,
  scheduled_local_text TEXT NOT NULL,
  period TEXT NOT NULL,
  actual TEXT,
  forecast TEXT,
  previous TEXT,
  revised_previous TEXT,
  unit TEXT NOT NULL,
  status TEXT NOT NULL,
  official_url TEXT NOT NULL,
  primary_source_id TEXT REFERENCES calendar_sources(id),
  first_seen_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  released_at TEXT,
  version INTEGER NOT NULL
);
CREATE INDEX idx_calendar_events_time ON calendar_events(scheduled_at_utc, country_code, importance);
CREATE INDEX idx_calendar_events_status ON calendar_events(status, last_seen_at DESC);

CREATE TABLE calendar_event_evidence (
  id TEXT PRIMARY KEY,
  event_id TEXT NOT NULL REFERENCES calendar_events(id),
  source_id TEXT NOT NULL REFERENCES calendar_sources(id),
  raw_snapshot_id TEXT NOT NULL REFERENCES calendar_raw_snapshots(id),
  source_event_id TEXT NOT NULL,
  source_url TEXT NOT NULL,
  fetched_at TEXT NOT NULL,
  parser_version TEXT NOT NULL,
  raw_fields_json TEXT NOT NULL,
  warnings_json TEXT NOT NULL,
  source_tier TEXT NOT NULL,
  UNIQUE(event_id, source_id, raw_snapshot_id, source_event_id)
);
CREATE INDEX idx_calendar_evidence_event ON calendar_event_evidence(event_id, fetched_at DESC);

CREATE TABLE calendar_event_revisions (
  id TEXT PRIMARY KEY,
  event_id TEXT NOT NULL REFERENCES calendar_events(id),
  changed_at TEXT NOT NULL,
  source_id TEXT REFERENCES calendar_sources(id),
  changed_fields_json TEXT NOT NULL,
  reason TEXT NOT NULL
);
CREATE INDEX idx_calendar_revisions_event ON calendar_event_revisions(event_id, changed_at DESC);
