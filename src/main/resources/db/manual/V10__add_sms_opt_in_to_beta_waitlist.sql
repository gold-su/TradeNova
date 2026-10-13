ALTER TABLE beta_waitlist
    ADD COLUMN phone_number VARCHAR(11) NULL AFTER participation_intent,
    ADD COLUMN sms_opt_in BOOLEAN NOT NULL DEFAULT FALSE AFTER phone_number;
