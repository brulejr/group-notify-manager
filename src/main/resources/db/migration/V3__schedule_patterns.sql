-- A third kind of schedule, PATTERN, written in words ("every other month on the 3rd
-- tuesday at 18:00 starting 2027-02-01") and stored in those canonical words, which
-- always give its start.
ALTER TABLE notifications ADD COLUMN pattern text;
