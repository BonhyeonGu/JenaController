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

import org.apache.jena.query.ResultSetFormatter

//트랜잭션 관련
import org.apache.jena.tdb2.TDB2Factory

class OntQuery(val ont:OntModel) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(OntQuery::class.java)
        val staURI = "http://paper.9bon.org/ontologies/sensorthings/1.1#"
        val udURI = "https://github.com/BonhyeonGu/resources/"
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

    // Debug

    fun debugUpdateVisitRand(): Long {
        val dataset = DatasetFactory.create(ont) // `ont` should be your ontology model
    
        // Step 1: Fetch all Observations
        val observations = fetchObservations(dataset)
    
        // Step 2: Generate random people counts for each Observation
        val peopleCounts = observations.map { generateRandomPeople() }
    
        val valuesClause = observations.zip(peopleCounts).joinToString("\n") { (obs, count) ->
            "(<${obs.second}> \"$count\"^^xsd:integer)"
        }
        
        val queryStringUpdate = """
            PREFIX tsc: <http://paper.9bon.org/ontologies/smartcity/0.2#>
            PREFIX sta: <http://paper.9bon.org/ontologies/sensorthings/1.1#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            DELETE {
                ?result sta:hasvalue ?oldValue.
            }
            INSERT {
                ?result sta:hasvalue ?newValue.
            }
            WHERE {
                VALUES (?obs ?newValue) {
                    $valuesClause
                }
                ?obs sta:hasresult ?result.
                ?result sta:hasObservedProperty ?obsProp;
                         sta:hasvalue ?oldValue.
                ?obsProp sta:hasname "Visit".
            }
        """.trimIndent()
    
    
        val update = UpdateFactory.create(queryStringUpdate)
        val updateProcessor = UpdateExecutionFactory.create(update, dataset)
    
        val startTime = System.currentTimeMillis()
        try {
            updateProcessor.execute()
            logger.debug("Random people count updated successfully")
        } catch (e: Exception) {
            logger.debug("Failed to update random people count: $e")
        }
        val endTime = System.currentTimeMillis()
        return endTime - startTime
    }


    fun debugUpdateTempRand(): Long {
        val dataset = DatasetFactory.create(ont) // `ont` should be your ontology model
    
        // Step 1: Fetch all Observations
        val observations = fetchObservations(dataset)
    
        // Step 2: Generate random people counts for each Observation
        val peopleCounts = observations.map { generateRandomTemp() }
    
        val valuesClause = observations.zip(peopleCounts).joinToString("\n") { (obs, count) ->
            "(<${obs.second}> \"$count\"^^xsd:double)"
        }
        
        val queryStringUpdate = """
            PREFIX tsc: <http://paper.9bon.org/ontologies/smartcity/0.2#>
            PREFIX sta: <http://paper.9bon.org/ontologies/sensorthings/1.1#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            DELETE {
                ?result sta:hasvalue ?oldValue.
            }
            INSERT {
                ?result sta:hasvalue ?newValue.
            }
            WHERE {
                VALUES (?obs ?newValue) {
                    $valuesClause
                }
                ?obs sta:hasresult ?result.
                ?result sta:hasObservedProperty ?obsProp;
                         sta:hasvalue ?oldValue.
                ?obsProp sta:hasname "Air Temperature".
            }
        """.trimIndent()
    
    
        val update = UpdateFactory.create(queryStringUpdate)
        val updateProcessor = UpdateExecutionFactory.create(update, dataset)
    
        val startTime = System.currentTimeMillis()
        try {
            updateProcessor.execute()
            logger.debug("Random people count updated successfully")
        } catch (e: Exception) {
            logger.debug("Failed to update random people count: $e")
        }
        val endTime = System.currentTimeMillis()
        return endTime - startTime
    }

    fun debugVisitUpdate_OLD() {
        val dataset = DatasetFactory.create(ont) // `ont` should be your ontology model
    
        // Step 1: Fetch all Areas
        val areas = countArea(dataset)
    
        // Step 2: Generate random people counts
        val peopleCounts = areas.map { generateRandomPeople() }
    
        // Step 3: Update SPARQL query
        val queryStringUpdate = """
            PREFIX tsc: <http://paper.9bon.org/ontologies/smartcity/0.2#>
            PREFIX sta: <http://paper.9bon.org/ontologies/sensorthings/1.1#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            DELETE {
                ?result sta:hasvalue ?oldValue.
            }
            INSERT {
                ?result sta:hasvalue ?newValue.
            }
            WHERE {
                VALUES (?area ?newValue) {
                    ${areas.zip(peopleCounts).joinToString("\n") { (area, count) -> "(<$area> \"$count\"^^xsd:integer)" }}
                }
                ?area tsc:hasThing/sta:hasMultiDatastream/sta:hasObservation ?obs.
                ?obs sta:hasresult ?result.
                ?result sta:hasObservedProperty ?obsProp;
                         sta:hasvalue ?oldValue.
                ?obsProp sta:hasname "Visit".
            }
        """.trimIndent()
    
        val update = UpdateFactory.create(queryStringUpdate)
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


    fun generateRandomTemp(): String {
        val ranges = listOf(
            -10.0..25.0
        )
        val selectedRange = ranges.random()
        val randomValue = Random.nextDouble(selectedRange.start, selectedRange.endInclusive)
        return String.format("%.2f", randomValue)
    }


    fun countArea(dataset: Dataset): List<String> {
        val fetchAreasQueryString = """
            PREFIX tsc: <http://paper.9bon.org/ontologies/smartcity/0.2#>
            
            SELECT ?area WHERE {
                ?area a tsc:Area.
            }
        """.trimIndent()
    
        val query = QueryFactory.create(fetchAreasQueryString)
        val qexec = QueryExecutionFactory.create(query, dataset)
    
        val areas = mutableListOf<String>()
        try {
            val results = qexec.execSelect()
            while (results.hasNext()) {
                val soln = results.nextSolution()
                areas.add(soln.getResource("area").uri)
            }
        } catch (e: Exception) {
            logger.debug("Failed to fetch areas: $e")
        } finally {
            qexec.close()
        }
        return areas
    }


    fun fetchObservations(dataset: Dataset): List<Pair<String, String>> {
        val fetchObservationsQueryString = """
            PREFIX tsc: <http://paper.9bon.org/ontologies/smartcity/0.2#>
            PREFIX sta: <http://paper.9bon.org/ontologies/sensorthings/1.1#>
            
            SELECT ?area ?obs WHERE {
                ?area a tsc:Area.
                ?area tsc:hasThing/sta:hasMultiDatastream/sta:hasObservation ?obs.
            }
        """.trimIndent()
    
        val query = QueryFactory.create(fetchObservationsQueryString)
        val qexec = QueryExecutionFactory.create(query, dataset)
    
        val observations = mutableListOf<Pair<String, String>>()
        try {
            val results = qexec.execSelect()
            while (results.hasNext()) {
                val soln = results.nextSolution()
                val area = soln.getResource("area").uri
                val obs = soln.getResource("obs").uri
                observations.add(area to obs)
            }
        } catch (e: Exception) {
            logger.debug("Failed to fetch observations: $e")
        } finally {
            qexec.close()
        }
        return observations
    }

    // Know

    fun updateLevel0(): Long {
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
                {
                    SELECT ?area (MAX(?resultTime) AS ?latestTime)
                    WHERE {
                        ?area tsc:hasThing/sta:hasMultiDatastream/sta:hasObservation ?observation.
                        ?observation sta:hasresultTime ?resultTime.
                    }
                    GROUP BY ?area
                }
                ?area tsc:hasThing/sta:hasMultiDatastream/sta:hasObservation ?latestObservation.
                ?latestObservation sta:hasresultTime ?latestTime;
                                sta:hasresult [
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
    
        //try {
        //    updateProcessor.execute()
        //    println("Update executed successfully")
        //} catch (e: Exception) {
        //    println("Failed to execute update: $e")
        //}
        val startTime = System.currentTimeMillis()
        updateProcessor.execute()
        val endTime = System.currentTimeMillis()
        return endTime - startTime
    }


    fun updateLevel1(): Long {
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
                ?area tsc:hasThing/sta:hasMultiDatastream/sta:hasIndexpoint [
                    sta:pointToresult ?resultResource;
                    sta:pointToMultiObservedProperty [
                        sta:hasname "Visit"
                    ]
                ].
                ?resultResource sta:hasvalue ?resultValue;
                                sta:isresultByObservation [
                                    sta:hasresultTime ?resultTime
                                ].
                {
                    SELECT ?area (MAX(?resultTime) AS ?latestTime)
                    WHERE {
                        ?area tsc:hasThing/sta:hasMultiDatastream/sta:hasIndexpoint [
                            sta:pointToresult/sta:isresultByObservation [
                                sta:hasresultTime ?resultTime
                            ]
                        ].
                    }
                    GROUP BY ?area
                }
                FILTER(?resultTime = ?latestTime)
                BIND(xsd:decimal(?resultValue) AS ?people)
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
    
        val startTime = System.currentTimeMillis()
        updateProcessor.execute()
        val endTime = System.currentTimeMillis()
        return endTime - startTime
    }


    fun updateLevel2_OLD() {
        val queryString = """
        PREFIX tsc: <http://paper.9bon.org/ontologies/smartcity/0.2#>
        PREFIX sta: <http://paper.9bon.org/ontologies/sensorthings/1.1#>
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
        
        # First, delete existing levels
        DELETE WHERE {
            ?area tsc:hasLevel ?oldLevel.
        };
        
        # Then, insert new levels based on updated calculations
        INSERT {
            ?area tsc:hasLevel ?level.
        }
        WHERE {
            # Find the unique node for the "Visit" ObservedProperty
            ?visitProp a sta:ObservedProperty ;
                       sta:hasname "Visit" .
        
            # Traverse to the associated values
            ?visitProp sta:isObservedPropertyByresult ?resultResource .
            ?resultResource sta:hasvalue ?resultValue .
            
            # Find associated areas
            ?area tsc:hasThing/sta:hasMultiDatastream/sta:hasIndexpoint/sta:pointToresult ?resultResource ;
                  tsc:hasSquareMeter ?sqm .
            
            BIND(xsd:decimal(?resultValue) AS ?people)
            BIND(?people / ?sqm AS ?peoplePerSqM)
        
            # Define levels based on peoplePerSqM
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
        updateProcessor.execute()
    }

    
    fun selectTempMax0(): List<String> {
        val queryString = """
            PREFIX tsc: <http://paper.9bon.org/ontologies/smartcity/0.2#>
            PREFIX sta: <http://paper.9bon.org/ontologies/sensorthings/1.1#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            SELECT ?areaName ?resultTime ?maxTemperature
            WHERE {
                {
                    SELECT ?area ?resultTime (MAX(?temperature) AS ?maxTemperature)
                    WHERE {
                        ?area tsc:hasThing/sta:hasMultiDatastream/sta:hasObservation ?observation.
                        ?observation sta:hasresultTime ?resultTime.
                        ?observation sta:hasresult [
                            sta:hasObservedProperty ?obsProp;
                            sta:hasvalue ?temperature
                        ].
                        ?obsProp sta:hasname "Air Temperature".
                    }
                    GROUP BY ?area ?resultTime
                    ORDER BY DESC(?maxTemperature)
                    LIMIT 1
                }
                ?area tsc:hasName ?areaName.
            }
        """.trimIndent()
        
        // SPARQL 쿼리 생성
        val query = QueryFactory.create(queryString)
        
        // 쿼리 실행
        val qexec = QueryExecutionFactory.create(query, ont)
        
        val startTime = System.currentTimeMillis()
        val resultSet = qexec.execSelect()
        val endTime = System.currentTimeMillis()
    
        // 파싱
        var resultList: List<String> = emptyList()
        if (resultSet.hasNext()) {
            val qs = resultSet.nextSolution()
            resultList = listOf(
                (endTime - startTime).toString(), // 소모시간
                qs.getLiteral("areaName").string,
                qs.getLiteral("resultTime").string,
                qs.getLiteral("maxTemperature").string
            )
        }
        
        qexec.close()
        return resultList
    }


    fun selectTempMax1(): List<String> {
        val queryString = """
            PREFIX tsc: <http://paper.9bon.org/ontologies/smartcity/0.2#>
            PREFIX sta: <http://paper.9bon.org/ontologies/sensorthings/1.1#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            SELECT ?areaName ?resultTime ?temperature
            WHERE {
                ?area tsc:hasName ?areaName .
                ?area tsc:hasThing/sta:hasMultiDatastream/sta:hasIndexpoint [
                    sta:pointToresult ?resultResource;
                    sta:pointToMultiObservedProperty [
                        sta:hasname "Air Temperature"
                    ]
                ].
                ?resultResource sta:hasvalue ?temperature;
                                sta:isresultByObservation [
                                    sta:hasresultTime ?resultTime
                                ].
            }
            ORDER BY DESC(?temperature)
            LIMIT 1
        """.trimIndent()
        
        // SPARQL 쿼리 생성
        val query = QueryFactory.create(queryString)
        
        // 쿼리 실행
        val qexec = QueryExecutionFactory.create(query, ont)
        
        val startTime = System.currentTimeMillis()
        val resultSet = qexec.execSelect()
        val endTime = System.currentTimeMillis()
    
        // 파싱
        var resultList: List<String> = emptyList()
        if (resultSet.hasNext()) {
            val qs = resultSet.nextSolution()
            resultList = listOf(
                (endTime - startTime).toString(), // 소모시간
                qs.getLiteral("areaName").string,
                qs.getLiteral("resultTime").string,
                qs.getLiteral("temperature").string
            )
        }
        
        qexec.close()
        return resultList
    }
}