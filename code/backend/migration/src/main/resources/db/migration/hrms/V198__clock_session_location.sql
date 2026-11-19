-- Migration: V198__clock_session_location.sql
-- Description: W-98.2 Store geolocation coordinates and accuracy on hrms.clock_session

ALTER TABLE hrms.clock_session
    ADD COLUMN in_latitude NUMERIC(9,6),
    ADD COLUMN in_longitude NUMERIC(9,6),
    ADD COLUMN in_accuracy_m NUMERIC(8,2),
    ADD COLUMN out_latitude NUMERIC(9,6),
    ADD COLUMN out_longitude NUMERIC(9,6),
    ADD COLUMN out_accuracy_m NUMERIC(8,2);

ALTER TABLE hrms.clock_session
    ADD CONSTRAINT clock_session_in_latitude_check  CHECK (in_latitude IS NULL OR (in_latitude >= -90 AND in_latitude <= 90)),
    ADD CONSTRAINT clock_session_in_longitude_check CHECK (in_longitude IS NULL OR (in_longitude >= -180 AND in_longitude <= 180)),
    ADD CONSTRAINT clock_session_in_accuracy_check  CHECK (in_accuracy_m IS NULL OR in_accuracy_m >= 0),
    ADD CONSTRAINT clock_session_out_latitude_check  CHECK (out_latitude IS NULL OR (out_latitude >= -90 AND out_latitude <= 90)),
    ADD CONSTRAINT clock_session_out_longitude_check CHECK (out_longitude IS NULL OR (out_longitude >= -180 AND out_longitude <= 180)),
    ADD CONSTRAINT clock_session_out_accuracy_check  CHECK (out_accuracy_m IS NULL OR out_accuracy_m >= 0);

