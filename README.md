# Publishing content from internal database to TREP

Database is/was a central entity in many legacy systems. Applications were built and maintained around database capabilities, resulting in accumulation of high quality and rare information in the database. With the advent of newer technologies, which have moved from database centric to message oriented architecture, often times there is a need for an adapter which can extract the information from database and provide it on a bus. This article will briefly discuss various mechanisms to do such a thing. In this article we will discuss techniques which can be used to extract data from a table, and develop a minimal OMM publisher using Thomson Reuters Elektron Message API to publish this extracted data to TREP infrastructure. The sample code provided with this article is specifically for MySQL database, but can be extended to any other database and complex schema's. While a simple usage scenario is discussed in this article, an application developer should also consider possibilities such as when database information is published, but the transaction rolls back.

Thomson Reuters also provides a product called Database Publishing System (DPS), which is a tightly integrated component of the Thomson Reuters Enterprise Platform for Real-Time that publishes information stored inside relational databases or flat files, in CSV and XML formats, and makes it available to any data consuming application.


### Prerequisites
Article assumes knowledge of [TREP](#glossary) infrastructure and access to an [ADS](#glossary) with publishing entitlements. Reader is assumed to have basic knowledge of database concepts like Schemas, Tables, Triggers etc. A database with administrator privileges is required to test the example. The sample code presented uses SQL and Java language. Familiarity and access to [EMA API](#glossary) toolkit is required.


### Methodologies
The fundamental issue with publishing database contents is how to extract that data in the first place. Once extracted, it is easy to publish this data using Thomson Reuters API's and there are numerous publishing examples demonstrating that. Unlike SQL, the data extraction process and techniques are not standardized across different database providers. There are a few ways in which this can be accomplished and there is no single best technique, or one which can be used with every database provider. The desired technique which developer may end up using, will depend upon database in question, ability to access and/or modify the database schema etc. Common techniques to achieve this are:

1. Periodic polling of database table
2. Polling and updating the database table
3. In-database actions - executable code
4. Database triggers

Let us take a brief look at these.

##### Periodic polling of database table

This technique involves an external application which uses a database connector ([JDBC/ODBC](#glossary)) and extracts all the data from desired schema. The extracted data is hashed and kept in memory or in local filesystem depending on size. The extraction process is repeated periodically; compared with cache and any differences are published. The cache is updated with new information after publishing.

**Pros:**   
Completely external application which requires no change to database setup.   
It works with all the database providers.

**Cons:**   
Additional load on database because of periodic polling and complete re-read of entire schema.   
It may not be feasible to maintain external cache for extremely large datasets.   


##### Polling and updating the database table

This technique involves an external application which uses a database connector ([JDBC/ODBC](#glossary)) and extracts/updates the desired schema's. Unlike previous technique, this application only selects the new or changed information from database, and then marks this information as "already-processed". This can be accomplished simply by using following SQL statements in the code:

```java
// BEGIN Transaction   
// extract data   
unpubIDs = execute("SELECT * FROM table where published = 'f'");   
// publish data   
for each (id : unpubIDs) {   
  publishMessage(id, data);   
}   
// update table   
execute("update table set published = 't' where published = 'f'")   
// COMMIT Transaction   
```

**Pros:**   
Application design is simple and this approach works with all database providers.   
There is no need to maintain huge external cache.

**Cons:**   
The database schema needs modification and write access provided to an external application, which is not always possible.   
There is a possibility of race condition with non transactional database.


##### In-database actions - executable code

This technique exploits the in-database code execution capability provided by some database vendors. Oracle and PostgreSQL are two such providers. The user code is allowed to inspect and act of the data that has or is about to change. This user code can then invoke the API calls required to publish the data directly from within database.

**Pros:**   
Direct interface to data without any overhead of external extraction.

**Cons:**   
Very provider specific and may not work with all providers.   
In-database code often executes with system level privileges, leading to security risks.   
The concept of persistent session which stays connected to publisher, may not be available.   
User code may consume additional CPU resources which may put additional load on database.


##### Database triggers

Database triggers is the ability to execute SQL statements within database, upon meeting certain trigger conditions. The trigger SQL code executes within the context of changed dataset. It is a standard concept although its implementation and capabilities vary across difference database providers. It is possible to write a database trigger to extract the modified data and write it to an external file, which is picked up by an external application and then published.
   
**Pros:**   
External application can maintain sessions and use any API's and is not constrained by provider in-database code capabilities.   
The application does not need any read/write access to database.   
Does not exert much additional load on the database.

**Cons:**   
Not standard across all database providers.   
Some databases may not have the ability to pass on the trigger'ed dataset outside of database.


This technique of using database trigger is explored in this article, using MySQL database and Elektron Message API.


### Solution

We will develop an adapter application which will read database information and appear as non-interactive OMM provider to enterprise platform. Since databases can be updated anytime, the **database triggers** mechanism as described above will be utilized. In our case the trigger will extract the updated data into an external file, which the adapter code will read and publish to [TREP](#glossary) infrastructure.

The data model of published content will be specific to information within database. For the purpose of this article, we will assume a flat non-relational schema which will be encoded and published as [OMM](#glossary) based Market Price message. A supporting trigger code will reside in our database and will be invoked whenever the data changes in the monitored table. This trigger will write changed information (to be published) to a flat file. 

A Java application built using [EMA](#glossary) API will monitor the filesystem for this flat file, and parse and publish the extracted data. 

Lets dig deeper into the setup.

#### Database:
The database schema name we are going to use is **"finance"**, and the table of interest is called **"quotes"** which contains three fields - _Instrument name_, _Bid price_ and _Ask price_ with sample data as shown:

	finance.quotes

	RIC		BID		ASK
	----------------------
	INSTR1	38.44	38.60
	INSTR2	44.79	45.02
	INSTR3	14.30	14.90

We need to publish any insert or update to existing dataset. The SQL code in attached sample code will create and populate this schema and table.


#### Trigger:
A trigger will monitor this table for changes. In MySQL a trigger code can also output the trigger'ed data to a file. Define a trigger in **"finance"** as:

```sql
CREATE TRIGGER autopublish1   
AFTER INSERT ON finance.quotes   
FOR EACH ROW   
BEGIN   
  SELECT * INTO OUTFILE 'tmp/pub/newData.csv'   
  FIELDS TERMINATED BY ','   
  LINES TERMINATED BY '\n'   
  FROM finance.quotes   
  WHERE RIC = new.RIC;   
END;
```

This will write new data (database insert) into 'newData.csv' file. A similar trigger is created for SQL Update as well. Complete trigger code is in attached sample code. The "Scanner" class from application loads and parses the information contained in this file. In the attached code "Scanner" class hard segregates the data into predefined "BID" and "ASK" buckets. It can be modified to be driven by application configuration or the published fields names can also be extracted from the database.


### Provider
The published data is a [RDM](#glossary) Market price message, which is an [OMM](#glossary) Field List where the field entries are name-value data pairs. We will use a custom service to publish the sample derived data from database and to distinguish it from market data provided by Thomson Reuters. The "Provider" class in our application handles all the publishing tasks. It maintains a handle to all the published items, and publishes an Image or an Update message.

```java
FieldList fieldList = EmaFactory.createFieldList();

data.forEach((key ,value) -> {
  int fid = key.intValue();
  int dValue = value.intValue();
  // add read data into OMM fieldlist
  fieldList.add( EmaFactory.createFieldEntry().real(fid, dValue, OmmReal.MagnitudeType.EXPONENT_NEG_2));
});
```

Here the data from file which has been parsed into a Key/Value Map by "Scanner" class is marshaled into an OMM FieldList which is then packaged as a Refresh or Update message and published by OMMProvider.


### Sample code
Sample code for this article is available in src directory. This sample uses Java, EMA and MySQL database.


### Testing
To check the end to end process flow, start the sample by providing Service name, ADS host name etc on the command line. Upon successful startup, the console window should display API messages showing successful login to ADS, and that application is monitoring the target directory for database files. Whenever any new data is inserted into target table, our trigger **"autopublish1"** will be invoked which will write it to a file, and that data will be published out to TREP.

```shell
Started monitoring: C:\Temp
Processing file: newData3.csv
Publishing: INSTR3, data: {22=1476, 25=1492}
Outgoing Reactor message (Tue Jan 17 10:41:48 EST 2017):
<!-- rwfMajorVer="14" rwfMinorVer="1" -->
<REFRESH domainType="MARKET_PRICE" streamId="-1" containerType="FIELD_LIST" flags="0x48 (HAS_MSG_KEY|REFRESH_COMPLETE)"
groupId="0" State: Open/Ok/None - text: "UnSolicited Refresh Completed" dataSize="15">
	<key flags="0x03 (HAS_SERVICE_ID|HAS_NAME)" serviceId="0" name="INSTR3"/>
	<dataBody>
		<fieldList flags="0x08 (HAS_STANDARD_DATA)">
			<fieldEntry fieldId="22" data="0C05 96"/>
			<fieldEntry fieldId="25" data="0C06 BA"/>
		</fieldList>
	</dataBody>
</REFRESH>

Processing file: newData3.csv
Publishing: INSTR3, data: {22=1442, 25=1745}
Outgoing Reactor message (Tue Jan 17 10:43:23 EST 2017):
<!-- rwfMajorVer="14" rwfMinorVer="1" -->
<UPDATE domainType="MARKET_PRICE" streamId="-1" containerType="FIELD_LIST" flags="0x08 (HAS_MSG_KEY)" updateType="0" dataSize="15">
	<key flags="0x03 (HAS_SERVICE_ID|HAS_NAME)" serviceId="0" name="INSTR3"/>
	<dataBody>
		<fieldList flags="0x08 (HAS_STANDARD_DATA)">
			<fieldEntry fieldId="22" data="0C05 96"/>
			<fieldEntry fieldId="25" data="0C06 BA"/>
		</fieldList>
	</dataBody>
</UPDATE>
```

Any market data subscriber will be able to receive the image and update messages from TREP.

```shell
Jan 17, 2017 10:43:15 AM com.thomsonreuters.ema.access.ChannelCallbackClient reactorChannelEventCallback
INFO: loggerMsg
	ClientName: ChannelCallbackClient
	Severity: Info
	Text:    Received ChannelUp event on channel Channel
		Instance Name EmaConsumer_1
		Component Version ads2.6.1.L1.linux.rrg 32-bit
loggerMsgEnd

Item Name: INSTR3
Service Name: TD
Item State: Open / Ok / None / 'UnSolicited Refresh Completed'
BID(22): 14.76
ASK(25): 14.92

Item Name: INSTR3
Service Name: TD
BID(22): 14.42
ASK(25): 17.45
```

### References
EMA provider example

### Glossary
TREP:	Thomson Reuters Enterprise Platform   
ADS:	Advanced Distributing Server   
EMA:	Elektron Message API   
JDBC:	Java Database Connectivity   
ODBC:	Open Database Connectivity   
OMM:	Open Message Model
