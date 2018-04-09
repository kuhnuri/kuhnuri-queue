CREATE TYPE STATUS AS ENUM ('queue', 'process', 'done', 'error');

CREATE TABLE IF NOT EXISTS job
(
  uuid     VARCHAR(256)             NOT NULL,
  created  TIMESTAMP WITH TIME ZONE NOT NULL,
  input    VARCHAR(256)             NOT NULL,
  output   VARCHAR(256)             NOT NULL,
  id       SERIAL                   NOT NULL
    CONSTRAINT job_id_pk
    PRIMARY KEY,
  finished TIMESTAMP WITH TIME ZONE,
  priority INTEGER DEFAULT 0        NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS job_uuid_uindex
  ON job (uuid);

CREATE UNIQUE INDEX IF NOT EXISTS job_id_uindex
  ON job (id);

CREATE TABLE IF NOT EXISTS task
(
  uuid       VARCHAR(256)                     NOT NULL,
  transtype  VARCHAR(256)                     NOT NULL,
  input      VARCHAR(256),
  output     VARCHAR(256),
  status     STATUS DEFAULT 'queue' :: STATUS NOT NULL,
  id         SERIAL                           NOT NULL
    CONSTRAINT task_id_pk
    PRIMARY KEY,
  processing TIMESTAMP WITH TIME ZONE,
  finished   TIMESTAMP WITH TIME ZONE,
  worker     VARCHAR(256),
  job        SERIAL                           NOT NULL
    CONSTRAINT task_job_id_fk
    REFERENCES job
    ON DELETE CASCADE,
  position   INTEGER                          NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS task_uuid_uindex
  ON task (uuid);

CREATE UNIQUE INDEX IF NOT EXISTS task_id_uindex
  ON task (id);

CREATE INDEX IF NOT EXISTS task_job_index
  ON task (job);
