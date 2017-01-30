/* Delete any old schema */
DROP SCHEMA finance;

CREATE SCHEMA finance;

/* Create a table with RIC as primary key */
CREATE TABLE finance.quotes (
  RIC VARCHAR(45) NOT NULL,
  BID DECIMAL(10, 2) NULL,
  ASK DECIMAL(10, 2) NULL,
  PRIMARY KEY (RIC));

/* Insert some test values in it */
INSERT INTO finance.quotes VALUES 
("INSTR1", 38.44, 38.60),
("INSTR2", 44.79, 45.02),
("INSTR3", 14.30, 14.90);


DROP TRIGGER autopublish1;
DROP TRIGGER autopublish2;

/* Create a trigger for insert */
DELIMITER //
CREATE TRIGGER autopublish1
AFTER INSERT ON finance.quotes 
FOR EACH ROW
BEGIN
	SELECT * 
	INTO OUTFILE 'c:/Temp/Temp/newData.csv' 
	FIELDS TERMINATED BY ','
	LINES TERMINATED BY '\n'
	FROM finance.quotes 
	where RIC = new.RIC;
END; //
DELIMITER ;

/* Create a trigger for update */
DELIMITER //
CREATE TRIGGER autopublish2
AFTER UPDATE ON finance.quotes 
FOR EACH ROW
BEGIN
	SELECT * 
	INTO OUTFILE 'c:/Temp/Temp/updData.csv' 
	FIELDS TERMINATED BY ','
	LINES TERMINATED BY '\n'
	FROM finance.quotes 
	where RIC = new.RIC;
END; //
DELIMITER ;

