CREATE TABLE IF NOT EXISTS event_publication
(
    id UUID NOT NULL PRIMARY KEY,
    listener_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    serialized_event VARCHAR(1000000) NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    status VARCHAR(255),
    completion_attempts INTEGER,
    last_resubmission_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);

CREATE TABLE users
(
    username VARCHAR(50) NOT NULL PRIMARY KEY,
    password VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL
);

CREATE TABLE authorities
(
    username VARCHAR(50) NOT NULL,
    authority VARCHAR(50) NOT NULL,
    CONSTRAINT fk_authorities_users FOREIGN KEY (username) REFERENCES users (username)
);
CREATE UNIQUE INDEX ix_auth_username ON authorities (username, authority);

CREATE TABLE napster_users
(
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    nickname VARCHAR(30) NOT NULL,
    password VARCHAR(500) NOT NULL,
    level VARCHAR(10) NOT NULL,
    link_speed SMALLINT NOT NULL,
    client_info VARCHAR(25) NOT NULL,
    email VARCHAR(30) NOT NULL UNIQUE,
    ip_address VARBINARY,
    downloads INTEGER,
    uploads INTEGER,
    data_port INTEGER,
    created_legacy BIGINT,
    last_seen BIGINT,
    created TIMESTAMP(6),
    last_modified TIMESTAMP(6),
    version BIGINT NOT NULL
);

CREATE TABLE napster_users_info
(
    napster_user_id UUID NOT NULL PRIMARY KEY,
    zip_code VARCHAR(20), country VARCHAR(20), age VARCHAR(25), gender VARCHAR(10),
    income VARCHAR(30), education VARCHAR(40),
    CONSTRAINT fk_napster_users_info_to_user FOREIGN KEY (napster_user_id) REFERENCES napster_users
);

CREATE TABLE shared_file
(
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    napster_user_id UUID NOT NULL,
    md5signature VARCHAR(50), bitrate INTEGER NOT NULL, dir VARCHAR(255) NOT NULL,
    filename VARCHAR(255) NOT NULL, absolute_path VARCHAR(255) NOT NULL,
    frequency INTEGER NOT NULL, seconds INTEGER NOT NULL, size INTEGER NOT NULL,
    created TIMESTAMP(6), last_modified TIMESTAMP(6), version BIGINT NOT NULL,
    CONSTRAINT fk_shared_file_user FOREIGN KEY (napster_user_id) REFERENCES napster_users,
    CONSTRAINT uk_shared_file_user_md5_path UNIQUE (napster_user_id, md5signature, absolute_path)
);

CREATE TABLE channel
(
    name VARCHAR(50) PRIMARY KEY, topic VARCHAR(255) NOT NULL,
    permanent BOOLEAN NOT NULL DEFAULT FALSE, channel_limit INTEGER NOT NULL DEFAULT 0,
    level VARCHAR(10) NOT NULL DEFAULT 'USER'
);

CREATE TABLE hotlist_entry
(
    target_user_id UUID NOT NULL, stalker_user_id UUID NOT NULL,
    PRIMARY KEY (target_user_id, stalker_user_id),
    CONSTRAINT fk_hotlist_target FOREIGN KEY (target_user_id) REFERENCES napster_users (id),
    CONSTRAINT fk_hotlist_stalker FOREIGN KEY (stalker_user_id) REFERENCES napster_users (id)
);
