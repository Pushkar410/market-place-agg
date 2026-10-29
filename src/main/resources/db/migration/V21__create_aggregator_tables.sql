-- Agentic marketplace aggregator: search history, per-listing trust scores, community-shared listings.
-- (Local profile lets Hibernate create these; production uses this migration.)

CREATE TABLE IF NOT EXISTS aggregator_search_history (
    id                  BIGSERIAL PRIMARY KEY,
    query_text          VARCHAR(300) NOT NULL,
    user_email          VARCHAR(254),
    interpreted_by      VARCHAR(40),
    planned_by          VARCHAR(40),
    clarification_asked BOOLEAN      NOT NULL DEFAULT FALSE,
    total_fetched       INTEGER      NOT NULL DEFAULT 0,
    duplicates_removed  INTEGER      NOT NULL DEFAULT 0,
    total_ranked        INTEGER      NOT NULL DEFAULT 0,
    top_trust_score     INTEGER,
    took_ms             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_agg_history_user    ON aggregator_search_history (user_email);
CREATE INDEX IF NOT EXISTS idx_agg_history_created ON aggregator_search_history (created_at);

CREATE TABLE IF NOT EXISTS aggregator_trust_scores (
    id            BIGSERIAL PRIMARY KEY,
    search_id     BIGINT       NOT NULL,
    listing_key   VARCHAR(200) NOT NULL,
    source_id     VARCHAR(100),
    title         VARCHAR(300),
    price         DOUBLE PRECISION,
    result_rank   INTEGER      NOT NULL,
    trust_score   INTEGER      NOT NULL,
    trust_level   VARCHAR(10)  NOT NULL,
    final_score   DOUBLE PRECISION NOT NULL,
    risk_flags    VARCHAR(2000),
    created_at    TIMESTAMP    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_agg_trust_search  ON aggregator_trust_scores (search_id);
CREATE INDEX IF NOT EXISTS idx_agg_trust_listing ON aggregator_trust_scores (listing_key);

CREATE TABLE IF NOT EXISTS aggregator_community_listings (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(120) NOT NULL,
    description      VARCHAR(1000),
    price            DOUBLE PRECISION,
    city             VARCHAR(60),
    category         VARCHAR(30),
    origin_name      VARCHAR(60),
    origin_url       VARCHAR(300),
    contact_name     VARCHAR(60),
    contact_provided BOOLEAN      NOT NULL DEFAULT FALSE,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_agg_community_created ON aggregator_community_listings (created_at);
