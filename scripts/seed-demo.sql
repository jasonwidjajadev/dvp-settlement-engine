-- Deterministic Phase 1 demo data. Not a Flyway migration.
-- Invoke explicitly after V1 exists, for example:
--   docker exec -i dvp-postgres psql -U dvp -d dvp < scripts/seed-demo.sql
--
-- Reruns are safe: existing rows are left unchanged, including current balances.

-- Participants
-- 00000000-0000-0000-0000-000000000001 Alice
-- 00000000-0000-0000-0000-000000000002 Bob
INSERT INTO participant (id, name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Alice'),
    ('00000000-0000-0000-0000-000000000002', 'Bob')
ON CONFLICT (id) DO NOTHING;

-- Assets
-- 00000000-0000-0000-0000-0000000000a1 AUD cash
-- 00000000-0000-0000-0000-0000000000e1 EQ1 security
INSERT INTO asset (id, code, type) VALUES
    ('00000000-0000-0000-0000-0000000000a1', 'AUD', 'CASH'),
    ('00000000-0000-0000-0000-0000000000e1', 'EQ1', 'SECURITY')
ON CONFLICT (id) DO NOTHING;

-- Accounts. ON CONFLICT skips the pair if it already exists so a rerun
-- cannot create a second account or reset opening/current balances.
-- 00000000-0000-0000-0000-0000000000aa Alice AUD  100000 / 100000
-- 00000000-0000-0000-0000-0000000000ae Alice EQ1       0 /      0
-- 00000000-0000-0000-0000-0000000000ba Bob   AUD       0 /      0
-- 00000000-0000-0000-0000-0000000000be Bob   EQ1      10 /     10
INSERT INTO account (
    id,
    participant_id,
    asset_id,
    opening_balance,
    current_balance
) VALUES
    (
        '00000000-0000-0000-0000-0000000000aa',
        '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-0000000000a1',
        100000,
        100000
    ),
    (
        '00000000-0000-0000-0000-0000000000ae',
        '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-0000000000e1',
        0,
        0
    ),
    (
        '00000000-0000-0000-0000-0000000000ba',
        '00000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-0000000000a1',
        0,
        0
    ),
    (
        '00000000-0000-0000-0000-0000000000be',
        '00000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-0000000000e1',
        10,
        10
    )
ON CONFLICT (participant_id, asset_id) DO NOTHING;
