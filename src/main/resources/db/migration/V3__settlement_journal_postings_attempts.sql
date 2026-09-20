-- Phase 3 schema: settlement journal, postings, settlement attempts,
-- and the trade-to-journal relationship.
-- V1 and V2 are not edited. Existing READY trades and capture command
-- results stay as stored; journal_id is NULL until a later settlement.
--
-- DEBIT decreases the account balance. CREDIT increases it.
-- These are project-local balance-movement directions, not GAAP
-- general-ledger debit/credit semantics.
-- amount is always positive. signed_amount carries the sign.
-- MISSING_ACCOUNT is not a settlement outcome.

CREATE TABLE settlement_journal (
    id         UUID PRIMARY KEY,
    trade_id   UUID NOT NULL,
    settled_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT settlement_journal_trade_fk
        FOREIGN KEY (trade_id) REFERENCES trade (id),
    CONSTRAINT settlement_journal_trade_id_unique UNIQUE (trade_id),
    CONSTRAINT settlement_journal_id_trade_id_unique UNIQUE (id, trade_id)
);

CREATE TABLE posting (
    id           UUID PRIMARY KEY,
    journal_id   UUID NOT NULL,
    account_id   UUID NOT NULL,
    direction    TEXT NOT NULL,
    amount       BIGINT NOT NULL,
    signed_amount BIGINT GENERATED ALWAYS AS (
        CASE WHEN direction = 'DEBIT' THEN -amount ELSE amount END
    ) STORED,
    CONSTRAINT posting_journal_fk
        FOREIGN KEY (journal_id) REFERENCES settlement_journal (id),
    CONSTRAINT posting_account_fk
        FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT posting_direction_supported CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT posting_amount_positive CHECK (amount > 0),
    CONSTRAINT posting_journal_account_unique UNIQUE (journal_id, account_id)
);

