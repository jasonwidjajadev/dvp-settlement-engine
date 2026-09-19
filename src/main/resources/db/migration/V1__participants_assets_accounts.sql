-- Phase 1 schema only: participant, asset, and account.
-- Balances are integer quantities. AUD uses minor units (100000 = AUD 1,000).
-- Securities use whole units (10 = 10 EQ1). Floating-point types are not used.

CREATE TABLE participant (
    id   UUID PRIMARY KEY,
    name TEXT NOT NULL,
    CONSTRAINT participant_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE TABLE asset (
    id   UUID PRIMARY KEY,
    code TEXT NOT NULL,
    type TEXT NOT NULL,
    CONSTRAINT asset_code_unique UNIQUE (code),
    CONSTRAINT asset_code_not_blank CHECK (length(btrim(code)) > 0),
    CONSTRAINT asset_type_supported CHECK (type IN ('CASH', 'SECURITY'))
);

CREATE TABLE account (
    id              UUID PRIMARY KEY,
    participant_id  UUID NOT NULL,
    asset_id        UUID NOT NULL,
    opening_balance BIGINT NOT NULL,
    current_balance BIGINT NOT NULL,
    CONSTRAINT account_participant_fk
        FOREIGN KEY (participant_id) REFERENCES participant (id),
    CONSTRAINT account_asset_fk
        FOREIGN KEY (asset_id) REFERENCES asset (id),
    CONSTRAINT account_participant_asset_unique UNIQUE (participant_id, asset_id),
    CONSTRAINT account_opening_balance_non_negative CHECK (opening_balance >= 0),
    CONSTRAINT account_current_balance_non_negative CHECK (current_balance >= 0)
);
