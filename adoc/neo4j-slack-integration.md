## Neo4j Slack Integration

Our colleague [Andreas](http://twitter.com/akolleger) who loves Slack and brought it into the company,
suggested the other day that we could build a Slack and Neo4j integration to demonstrate how useful
a graph database backend would be.

And of course how much fun.

![](http://memegen.link/xy/slack-all-the/graph-things.jpg)

### Building Blocks

As it was only midnight we, the amazing [Nicole](http://twitter.com/_nicolemargaret) and [Michael](http://twitter.com/mesirii) decided to put something together.

We set up a [Neo4j 2.2.3 instance](http://neo4j.com/download) in the [cloud](http://neo4j.com/developer/guide-cloud-deployment).

Then we created a Python app for our slack-neo4j server and pushed it to [GitHub](http://github.com/neo4j-examples/slack-neo4j).

The application uses `web.py` for the webapp, `requests` to access the slack APIs and [py2neo](http://py2neo.org) to talk to Neo4j.

Next we pushed it to Heroku to make it available publicly so that slack could connect to it.

You have to provide environment variables to your Neo4j Server, your Slack API token and the team token configured with your slash command.
Find the details in the [project readme](https://github.com/neo4j-examples/slack-neo4j#neo4j-slack-integration).


### Slack Slash Command

Then we could set up a [slash command](https://api.slack.com/slash-commands), for our integration, the ideas came from Andreas:

* `/graph import` - import users, channels and membership into Neo4j
* `/graph cypher MATCH ... RETURN`  - execute read only cypher statement and return the results
* `/graph` - provide an overview of the data that's in the database


Implementing the app was straightforward. Parsing the POST payload and checking the team token then getting the first word of the `text` parameter as "command" to dispatch on.

### Getting Data from Slack into Neo4j

For the integration with Neo4j we send Cypher statements to Neo4j using py2neo's APIs. E.g.

````
from py2neo import Graph
graph = Graph(os.environ.get('NEO4J_URL'))

graph.cypher.execute("MATCH (u:User)-[:MEMBEROF]->(c:Channel) return u.screenname, c.name")
````

Sending requests to the Slack API with the token and getting the JSON response is straightforward with  `requests`.
We then pass the JSON response directly as parameters to a Cypher statement to create the graph structure in Neo4j.

````
res = requests.get("https://slack.com/api/channels.list?token={}".format(token))

query = """
UNWIND {channels} AS channel
MERGE (c:Channel {id:channel.id}) ON CREATE SET c.name = channel.name
"""
graph.cypher.execute_one(query, res.json())
````

So we can import the users, channels and membership, easy peasy.

`/graph import channels`
> slackbot: 115 users uploaded.
> Only you can see this message

`/graph import users`
> slackbot: 117 channels uploaded
> Only you can see this message

### Graph all the Slack Things

And to show your that it worked here is a graph of our slack universe:

![](https://dl.dropboxusercontent.com/u/14493611/blog/img/slack-neo4j-graph.jpg)

And some queries that show the most prolific people

````
/graph cypher match (u:User)-->() return u, count(*) as memberships order by memberships desc limit 3`

slackbot:
   | u                                                            | memberships
---+--------------------------------------------------------------+-------------
1 | (n200:User {fullname:"Michael",id:"U02HVJ36",username:"mh"})  |          64
2 | (n151:User {fullname:"Chris",id:"U0KLMP5X",username:"cl"})    |          37
3 | (n210:User {fullname:"Philip",id:"U02HDEF0EX",username:"pr"}) |          37
````

### Recommendations

And finally the big surprise, we want to recommend new channels to people.

We use traditional collaborative filtering for this, "channels of your colleagues that are not yet your channels".
But we also filter out prolific users and channels so that they don't distort the picture.

````
/graph cypher
MATCH (c:Channel) with toInt(count(*)*0.618) as channel_cutoff
MATCH (u:User) with toInt(count(*)*0.618) as user_cutoff, channel_cutoff

MATCH (u:User {username:"laeg"})-[:MEMBER_OF]->(c:Channel)
      <-[:MEMBER_OF]-(coll:User)-[:MEMBER_OF]->(reco:Channel)

WHERE size((c)<--())    < channel_cutoff
  AND size((reco)<--()) < channel_cutoff
  AND size((coll)-->()) < user_cutoff
  AND NOT (u)-[:MEMBER_OF]->(reco)

RETURN reco.name, count(*) AS freq
ORDER BY freq DESC
LIMIT 5;
````

````
slackbot:
  | reco.name           | freq
--+---------------------+------
1 | feedback            |  218
2 | dev-team            |  179
3 | sales_marketing     |  161
4 | marketing           |  142
5 | cypher-the-language |  125
````
