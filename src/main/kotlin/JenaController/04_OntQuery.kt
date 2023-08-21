package JenaController

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.apache.jena.ontology.OntModel
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryExecutionFactory

class OntQuery(val ont:OntModel) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(OntQuery::class.java)
        val stsURI = "http://paper.9bon.org/ontologies/sensorthings/1.1#"
    }

    fun enShort(inp: String): String {
        if (inp.contains(stsURI)) {
            val inpPart = inp.split(stsURI)
            return "sts-${inpPart[1]}"
        } else {
            return inp
        }
    }

    fun deShort(inp: String): String {
        if (inp.contains("sts-")) {
            val inpPart = inp.split("sts-")
            return "${stsURI}${inpPart[1]}"
        } else {
            return inp
        }
    }

    fun browseQuery(resourceURI: String): List<Array<String>> {
        logger.info("Browse => ${resourceURI}")
    
        val queryString = """
            SELECT ?property ?value WHERE {
                { <$resourceURI> ?property ?value. }
                UNION
                { ?value ?property <$resourceURI>. }
            }
        """.trimIndent()
    
        val query = QueryFactory.create(queryString)
        val qexec = QueryExecutionFactory.create(query, ont)
    
        val resultsList = mutableListOf<Array<String>>()
        val results = qexec.execSelect()
    
        while (results.hasNext()) {
            val soln = results.nextSolution()
            val property = soln.getResource("property").toString()
            val text: String
            val link: String
            if (soln.get("value").isResource) {
                text = soln.getResource("value").toString()
                link = enShort(soln.getResource("value").toString())
            } else {
                text = soln.getLiteral("value").toString()
                link = "x"
            }
            resultsList.add(arrayOf(property, text, link))
        }
        return resultsList
    }
    
}