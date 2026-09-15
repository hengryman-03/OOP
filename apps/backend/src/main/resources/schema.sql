CREATE TABLE IF NOT EXISTS aggregates (
    kind VARCHAR(80) NOT NULL,
    id VARCHAR(80) NOT NULL,
    payload CLOB NOT NULL,
    PRIMARY KEY (kind, id)
);
CREATE TABLE IF NOT EXISTS mutation_lock (id INT PRIMARY KEY);
MERGE INTO mutation_lock KEY(id) VALUES (1);
