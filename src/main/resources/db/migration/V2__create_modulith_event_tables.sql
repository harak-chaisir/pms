CREATE TABLE event_publication
(
    id                    UUID         NOT NULL,
    listener_id           VARCHAR(255) NOT NULL,
    event_type            VARCHAR(255) NOT NULL,
    serialized_event      TEXT         NOT NULL,
    publication_date      TIMESTAMP    NOT NULL,
    completion_date       TIMESTAMP,
    last_resubmission_date TIMESTAMP,
    completion_attempts   INTEGER      NOT NULL DEFAULT 0,
    status                VARCHAR(50),
    PRIMARY KEY (id, listener_id)
);

CREATE INDEX idx_event_publication_completion_date
    ON event_publication (completion_date);