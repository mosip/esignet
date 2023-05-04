CREATE TABLE IF NOT EXISTS household_view (
household_ID BIGINT,
group_name character varying(256) NOT NULL,
phone_number character varying(256) NOT NULL,
ID_number character varying(256) NOT NULL,
password character varying(256) NOT NULL,
tamwini_consented boolean default true,
CONSTRAINT pk_household PRIMARY KEY (household_ID)
);