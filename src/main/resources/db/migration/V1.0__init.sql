CREATE TABLE IF NOT EXISTS event_publication
(
    id                     UUID NOT NULL,
    listener_id            TEXT NOT NULL,
    event_type             TEXT NOT NULL,
    serialized_event       TEXT NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);

alter table event_publication
    owner to "retronap";

-- Standard Spring Security schema for JDBC User Details
-- This keeps authentication/authorization concerns separate from the main application user table.
CREATE TABLE users
(
    username VARCHAR(50)  NOT NULL PRIMARY KEY,
    password VARCHAR(500) NOT NULL,
    enabled  BOOLEAN      NOT NULL
);

CREATE TABLE authorities
(
    username  VARCHAR(50) NOT NULL,
    authority VARCHAR(50) NOT NULL,
    CONSTRAINT fk_authorities_users FOREIGN KEY (username) REFERENCES users (username)
);

CREATE UNIQUE INDEX ix_auth_username ON authorities (username, authority);

-- Napster Users schema

CREATE TABLE napster_users
(
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nickname       VARCHAR(30)  NOT NULL,
    password       VARCHAR(500) NOT NULL,
    level          VARCHAR(10)  NOT NULL,
    link_speed     SMALLINT     NOT NULL,
    client_info    VARCHAR(25)  NOT NULL,
    email          VARCHAR(30)  NOT NULL,
    ip_address     BYTEA,
    downloads      INTEGER,
    uploads        INTEGER,
    data_port      INTEGER,
    created_legacy BIGINT,
    last_seen      BIGINT,

    created        TIMESTAMP(6),
    last_modified  TIMESTAMP(6),
    version        BIGINT       NOT NULL
);

ALTER TABLE napster_users
    ADD CONSTRAINT UK_user_email UNIQUE (email);

CREATE TABLE napster_users_info
(
    napster_user_id UUID NOT NULL,
    zip_code        VARCHAR(20),
    country         VARCHAR(20),
    age             VARCHAR(25),
    gender          VARCHAR(10),
    income          VARCHAR(30),
    education       VARCHAR(40),

    CONSTRAINT FK_napster_users_info_to_user FOREIGN KEY (napster_user_id) REFERENCES napster_users
);

create table shared_file
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    napster_user_id UUID         NOT NULL,
    md5signature    varchar(50), -- 71887565022b45a2c06c457084e0a9d0-9359151
    bitrate         integer      not null,
    dir             varchar(255) not null,
    filename        varchar(255) not null,
    absolute_path   varchar(255) not null,
    frequency       integer      not null,
    seconds         integer      not null,
    size            integer      not null,

    created         TIMESTAMP(6),
    last_modified   TIMESTAMP(6),
    version         BIGINT       NOT NULL,

    CONSTRAINT FK_napster_users_info_to_user FOREIGN KEY (napster_user_id) REFERENCES napster_users
);


CREATE TABLE channel
(
    name          VARCHAR(50)  PRIMARY KEY,
    topic         VARCHAR(255) NOT NULL,
    permanent     BOOLEAN      NOT NULL DEFAULT FALSE,
    channel_limit INTEGER      NOT NULL DEFAULT 0,
    level         VARCHAR(10)  NOT NULL DEFAULT 'USER'
);

ALTER TABLE shared_file
    ADD CONSTRAINT UK_shared_file_user_md5_path UNIQUE (napster_user_id, md5signature, absolute_path);

ALTER TABLE shared_file
    ADD COLUMN filename_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('simple', coalesce(filename, '')))
            STORED;

CREATE INDEX idx_shared_file_filename_fts
    ON shared_file
        USING gin (filename_tsv);

CREATE TABLE hotlist_entry
(
    target_user_id  UUID NOT NULL,
    stalker_user_id UUID NOT NULL,
    PRIMARY KEY (target_user_id, stalker_user_id),
    CONSTRAINT FK_hotlist_entry_target_user FOREIGN KEY (target_user_id) REFERENCES napster_users (id),
    CONSTRAINT FK_hotlist_entry_stalker_user FOREIGN KEY (stalker_user_id) REFERENCES napster_users (id)
);
