//tag::dependencies[]
@Grab('com.xlson.groovycsv:groovycsv:1.0')
@Grab('org.neo4j:neo4j:2.1.4')
import static com.xlson.groovycsv.CsvParser.parseCsv
import org.neo4j.graphdb.*
//end::dependencies[]
 
//tag::enums[]
enum Labels implements Label { Author, Article }
enum Types implements RelationshipType { WROTE }
//end::enums[]
 
// [keys,value].transpose().collectEntries()
def toMap(line) { line.columns.inject([:]){ m,k,v -> m.put(k,line.values[v]);m } }
 
def config = [
"use_memory_mapped_buffers": "true",
"neostore.nodestore.db.mapped_memory": "250M",
"neostore.relationshipstore.db.mapped_memory": "1G",
"neostore.propertystore.db.mapped_memory": "500M",
"neostore.propertystore.db.strings.mapped_memory": "500M",
"neostore.propertystore.db.arrays.mapped_memory": "0M",
"cache_type": "none",
"dump_config": "true"
]
 
def NO_PROPS=[:]

// cache
def authors = [:]
 
count = 0
time = System.currentTimeMillis()
 
def trace(output) {
  if (output || ++ count % 100_000 == 0) {
    now = System.currentTimeMillis()
		println "$count rows ${(now-time)} ms"
		time = now
	}
}

//tag::main[]
store=args[0]
articles_file=new File(args[1])

println "Importing data from ${articles_file} into ${store}"

csv = articles_file.newReader()
batch = org.neo4j.unsafe.batchinsert.BatchInserters.inserter(store,config)
format = new java.text.SimpleDateFormat("yyyy-MM-dd")
try {
//tag::loop[]
  for (line in parseCsv(csv)) {
     name = line.author
     if (!authors[name]) {
        authors[name] = batch.createNode([name:name],Labels.Author)
     }
     date = format.parse(line.date).time
     article = batch.createNode([title:line.title, date:date],Labels.Article)
     batch.createRelationship(authors[name] ,article, Types.WROTE, NO_PROPS)
     trace()
  }
//end::loop[]
  batch.createDeferredConstraint(Labels.Author).assertPropertyIsUnique("name").create()
  batch.createDeferredSchemaIndex(Labels.Article).on("title").create()
} finally {
   csv.close()
   batch.shutdown()
   trace(true)
}
//end::main[]
