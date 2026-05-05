CREATE TABLE postmortems (
    id UUID PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(768)
);

CREATE INDEX postmortems_embedding_hnsw_idx ON postmortems USING hnsw (embedding vector_cosine_ops);
