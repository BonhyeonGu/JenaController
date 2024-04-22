package JenaController

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import kotlin.random.Random

import org.apache.jena.ontology.OntModel
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.ResultSet

import org.apache.jena.query.Dataset
import org.apache.jena.query.DatasetFactory
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.apache.jena.rdf.model.ModelFactory


class OntQuery(val ont:OntModel) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(OntQuery::class.java)
        val staURI = "http://paper.9bon.org/ontologies/sensorthings/1.1#"
        val udURI = "https://github.com/VCityTeam/UD-Graph/"
        val scURI = "http://paper.9bon.org/ontologies/smartcity/0.2#"  // 새로운 URI
    }

    fun enShort(inp: String): String {
        var ret = ""
        when {
            inp.contains(staURI) -> {
                val inpPart = inp.split(staURI)
                ret = "sta-${inpPart[1]}"
            }
            inp.contains(udURI) -> {
                val inpPart = inp.split(udURI)
                ret = "ud-${inpPart[1]}"
                if (ret.contains("#")) {
                    val inpPart = ret.split("#")
                    ret = "${inpPart[0]}+${inpPart[1]}"
                }
            }
            inp.contains(scURI) -> {  // 새로운 조건문 추가
                val inpPart = inp.split(scURI)
                ret = "tsc-${inpPart[1]}"
            }
        }
        return ret
    }

    fun deShort(inp: String): String {
        var ret = ""
        when {
            inp.contains("sta-") -> {
                val inpPart = inp.split("sta-")
                ret = "${staURI}${inpPart[1]}"
            }
            inp.contains("ud-") -> {
                val inpPart = inp.split("ud-")
                ret = "${udURI}${inpPart[1]}"
                if (ret.contains("+")) {
                    val inpPart = ret.split("+")
                    ret = "${inpPart[0]}#${inpPart[1]}"
                }
            }
            inp.contains("tsc-") -> {  // 새로운 조건문 추가
                val inpPart = inp.split("tsc-")
                ret = "${scURI}${inpPart[1]}"
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
        //val startTime = System.currentTimeMillis()
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
        //val endTime = System.currentTimeMillis()
        //println("s:${endTime - startTime} ")
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

    fun bldgType(): ResultSet {
        val q = """
        SELECT DISTINCT ?type
        WHERE {
        ?s a ?type .
        FILTER(STRSTARTS(STR(?type), "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/building"))
        }
        """.trimIndent()
        logger.info("selectType => ${q}")
        val query = QueryFactory.create(q)
        val qexec = QueryExecutionFactory.create(query, ont)
        return qexec.execSelect()
    }
    
    fun bldgnameTypeToId(fromURI: String, typeURI: String): ResultSet {
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
        <$fromURI> ?p ?n .
        ?n rdf:type $typeURI .
        ?n skos:prefLabel ?label .
        }

        """.trimIndent()
        logger.info("selectType => ${q}")
        val query = QueryFactory.create(q)
        val qexec = QueryExecutionFactory.create(query, ont)
        return qexec.execSelect()
    }

    fun idToGML(gmlID: String): ResultSet {
        val q = """
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        PREFIX geo: <http://www.opengis.net/ont/geosparql#>
        PREFIX core: <https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/core#>
        PREFIX gen: <https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/generics#>

        SELECT ?asGML
        WHERE {
            ?a skos:prefLabel "$gmlID" .
            ?a geo:hasGeometry ?geomNode .
            ?geomNode geo:asGML ?asGML .
        }

        """.trimIndent()
        logger.info("selectType => ${q}")
        val query = QueryFactory.create(q)
        val qexec = QueryExecutionFactory.create(query, ont)
        
        return qexec.execSelect()
    }

    fun idToMeta(gmlID: String): ResultSet {
        val q = """
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        PREFIX geo: <http://www.opengis.net/ont/geosparql#>
        PREFIX core: <https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/core#>
        PREFIX gen: <https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/generics#>

        SELECT ?altLabel ?value
        WHERE {
            ?a skos:prefLabel "$gmlID" .
            ?a core:AbstractCityObject.abstractGenericAttribute ?relatedNode .
            ?relatedNode skos:altLabel ?altLabel .
            ?relatedNode gen:StringAttribute.value ?value .
        }
        """.trimIndent()
        logger.info("selectType => ${q}")
        val query = QueryFactory.create(q)
        val qexec = QueryExecutionFactory.create(query, ont)
        
        return qexec.execSelect()
    }

    fun idToBB(gmlID: String): ResultSet {
        val q = """
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        PREFIX iso19107_2207: <http://def.isotc211.org/iso19107/2003/CoordinateGeometry#>
        PREFIX iso19136: <http://def.isotc211.org/iso19136/2007/Feature#>
        PREFIX core: <https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/core#>

        SELECT ?lowerCorner ?upperCorner
        WHERE {
        ?s skos:prefLabel "$gmlID" .
        ?m core:CityModel.cityObjectMember ?s .
        ?m iso19136:AbstractFeature.boundedBy ?d .
        ?d iso19107_2207:GM_Envelope.lowerCorner ?lowerCorner .
        ?d iso19107_2207:GM_Envelope.upperCorner ?upperCorner .
        }
        """.trimIndent()
        logger.info("selectType => ${q}")
        val query = QueryFactory.create(q)
        val qexec = QueryExecutionFactory.create(query, ont)
        
        return qexec.execSelect()
    }

    //paper
    fun levelUpdate() {
        val queryString = """
        PREFIX tsc: <http://paper.9bon.org/ontologies/smartcity/0.2#>
        PREFIX sta: <http://paper.9bon.org/ontologies/sensorthings/1.1#>
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

        DELETE WHERE {
            ?area tsc:hasLevel ?oldLevel.
        };

        INSERT {
            ?area tsc:hasLevel ?level.
        } WHERE {
            ?city tsc:hasArea ?area.
            ?area tsc:hasSquareMeter ?sqm.
            ?area tsc:hasThing/sta:hasMultiDatastream/sta:hasObservation/sta:hasresult [
                sta:hasObservedProperty ?obsProp;
                sta:hasvalue ?count
            ].
            ?obsProp sta:hasname "Visit".

            BIND(xsd:decimal(?count) AS ?people)
            BIND(?people / ?sqm AS ?peoplePerSqM)

            OPTIONAL { ?level tsc:hasName "A" . FILTER(?peoplePerSqM <= 0.5) }
            OPTIONAL { ?level tsc:hasName "B" . FILTER(?peoplePerSqM > 0.5 && ?peoplePerSqM <= 0.7) }
            OPTIONAL { ?level tsc:hasName "C" . FILTER(?peoplePerSqM > 0.7 && ?peoplePerSqM <= 1.08) }
            OPTIONAL { ?level tsc:hasName "D" . FILTER(?peoplePerSqM > 1.08 && ?peoplePerSqM <= 1.39) }
            OPTIONAL { ?level tsc:hasName "E" . FILTER(?peoplePerSqM > 1.39 && ?peoplePerSqM <= 2) }
            OPTIONAL { ?level tsc:hasName "F" . FILTER(?peoplePerSqM > 2) }
        }
        """.trimIndent()
    
        val update = UpdateFactory.create(queryString)
        val dataset = DatasetFactory.create(ont)
        val updateProcessor = UpdateExecutionFactory.create(update, dataset)
    
        try {
            updateProcessor.execute()
            println("Update executed successfully")
        } catch (e: Exception) {
            println("Failed to execute update: $e")
        }
    }

    fun visitTest() {
        val baseUri = "http://paper.9bon.org/ontologies/smartcity/0.2#Area_000"
        val peopleCounts = (0..4).map { generateRandomPeople() }
        val queryStringUpdate = """
            PREFIX tsc: <http://paper.9bon.org/ontologies/smartcity/0.2#>
            PREFIX sta: <http://paper.9bon.org/ontologies/sensorthings/1.1#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
    
            DELETE {
                ?result sta:hasvalue ?oldValue.
            }
            INSERT {
                ?result sta:hasvalue ?newValue.
            } WHERE {
                VALUES (?area ?newValue) {
                    ${peopleCounts.mapIndexed { index, count -> "(<$baseUri$index> \"$count\")" }.joinToString("\n")}
                }
                ?area tsc:hasThing/sta:hasMultiDatastream/sta:hasObservation ?obs.
                ?obs sta:hasresult ?result.
                ?result sta:hasObservedProperty ?obsProp;
                         sta:hasvalue ?oldValue.
                ?obsProp sta:hasname "Visit".
            }
        """.trimIndent()
    
        val update = UpdateFactory.create(queryStringUpdate)
        val dataset = DatasetFactory.create(ont) // `ont` should be your ontology model
        val updateProcessor = UpdateExecutionFactory.create(update, dataset)
    
        try {
            updateProcessor.execute()
            logger.debug("Random people count updated successfully")
        } catch (e: Exception) {
            logger.debug("Failed to update random people count: $e")
        }
    }
    
    
    
    fun generateRandomPeople(): String {
        // 각 레벨에 해당하는 인원 수 범위 정의
        val ranges = listOf(
            1..4999,    // Level A
            5000..6999, // Level B
            7000..10799, // Level C
            10800..13899, // Level D
            13900..19999, // Level E
            20000..30000  // Level F, 최대값은 예시로 30000을 설정
        )
        val selectedRange = ranges.random()  // 리스트에서 무작위로 하나의 범위 선택
        return (selectedRange.random()).toString()  // 선택된 범위 내에서 무작위로 숫자 선택
    }
    
    
}