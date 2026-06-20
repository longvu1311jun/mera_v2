-- Add view_id columns for each table in search_config
ALTER TABLE search_config ADD COLUMN IF NOT EXISTS khach_hang_view_id VARCHAR(64);
ALTER TABLE search_config ADD COLUMN IF NOT EXISTS lich_hen_view_id VARCHAR(64);
ALTER TABLE search_config ADD COLUMN IF NOT EXISTS trao_doi_view_id VARCHAR(64);