CREATE TABLE settlement_attempt (
    id            UUID PRIMARY KEY,
    trade_id      UUID NOT NULL,
    command_key   TEXT NOT NULL,
    outcome       TEXT NOT NULL,
    journal_id    UUID,
    business_date DATE NOT NULL,
    decided_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT settlement_attempt_trade_fk
        FOREIGN KEY (trade_id) REFERENCES trade (id),
    CONSTRAINT settlement_attempt_command_fk
        FOREIGN KEY (command_key) REFERENCES command_result (command_key),
    CONSTRAINT settlement_attempt_command_key_unique UNIQUE (command_key),
    CONSTRAINT settlement_attempt_journal_fk
        FOREIGN KEY (journal_id) REFERENCES settlement_journal (id),
    CONSTRAINT settlement_attempt_outcome_supported CHECK (
        outcome IN (
            'SETTLED',
            'ALREADY_SETTLED',
            'NOT_DUE',
            'INSUFFICIENT_CASH',
            'INSUFFICIENT_SECURITIES'
        )
    ),
    CONSTRAINT settlement_attempt_journal_consistency CHECK (
        (outcome IN ('SETTLED', 'ALREADY_SETTLED')) = (journal_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX settlement_attempt_one_settled_per_trade
    ON settlement_attempt (trade_id)
    WHERE outcome = 'SETTLED';

ALTER TABLE trade DROP CONSTRAINT trade_status_supported;
ALTER TABLE trade ADD CONSTRAINT trade_status_supported
    CHECK (status IN ('READY', 'SETTLED'));

ALTER TABLE trade ADD COLUMN journal_id UUID;

ALTER TABLE trade ADD CONSTRAINT trade_journal_fk
    FOREIGN KEY (journal_id) REFERENCES settlement_journal (id);

ALTER TABLE trade ADD CONSTRAINT trade_journal_id_unique UNIQUE (journal_id);

ALTER TABLE trade ADD CONSTRAINT trade_settled_journal_consistency
    CHECK ((status = 'SETTLED') = (journal_id IS NOT NULL));

ALTER TABLE trade ADD CONSTRAINT trade_journal_same_trade_fk
    FOREIGN KEY (journal_id, id) REFERENCES settlement_journal (id, trade_id);

ALTER TABLE command_result DROP CONSTRAINT command_result_operation_supported;
ALTER TABLE command_result ADD CONSTRAINT command_result_operation_supported
    CHECK (operation IN ('CAPTURE_TRADE', 'SETTLE_TRADE'));

CREATE FUNCTION settlement_history_immutable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'settlement_history_immutable'
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER settlement_journal_immutable
    BEFORE UPDATE OR DELETE ON settlement_journal
    FOR EACH ROW
    EXECUTE FUNCTION settlement_history_immutable();

CREATE TRIGGER posting_immutable
    BEFORE UPDATE OR DELETE ON posting
    FOR EACH ROW
    EXECUTE FUNCTION settlement_history_immutable();

CREATE TRIGGER settlement_attempt_immutable
    BEFORE UPDATE OR DELETE ON settlement_attempt
    FOR EACH ROW
    EXECUTE FUNCTION settlement_history_immutable();

CREATE FUNCTION trade_protect_settlement_state()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.id IS DISTINCT FROM NEW.id
        OR OLD.external_trade_id IS DISTINCT FROM NEW.external_trade_id
        OR OLD.buyer_id IS DISTINCT FROM NEW.buyer_id
        OR OLD.seller_id IS DISTINCT FROM NEW.seller_id
        OR OLD.security_id IS DISTINCT FROM NEW.security_id
        OR OLD.quantity IS DISTINCT FROM NEW.quantity
        OR OLD.cash_amount IS DISTINCT FROM NEW.cash_amount
        OR OLD.settlement_date IS DISTINCT FROM NEW.settlement_date
    THEN
        RAISE EXCEPTION 'trade_terms_immutable'
            USING ERRCODE = '23514';
    END IF;

    IF OLD.status = 'SETTLED' THEN
        RAISE EXCEPTION 'trade_settled_immutable'
            USING ERRCODE = '23514';
    END IF;

    IF OLD.status = 'READY'
       AND NEW.status = 'SETTLED'
       AND OLD.journal_id IS NULL
       AND NEW.journal_id IS NOT NULL
    THEN
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'trade_settlement_transition_invalid'
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trade_protect_settlement_state
    BEFORE UPDATE ON trade
    FOR EACH ROW
    EXECUTE FUNCTION trade_protect_settlement_state();

CREATE FUNCTION settlement_journal_assert_shape()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    posting_count INTEGER;
    cash_asset_id UUID;
    trade_buyer UUID;
    trade_seller UUID;
    trade_security UUID;
    trade_quantity BIGINT;
    trade_cash BIGINT;
    buyer_cash UUID;
    seller_cash UUID;
    buyer_security UUID;
    seller_security UUID;
BEGIN
    SELECT buyer_id, seller_id, security_id, quantity, cash_amount
    INTO trade_buyer, trade_seller, trade_security, trade_quantity, trade_cash
    FROM trade
    WHERE id = NEW.trade_id;

    SELECT id
    INTO cash_asset_id
    FROM asset
    WHERE code = 'AUD' AND type = 'CASH';

    IF cash_asset_id IS NULL THEN
        RAISE EXCEPTION 'settlement_journal_shape'
            USING ERRCODE = '23514';
    END IF;

    SELECT count(*)
    INTO posting_count
    FROM posting
    WHERE journal_id = NEW.id;

    IF posting_count <> 4 THEN
        RAISE EXCEPTION 'settlement_journal_shape'
            USING ERRCODE = '23514';
    END IF;

    SELECT id INTO buyer_cash
    FROM account
    WHERE participant_id = trade_buyer AND asset_id = cash_asset_id;

    SELECT id INTO seller_cash
    FROM account
    WHERE participant_id = trade_seller AND asset_id = cash_asset_id;

    SELECT id INTO buyer_security
    FROM account
    WHERE participant_id = trade_buyer AND asset_id = trade_security;

    SELECT id INTO seller_security
    FROM account
    WHERE participant_id = trade_seller AND asset_id = trade_security;

    IF buyer_cash IS NULL OR seller_cash IS NULL
       OR buyer_security IS NULL OR seller_security IS NULL
    THEN
        RAISE EXCEPTION 'settlement_journal_shape'
            USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM posting
        WHERE journal_id = NEW.id
          AND account_id = buyer_cash
          AND direction = 'DEBIT'
          AND amount = trade_cash
    ) THEN
        RAISE EXCEPTION 'settlement_journal_shape'
            USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM posting
        WHERE journal_id = NEW.id
          AND account_id = seller_cash
          AND direction = 'CREDIT'
          AND amount = trade_cash
    ) THEN
        RAISE EXCEPTION 'settlement_journal_shape'
            USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM posting
        WHERE journal_id = NEW.id
          AND account_id = buyer_security
          AND direction = 'CREDIT'
          AND amount = trade_quantity
    ) THEN
        RAISE EXCEPTION 'settlement_journal_shape'
            USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM posting
        WHERE journal_id = NEW.id
          AND account_id = seller_security
          AND direction = 'DEBIT'
          AND amount = trade_quantity
    ) THEN
        RAISE EXCEPTION 'settlement_journal_shape'
            USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM posting p
        INNER JOIN account a ON a.id = p.account_id
        WHERE p.journal_id = NEW.id
        GROUP BY a.asset_id
        HAVING sum(p.signed_amount) <> 0
    ) THEN
        RAISE EXCEPTION 'settlement_journal_shape'
            USING ERRCODE = '23514';
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER settlement_journal_shape
    AFTER INSERT ON settlement_journal
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION settlement_journal_assert_shape();
