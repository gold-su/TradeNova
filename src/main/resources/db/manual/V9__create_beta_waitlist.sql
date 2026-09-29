CREATE TABLE beta_waitlist (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    investment_experience VARCHAR(40) NOT NULL,
    primary_challenge VARCHAR(40) NOT NULL,
    participation_intent VARCHAR(20) NOT NULL,
    utm_source VARCHAR(100) NULL,
    utm_medium VARCHAR(100) NULL,
    utm_campaign VARCHAR(100) NULL,
    utm_content VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_beta_waitlist_email UNIQUE (email)
);
