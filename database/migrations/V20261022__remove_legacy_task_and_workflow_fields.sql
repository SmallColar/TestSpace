-- Manual PostgreSQL migration for the simplified task workflow.
-- Back up the database and review legacy rows before applying this script.
-- This script is intentionally not run automatically by the application.

BEGIN;

-- The old staged-confirmation status is no longer part of the task workflow.
UPDATE agent_task SET status = 'PENDING' WHERE status = 'WAIT_CONFIRM';

-- Preserve the old, separately-entered interface filter prompt by folding it
-- into the single recording goal prompt before removing filter_prompt.
UPDATE agent_task
SET recording_goal = CASE
    WHEN recording_goal IS NULL OR btrim(recording_goal) = ''
        THEN '接口筛选要求：' || btrim(filter_prompt)
    WHEN position(btrim(filter_prompt) IN recording_goal) > 0
        THEN recording_goal
    ELSE recording_goal || E'\n\n接口筛选要求：' || btrim(filter_prompt)
END
WHERE filter_prompt IS NOT NULL
  AND btrim(filter_prompt) <> '';

ALTER TABLE agent_task DROP COLUMN IF EXISTS filter_prompt;
ALTER TABLE agent_task DROP COLUMN IF EXISTS type;
ALTER TABLE agent_task DROP COLUMN IF EXISTS mode;
ALTER TABLE agent_task DROP COLUMN IF EXISTS config;

-- These tables belonged to the retired confirmation/report/public-variable
-- workflow. Current cases keep public variables in test_case.public_vars.
DROP TABLE IF EXISTS extracted_variable;
DROP TABLE IF EXISTS case_gen_report;
DROP TABLE IF EXISTS confirm_item;
DROP TABLE IF EXISTS stage_confirm_record;
DROP TABLE IF EXISTS public_var_candidate;
DROP TABLE IF EXISTS public_var;

COMMIT;
