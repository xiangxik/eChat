ALTER TABLE memory_items
    ADD COLUMN IF NOT EXISTS embedding_dimension INTEGER;

UPDATE memory_items
SET embedding_dimension = ${embeddingDimension}
WHERE embedding IS NOT NULL AND embedding_dimension IS NULL;