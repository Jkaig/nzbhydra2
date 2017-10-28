ALTER TABLE INDEXERNZBDOWNLOAD
  ADD SEARCH_RESULT_ID BIGINT;

--good enough
UPDATE INDEXERNZBDOWNLOAD d
SET d.SEARCH_RESULT_ID = (SELECT x.ID
                         FROM SEARCHRESULT x
                         WHERE d.title = x.TITLE
                         LIMIT 1);

ALTER TABLE INDEXERNZBDOWNLOAD
  DROP COLUMN TITLE;

ALTER TABLE INDEXERNZBDOWNLOAD DROP CONSTRAINT FKMTRRF4HK98C9O3FDQJTS3HUPB;
DROP INDEX INDEXERNZBDOWNLOAD_INDEXER_ID_TIME_INDEX;
ALTER TABLE INDEXERNZBDOWNLOAD
  DROP COLUMN INDEXER_ID;