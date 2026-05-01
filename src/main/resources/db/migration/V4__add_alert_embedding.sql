CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE alerts ADD COLUMN embedding vector(768);

CREATE INDEX alerts_embedding_hnsw_idx ON alerts USING hnsw (embedding vector_cosine_ops);
