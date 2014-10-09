// script to import some data of the Kaggle Paper-Author challenge: https://www.kaggle.com/c/kdd-cup-2013-author-paper-identification-challenge/data

@Grab('com.xlson.groovycsv:groovycsv:1.0')
@Grab('org.neo4j:neo4j:2.1.4')
import static com.xlson.groovycsv.CsvParser.parseCsv
import org.neo4j.graphdb.*
 
enum Labels implements Label { Author, Paper }
enum Types implements RelationshipType { WROTE }
 
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
def papers = [:]
 
count = 0
time = start = System.currentTimeMillis()
 
def trace(output) {
  if (output || ++ count % 100_000 == 0) {
    now = System.currentTimeMillis()
        println "$count rows ${(now-time)} ms"
        time = now
    }
}

//tag::main[]
store=args[0]
dir=new File(args[1])
papers_file=new File(dir,"Paper.csv")
author_paper_file=new File(dir,"PaperAuthor.csv")

println "Importing data from ${papers_file} and ${author_paper_file} into ${store}"

batch = org.neo4j.unsafe.batchinsert.BatchInserters.inserter(store,config)
try {
  // create papers
  // Id,Title,Year,ConferenceId,JournalId,Keyword
  csv = papers_file.newReader()
  for (line in parseCsv(csv)) {
     if (line.values.size() < 3) continue
     paper = line.Id
     if (!papers[paper]) {
        papers[paper] = batch.createNode([id:paper,title:line.Title,year:line.Year],Labels.Paper)
     }
     trace()
  }
  csv.close()

  // create authors and wrote-relationships
  // PaperId,AuthorId,Name,Affiliation
  csv = author_paper_file.newReader()
  for (line in parseCsv(csv)) {
     if (line.values.size() < 3) continue
     author = line.AuthorId
     if (!authors[author]) {
        authors[author] = batch.createNode([id:author,name:line.Name],Labels.Author)
     }
     paper = line.PaperId
     if (papers[paper]) {
        batch.createRelationship(authors[author] ,papers[paper], Types.WROTE, NO_PROPS)
     }
     trace()
  }
  csv.close()
  batch.createDeferredConstraint(Labels.Author).assertPropertyIsUnique("name").create()
  batch.createDeferredSchemaIndex(Labels.Paper).on("title").create()
} finally {
   batch.shutdown()
   trace(true)
   println "Total $count Rows ${authors.size()} Authors and ${papers.size()} Papers took ${(now-start)/1000} seconds."
}
