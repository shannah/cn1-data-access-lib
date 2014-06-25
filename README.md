#Codename One Data Access Library

This provides a Data access layer for SQLite databases in [Codename One](http://www.codenameone.com).

##Contents

1. [Motivation](#motivation)
2. [Features](#features)
3. [Requirements](#requirements)
4. [Supported Platforms](#supported-platforms)
5. [License](#license)
6. [Support](#support)
7. [Installation](#installation)
8. [Getting Started](#getting-started)
	1. [Setting up DAOProvider](#setting-up-daoprovider)
	2. [Getting DAO for People Table](#getting-dao-for-people-table)
	3. [Creating a New Record](#creating-a-new-record)
	4. [Fetching All People](#fetching-all-people)
	5. [Fetching Person By ID](#fetching-person-by-id)
	6. [Fetching People With Query](#fetching-people-with-query)
	7. [Importing from List](#importing-from-list)
	8. [Importing from a Map with Nested Lists](#importing-from-a-map-with-nested-lists)
	9. [Importing from a JSON data set](#importing-from-a-json-data-set)
9. [Creating a Custom DAO Class](#creating-a-custom-dao-class)
10. [Custom Entity Classes](#custom-entity-classes)
11. [Database Creating and Versioning](#database-creating-and-versioning)
12. [Limitations and Constraints](#limitations-and-constraints)
13. [Credits](#credits)

##Motivation

In almost all Codename One apps that I write (that use a database) there are two key functions that always occur:

1. Creating the database schema, and updating the database schema for successive versions of the app.
2. Loading data from a Web service into the database.  Usually from a JSON data source, but not always.
3. Loading data from the database into some sort of Java object - let's call them Entity Objects.

Most SDKs have a solution for this already, but Codename One currently doesn't.  So I wrote one.

##Features

1. Data Access Objects for reading and writing to the database without SQL.
2. Imports data from JSON or other data structures into the database without using SQL.
3. Database versioning support.
4. Entity object caching with weak references for good performance and no memory leaks.

##Requirements

None.  Just [Codename One](http://www.codenameone.com).

##Supported Platforms (Status)

* Simulator (Tested)
* Android (Tested)
* iOS (Tested)
* Windows Phone (Untested but should work)
* J2ME & Legacy RIM devices (Unsupported as they don't support SQLite).


##License

Apache 2.0

##Support

Post issues in the issue tracker.

##Installation

1. Download [CN1DataAccess.cn1lib](https://github.com/shannah/cn1-data-access-lib/blob/master/dist/CN1DataAccess.cn1lib?raw=true) and copy into your app's "lib" directory.
2. Right click on your project in the Netbeans project explorer, and select "Refresh Libs".


##Getting Started

For the first example, I'm going to assume you already have a database in your app, so I'll save the versioning and database creation features for a later example.  Let's suppose we have a database with a "people" table as follows:

~~~
CREATE TABLE people (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR,
    age INTEGER,
    bio VARCHAR
)
~~~ 

###Setting Up DAOProvider

~~~
DAOProvider daoProvider = new DAOProvider(myDatabase);
~~~

###Getting DAO for People table
~~~
DAO people = daoProvider.get("people"); // gets a DAO for the people table.
~~~

### Creating a New Record

~~~
Map person =  people.newObject();
person.put("name", "Steve");
person.put("age", 35);
person.put("bio", "Likes long walks on the beach");
people.save(person);
~~~


### Fetching All People

~~~
List<Map> thePeople = people.fetchAll();
for ( Map person : thePeople ){
    Log.p("Person "+person.get("id")+" is "+person.get("name"));
}
~~~


###Fetching Person By ID
~~~
Map person = people.getById(1);
~~~

###Fetching People with Query
~~~
List<Map> matches = people.fetch(new String[]{"name","Steve"});
  // Fetches all people where name=Steve
  
matches = people.fetch(new String[]{
	"name", "Steve",
	"age", "35"
	});
  // fetches all people where name=Steve and age=35
~~~

###Importing from a List
~~~
List<Map> importRecords = new ArrayList<Map>();
Map row = new HashMap();
row.put("name", "John");
row.put("age", 50);
importRecords.add(row);
row = new HashMap();
row.put("name", "Susan");
row.put("age", 23);
importRecords.add(row);
// ... etc...

people.importSet(importRecords);
~~~

###Importing from a Map with nested lists

~~~

// First set up nested data structure
Map map = new HashMap();
List<Map> importRecords = new ArrayList<Map>();
Map row = new HashMap();
row.put("name", "John");
row.put("age", 50);
importRecords.add(row);
row = new HashMap();
row.put("name", "Susan");
row.put("age", 23);
importRecords.add(row);
// ... etc...

Map tables = new HashMap();
map.put("tables", tables );

tables.put("people", importRecords);

// Now import from the data structure specifying path
// to the list we want to import
people.importSet(map, "tables/people");

~~~

###Importing from a JSON Data Set

Suppose the server returns the following JSON:

~~~
{
	tables : {
		people : [
			{ name : "Steve", age: 35 },
			{ name : "John", age: 50},
			{ name : "Susan", age: 23}
		]
	}
}
~~~

Now suppose we load the Dataset with our connection request.

~~~
ConnectionRequest conn = new ConnectionRequest(){
    @Override
    protected void readResponse(InputStream input) throws IOException {
        people.importJSON(input, "tables/people");
    }
};
conn.setUrl(theUrlToTheWebService);
NetworkManager.getInstance().addToQueueAndWait(conn);
~~~


## Creating a Custom DAO class

The above examples all use the default generic DAO, which uses a Map for the entity objects.  However, you can also create your own DAO class that is  set up to use your own custom entity classes.

The DAO class is an abstract class.  Subclasses must implement a small set of methods:

1. newObject() : Creates and returns a new entity object.  This can be of any class you like.
2. getId(Object) : Returns the ID for a specified entity object.
3. unmap(Object,Map) : Copies values from a provided Map into the given entity object.
4. map(Object,Map) : Copies values from the provided entity object into the given map.

Let's look at a simple example that just uses a HashMap for the entity object class.  This would basically be the same as the Generic default DAO.

~~~

public class MyDAO extends DAO<Map> {

    public MyDAO(DAOProvider provider){
        super("people", provider);
            // "people" is the table name that this DAO is used for.
    }

    public Map newObject(){ return new HashMap();}
    
    public long getId(Map obj){ return (long)obj.get("id"); }
    
    public void map(Map object, Map values){
        values.putAll(object);
    }
    
    public void unmap(Map object, Map values){
        object.putAll(values);
    }
}

~~~


You would then need to register this class with the DAOProvider object.

~~~
daoProvider.set("people", new MyDAO(daoProvider));
~~~

From then on you can cast calls from daoProvider.get() to MyDAO.

~~~
MyDAO people = (MyDAO)daoProvider.get("people");
~~~

##Custom Entity Classes

The previous section showed a custom DAO class, but it still only used a Map for the entity object.  Generally, if you implement a custom DAO class, you'll be using it with a custom entity class, or POJO (Plain old Java Object) also.

The custom entity class:

~~~
public class Person {
    private long id;
    private String name;
    private int age;
    
    public long getId(){ return id;}
    public void setId(long id){ this.id = id;}
    public String getName(){ return name;}
    public void setName(String name){ this.name = name;}
    public int getAge(){ return age;}
    public void setAge(int age){ this.age = age;}
}
~~~

And the custom DAO class for Person:

~~~

public class PersonDAO extends DAO<Person> {

    public PersonDAO(DAOProvider provider){
        super("people", provider);
            // "people" is the table name that this DAO is used for.
    }

    public Person newObject(){ return new Person();}
    
    public long getId(Person obj){ return person.getId(); }
    
    public void map(Person object, Map values){
        values.put("id", object.getId());
        values.put("name", object.getName());
        values.put("age", object.getAge());
        
    }
    
    public void unmap(Person object, Map values){
        object.setId(NumberUtil.longValue(values.get("id")));
        object.setName(values.get("name"));
        object.setAge(NumberUtil.intValue(values.get("age")));
    }
}

~~~

*Note that this snippet makes use of a NumberUtil utility class that handles conversion of non-specified types of inputs into long and int values.  Without it you would have to add some validation in retrieving values from maps and placing them in primitive attributes.

###Why Create a Custom DAO class?

The idea of a DAO is to shield the rest of the application from the details of SQL, and possibly some of the data structure (although the correspondence of DAOs with tables does reveal some information about the table structure).  Therefore any queries that your application needs to make to the database, should be handled inside a DAO class.

For example, if it is common to want to search for people in a department, you might add a method like the following to your PersonDAO class.

~~~

public List<Person> fetchPeopleInCompany(Company company){
    return this.fetchAll("select * from people p inner join companies_people cp on p.id=cp.person_id where cp.company_id=?", new String[]{""+company.getId()});
}

~~~

This will fetch all of the people in the specified company. This makes use of the protected fetchAll(String,String[]) method in DAO, which takes an SQL query and returns a set of entity objects.

Another example, is a method to add a person to a company.

~~~
public void addPersonToCompany(Person person, Company company){
	db().execute("replace into company_people (person_id,company_id) values (?,?)",
	    new Object[]{""+person.getId(), ""+company.getId()});
}
~~~

Then the general usage would be:

~~~

DAOProvider provider = new DAOProvider(db);
PersonDAO personDAO = new PersonDAO(provider);
CompanyDAO companyDAO = new CompanyDAO(provider);
provider.set("people", personDAO);
provider.set("companies", companyDAO);

Company sony = companyDAO.fetchOne(new String[]{"name", "Sony"});
Person steve = personDAO.getById(1); // Say we know steve is id 1

personDAO.addPersonToCompany(steve, sony);

// Now let's check to make sure it worked
List<Person> sonyEmployees = companyDAO.fetchPeopleInCompany(sony);

if ( sonyEmployees.contains(steve) ){
    Log.p("Success!!! Steve is in the company");
} else {
    Log.p("Failed!!! Steve is not in the company");
}

~~~

###Entity Object Uniqueness

The above example highlights a special property of entity objects:  their uniqueness.  Two entity objects that encapsulate the same row in the database, will always be the same object.  Therefore, we were able to check if steve is an employee of sony using `sonyEmployees.contains(steve)` because if the result set included a row representing the "steve" record, then it would be the same entity object.

There are some consequences of this design.  If you make changes to an entity object, then fetch that object from the database again before saving the changes, your changes will be overwritten with the values in the database.  Be aware of this.

##Database Creation and Versioning

One painful aspect of SQLite in mobile apps is managing database updates between versions of your application.  For example, suppose you have released version 1.0 of your application, and it uses an SQlite database with a People table defined as in our previous examples (i.e. with columns "id", "name", and "age"). This, perhaps was created at some point with code like:

~~~
db.execute("CREATE TABLE IF NOT EXISTS people ("+
    "id INTEGER PRIMARY KEY AUTOINCREMENT, "+
    "name VARCHAR,"+
    "age INTEGER)");
~~~

 In version 1.1, you have changed the table structure by adding a column "interests".  You now have a problem.  If you simply modify your create table statement, then new installs will get the correct table structure, but existing installs (i.e. users updating from 1.0 to 1.1) will still have the old table structure sans the "interests" column.

The Codename One Data Access Library solves this problem by supporting versioned SQL files (referred elsewhere in this document and throughout javadocs as "config files"). You simply add a "setup.sql" file to your source root directory with the following format:

~~~
--Version:1
CREATE TABLE people ( 
    id INTEGER PRIMARY KEY AUTOINCREMENT, 
    name VARCHAR, 
    age INTEGER
);
--
CREATE TABLE companies (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	name VARCHAR
);
--
CREATE TABLE company_people (
	company_id INTEGER,
	person_id INTEGER,
	PRIMARY KEY (company_id, person_id)
)
--
~~~

Some things to notice about the format of this file:

1. **SQL queries are separated by lines containing only "--".**  This delimiter must be there, as the library uses it to split the content into the separate queries.
2. **The first line "--Version:1" marks the version of the schema.** It indicates that all of the SQL queries that follow should be executing when performing an update to version 1 of the schema.

Now we can use this config file from our code as follows:

~~~
DAOProvider provider = new DAOProvider(db, "/setup.sql", 1);
~~~

Notice the last 2 parameters:
1. We provide the path (within the source root) of our SQL config file.
2. We provide the version of the schema that we wish to conform to.  In our case, there is only one version.

Inside this constructor now, it is checking to see the schema version of the database.  If the schema hasn't been created yet (i.e. the version is 0), then it will look in the config file and find all of the updates <= version 1, and execute the SQL contained therein.  It stores the current version of the databse inside the database itself so that it can tell if it requires an update.

###Updating Database Version

Now, let's look at the scenario where we want to change the structure of the database for our app's 1.1 release.  We need only do 2 things to make this happen:

1. Update the setup.sql file using a new "--Version" section:

~~~
--Version:1
CREATE TABLE people ( 
    id INTEGER PRIMARY KEY AUTOINCREMENT, 
    name VARCHAR, 
    age INTEGER
);
--
CREATE TABLE companies (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	name VARCHAR
);
--
CREATE TABLE company_people (
	company_id INTEGER,
	person_id INTEGER,
	PRIMARY KEY (company_id, person_id)
)
--Version:2
ALTER TABLE PEOPLE ADD COLUMN interests VARCHAR;
--
~~~

2. Change the 3rd parameter of the DAOProvider constructor to tell it that we wish to use Version 2 of our schema instead of version 1:

~~~
DAOProvider provider = new DAOProvider(db, "/setup.sql", 2);
~~~ 

Now, if users are installing the app for the first time, it will execute all of the SQL statements for version 1 and 2 (because the databse will be starting from 0).  But if users had previously installed the app and already had version 1 of the schema, then it would only execute the statements in version 2 (i.e. the ALTER TABLE statement that we added).

##Limitations and Constraints

1. Currently tables for which you register a DAO *must* contain a column named "id" of type INTEGER, and it should be AUTOINCRENT.  This is the column that will be treated as the primary key of the table.


##Credits

* Codename One Data Access Library developed and maintained by [Steve Hannah](http://www.sjhannah.com)
* Special thanks to the [Codename One team](http://www.codenameone.com) for creating such a fantastic platform for mobile app development.