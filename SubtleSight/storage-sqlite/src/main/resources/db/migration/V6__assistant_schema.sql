CREATE TABLE assistant_sessions (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL DEFAULT '新对话',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  archived INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE assistant_turns (
  id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES assistant_sessions(id) ON DELETE CASCADE,
  role TEXT NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
  content TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','PLANNING','EXECUTING','AWAITING_CONFIRM',
                      'STREAMING','COMPLETED','FAILED','CANCELLED')),
  plan_json TEXT,
  tools_json TEXT,
  context_json TEXT,
  created_at TEXT NOT NULL,
  completed_at TEXT
);
CREATE INDEX idx_turns_session ON assistant_turns(session_id, created_at);

CREATE TABLE assistant_audit (
  id TEXT PRIMARY KEY,
  turn_id TEXT NOT NULL REFERENCES assistant_turns(id) ON DELETE CASCADE,
  action TEXT NOT NULL,
  detail_json TEXT,
  created_at TEXT NOT NULL
);
CREATE INDEX idx_audit_turn ON assistant_audit(turn_id, created_at);
