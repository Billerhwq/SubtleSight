ALTER TABLE outbox_events ADD COLUMN published_at TEXT;
CREATE INDEX idx_outbox_unpublished ON outbox_events(created_at) WHERE published_at IS NULL;
