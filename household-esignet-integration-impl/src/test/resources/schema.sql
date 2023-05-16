--CREATE VIEW household_view (
--household_ID BIGINT,
--group_name character varying(256) NOT NULL,
--phone_number character varying(256) NOT NULL,
--ID_number character varying(256) NOT NULL,
--password character varying(256) NOT NULL,
--tamwini_consented boolean default true,
--CONSTRAINT pk_household PRIMARY KEY (household_ID)
--);
CREATE TABLE household (
    household_ID BIGINT PRIMARY KEY,
    group_name character varying(256) NOT NULL,
    phone_number character varying(256) NOT NULL,
    ID_number character varying(256) NOT NULL,
    password character varying(256) NOT NULL,
    tamwini_consented boolean default true
);

INSERT INTO household(household_ID,group_name,phone_number,ID_number,password) VALUES
(1111112L,'test','1234567890','H01','12345');

--INSERT INTO household(household_ID,group_name,phone_number,ID_number,password) VALUES
--(12,"TEST2","12322","12345","123415234");

CREATE VIEW  household_view AS
SELECT
    household_ID,
    group_name,
    phone_number,
    ID_number,
    password,
    tamwini_consented
FROM
    household;