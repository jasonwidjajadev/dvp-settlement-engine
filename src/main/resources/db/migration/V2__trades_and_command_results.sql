-- Phase 2 schema only: trade and command_result.
-- Captured terms stay immutable through application behaviour; PostgreSQL uniqueness
-- is the second line of defence against a second trade for the same reference.
-- Command results remember idempotent capture outcomes. A completed result cannot
-- be overwritten. A business rejection does not require a trade row.
-- Quantity and cash are integer units. AUD uses minor units (100000 = AUD 1,000).
-- Securities use whole units (10 = 10 EQ1). Floating-point types are not used.
--
-- no_surrounding_whitespace matches Java String.strip() / Character.isWhitespace,
-- which is what @NoSurroundingWhitespace uses. PostgreSQL btrim() only removes
-- ordinary space (U+0020) and would accept a leading tab or newline.

CREATE FUNCTION no_surrounding_whitespace(value text)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
AS $fn$
    SELECT left(value, 1) !~ $ws$[\t\n\x0B\f\r\x1C\x1D\x1E\x1F \u1680\u2000-\u2006\u2008-\u200A\u2028\u2029\u205F\u3000]$ws$
       AND right(value, 1) !~ $ws$[\t\n\x0B\f\r\x1C\x1D\x1E\x1F \u1680\u2000-\u2006\u2008-\u200A\u2028\u2029\u205F\u3000]$ws$;
$fn$;

CREATE TABLE trade (
    id                UUID PRIMARY KEY,
    external_trade_id TEXT NOT NULL,
    buyer_id          UUID NOT NULL,
    seller_id         UUID NOT NULL,
    security_id       UUID NOT NULL,
    quantity          BIGINT NOT NULL,
    cash_amount       BIGINT NOT NULL,
    settlement_date   DATE NOT NULL,
    status            TEXT NOT NULL,
    CONSTRAINT trade_external_trade_id_unique UNIQUE (external_trade_id),
    CONSTRAINT trade_external_trade_id_format CHECK (
        length(external_trade_id) BETWEEN 1 AND 128
        AND no_surrounding_whitespace(external_trade_id)
    ),
    CONSTRAINT trade_buyer_fk
        FOREIGN KEY (buyer_id) REFERENCES participant (id),
    CONSTRAINT trade_seller_fk
        FOREIGN KEY (seller_id) REFERENCES participant (id),
    CONSTRAINT trade_security_fk
        FOREIGN KEY (security_id) REFERENCES asset (id),
    CONSTRAINT trade_buyer_not_seller CHECK (buyer_id <> seller_id),
    CONSTRAINT trade_quantity_positive CHECK (quantity > 0),
    CONSTRAINT trade_cash_amount_positive CHECK (cash_amount > 0),
    CONSTRAINT trade_status_supported CHECK (status IN ('READY'))
);

CREATE TABLE command_result (
    command_key      TEXT PRIMARY KEY,
    operation        TEXT NOT NULL,
    request_identity TEXT NOT NULL,
    http_status      INTEGER,
    response_body    TEXT,
    location         TEXT,
    CONSTRAINT command_result_key_format CHECK (
        length(command_key) BETWEEN 1 AND 128
        AND no_surrounding_whitespace(command_key)
    ),
    CONSTRAINT command_result_operation_supported CHECK (operation IN ('CAPTURE_TRADE')),
    CONSTRAINT command_result_request_identity_not_blank CHECK (length(btrim(request_identity)) > 0),
    CONSTRAINT command_result_completion_state CHECK (
        (http_status IS NULL AND response_body IS NULL AND location IS NULL)
        OR
        (http_status IS NOT NULL AND response_body IS NOT NULL)
    )
);

CREATE FUNCTION command_result_prevent_completed_overwrite()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.http_status IS NOT NULL THEN
        RAISE EXCEPTION 'command_result_completed_immutable'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER command_result_completed_immutable
    BEFORE UPDATE ON command_result
    FOR EACH ROW
    EXECUTE FUNCTION command_result_prevent_completed_overwrite();
