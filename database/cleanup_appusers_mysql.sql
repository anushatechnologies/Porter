-- Run once against production MySQL/RDS after taking a backup.
-- AppUser is mapped to `appusers` in AppUser.java.
START TRANSACTION;

-- Keep the newest row for each phone number.
DELETE older
FROM appusers older
JOIN appusers newer ON newer.phone = older.phone AND newer.id > older.id
WHERE older.phone IS NOT NULL AND TRIM(older.phone) <> '';

-- Blank signup emails are not identifiers.
UPDATE appusers SET email = NULL
WHERE email IS NOT NULL AND TRIM(email) = '';

COMMIT;

-- Verify no duplicate non-blank phones remain before applying the index:
-- SELECT phone, COUNT(*) FROM appusers
-- WHERE phone IS NOT NULL AND TRIM(phone) <> ''
-- GROUP BY phone HAVING COUNT(*) > 1;

ALTER TABLE appusers ADD CONSTRAINT uk_appusers_phone UNIQUE (phone);
