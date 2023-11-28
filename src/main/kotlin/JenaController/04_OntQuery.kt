package JenaController

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.apache.jena.ontology.OntModel
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.ResultSet

class OntQuery(val ont:OntModel) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(OntQuery::class.java)
        val stsURI = "http://paper.9bon.org/ontologies/sensorthings/1.1#"
        val udURI = "https://github.com/VCityTeam/UD-Graph/"
    }

    fun enShort(inp: String): String {
        var ret = ""
        if (inp.contains(stsURI)) {
            val inpPart = inp.split(stsURI)
            ret = "sts-${inpPart[1]}"
        }
        else if (inp.contains(udURI)) {
            val inpPart = inp.split(udURI)
            ret = "ud-${inpPart[1]}"
            if (ret.contains("#")) {
                val inpPart = ret.split("#")
                ret = "${inpPart[0]}+${inpPart[1]}"
            }
        }
        return ret
    }

    fun deShort(inp: String): String {
        var ret = ""
        if (inp.contains("sts-")) {
            val inpPart = inp.split("sts-")
            ret = "${stsURI}${inpPart[1]}"
        }
        else if (inp.contains("ud-")) {
            val inpPart = inp.split("ud-")
            ret = "${udURI}${inpPart[1]}"
            if (ret.contains("+")) {
                val inpPart = ret.split("+")
                ret = "${inpPart[0]}#${inpPart[1]}"
            }
        }
        return ret
    }

    fun browseQuery(q: String): List<Array<String>> {
        logger.debug("Browse => ${q}")

        val query = QueryFactory.create(q)
        val qexec = QueryExecutionFactory.create(query, ont)
    
        var resultsList = mutableListOf<Array<String>>()
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

        val extendedResourceInfo = resultsList.map { entry -> 
            entry.toList() + "" // 리스트로 변환 후 추가
        }.map { // 다시 배열로 변환
            it.toTypedArray()
        }.toMutableList()
        
        val sortedResourceInfo = extendedResourceInfo.sortedWith(
            compareBy({ if (it[2] == "x") 0 else 1 }, { it[0] }, { it[1] })
        )
    
        for (i in sortedResourceInfo.indices) {
            val currentEntry = sortedResourceInfo[i]
            val nextEntries = sortedResourceInfo.subList(i, sortedResourceInfo.size)
            val rowspan = nextEntries.takeWhile { it[0] == currentEntry[0] }.size
            currentEntry[3] = rowspan.toString()
        }

        return sortedResourceInfo
    }
    
    fun executeSPARQL(queryString: String): List<Array<String>> {
        val query = QueryFactory.create(queryString)
        val qexec = QueryExecutionFactory.create(query, ont)
    
        val resultsList = mutableListOf<Array<String>>()
        val results = qexec.execSelect()
        
        // 쿼리의 결과에서 반환된 변수 이름 목록을 가져옵니다.
        val resultVars = results.resultVars
    
        while (results.hasNext()) {
            val soln = results.nextSolution()
            
            // 각 변수에 대해 값을 가져옵니다.
            val row = resultVars.map { varName ->
                val rdfNode = soln.get(varName)
                when {
                    rdfNode.isResource -> rdfNode.asResource().toString()
                    rdfNode.isLiteral -> rdfNode.asLiteral().toString()
                    else -> ""
                }
            }.toTypedArray()
            
            resultsList.add(row)
        }
    
        return resultsList
    }

    fun isProperty(resourceURI: String): Boolean {
        val q = """
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            ASK WHERE {
                {<$resourceURI> a rdf:Property.}
                UNION
                {<$resourceURI> a owl:ObjectProperty.}
                UNION
                {<$resourceURI> a owl:DatatypeProperty.}
            }
        """.trimIndent()
    
        val query = QueryFactory.create(q)
        val qexec = QueryExecutionFactory.create(query, ont)
        return qexec.execAsk()
    }
    
    fun selectType(typeURL: String): ResultSet {
        val q = """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX bldg: <https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/building#>
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        PREFIX brid: <https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/bridge#>
        PREFIX app: <https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/appearance#>
        PREFIX gmlowl: <http://www.opengis.net/ont/gml#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX gen: <https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/generics#>
        PREFIX iso19136: <http://def.isotc211.org/iso19136/2007/Feature#>
        PREFIX geo: <http://www.opengis.net/ont/geosparql#>
        PREFIX core: <https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/core#>
        PREFIX iso19107_2207: <http://def.isotc211.org/iso19107/2003/CoordinateGeometry#>

        SELECT ?label
        WHERE {
        ?n rdf:type <$typeURL> .
        ?n skos:prefLabel ?label .
        }

        """.trimIndent()
        logger.info("selectType => ${q}")
        val query = QueryFactory.create(q)
        val qexec = QueryExecutionFactory.create(query, ont)
        return qexec.execSelect()
    }
}