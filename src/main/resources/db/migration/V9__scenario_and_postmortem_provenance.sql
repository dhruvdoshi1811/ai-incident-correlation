ALTER TABLE analysis_requests ADD COLUMN retrieved_postmortem_titles TEXT;
ALTER TABLE postmortems ADD COLUMN source_incident_id UUID;
