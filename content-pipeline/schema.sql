PRAGMA user_version = 1;
PRAGMA journal_mode = DELETE;
PRAGMA page_size = 4096;

CREATE TABLE metadata (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
) WITHOUT ROWID;

CREATE TABLE materials (
  mid TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  content_json TEXT NOT NULL,
  source_path TEXT NOT NULL
) WITHOUT ROWID;

CREATE TABLE questions (
  qid TEXT PRIMARY KEY,
  module TEXT NOT NULL,
  category TEXT NOT NULL,
  year INTEGER,
  region TEXT NOT NULL,
  paper TEXT NOT NULL,
  title TEXT NOT NULL,
  stem_json TEXT NOT NULL,
  answer TEXT NOT NULL CHECK(answer IN ('A','B','C','D')),
  explanation_json TEXT NOT NULL,
  fast_solution_json TEXT NOT NULL,
  reasoning_json TEXT NOT NULL,
  pitfalls_json TEXT NOT NULL,
  material_id TEXT,
  inline_material_json TEXT NOT NULL,
  source_path TEXT NOT NULL,
  FOREIGN KEY(material_id) REFERENCES materials(mid)
) WITHOUT ROWID;

CREATE TABLE options (
  qid TEXT NOT NULL,
  option_key TEXT NOT NULL CHECK(option_key IN ('A','B','C','D')),
  content_json TEXT NOT NULL,
  PRIMARY KEY(qid, option_key),
  FOREIGN KEY(qid) REFERENCES questions(qid)
) WITHOUT ROWID;

CREATE INDEX questions_category_idx ON questions(category, qid);
CREATE INDEX questions_material_idx ON questions(material_id, qid);
