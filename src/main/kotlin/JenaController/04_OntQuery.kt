package JenaController
//--------------------------------------------------------------------
import org.slf4j.Logger
import org.slf4j.LoggerFactory
//--------------------------------------------------------------------
import org.apache.jena.ontology.OntModel
import org.apache.jena.query.Query
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.ResultSet
//--------------------------------------------------------------------
import org.apache.jena.query.Dataset
import org.apache.jena.query.DatasetFactory
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.apache.jena.rdf.model.ModelFactory
//--------------------------------------------------------------------
import org.apache.jena.query.ResultSetFormatter
//--------------------------------------------------------------------
import org.apache.jena.query.QueryExecution
import org.apache.jena.query.ARQ
import org.apache.jena.sparql.util.Context
import org.apache.jena.update.UpdateRequest

//--------------------------------------------------------------------
import org.json.JSONObject
import java.io.File
//--------------------------------------------------------------------
import kotlin.random.Random
import kotlin.booleanArrayOf

class OntQuery(val ont: OntModel, val cache: Boolean) {
    private val queries: MutableMap<String, String> = mutableMapOf()

    init {
        reloadQuery()
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(OntQuery::class.java)
        val staURI = "http://paper.9bon.org/ontologies/sensorthings/1.1.3#"
        val udURI = "https://github.com/BonhyeonGu/resources/"
        val scURI = "http://paper.9bon.org/ontologies/smartcity/0.2#"  // 새로운 URI
        val gitURI = "https://github.com/BonhyeonGu/STA_Plugin/"
        val LOCALE_JSON_QUERIES = "./_Queries"
    }
    
    fun reloadQuery() {
        val folder = File(LOCALE_JSON_QUERIES)
        if (folder.exists() && folder.isDirectory) {
            val files = folder.listFiles()

            files?.forEach { file ->
                if (file.isFile) {
                    val content = file.readText()
                    queries[file.name.split(".")[0]] = content
                    logger.info("Query Name: ${file.name.split(".")[0]}")
                }
            }

        } else {
            logger.error("Can't found Qeuries")
        }
        logger.info("")
    }


    fun enShort(inp: String): String {
        return when {
            inp.contains(staURI) -> inp.replace(staURI, "sta-").replace("/", "$$")
            inp.contains(udURI) -> inp.replace(udURI, "ud-").replace("/", "$$").replace("#", "~~")
            inp.contains(scURI) -> inp.replace(scURI, "tsc-").replace("/", "$$")
            inp.contains(gitURI) -> inp.replace(gitURI, "git-").replace("/", "$$").replace("#", "~~")
            else -> inp
        }
    }
    
    fun deShort(inp: String): String {
        return when {
            inp.startsWith("sta-") -> inp.replace("sta-", staURI).replace("$$", "/")
            inp.startsWith("ud-") -> inp.replace("ud-", udURI).replace("$$", "/").replace("~~", "#")
            inp.startsWith("tsc-") -> inp.replace("tsc-", scURI).replace("$$", "/")
            inp.startsWith("git-") -> inp.replace("git-", gitURI).replace("$$", "/").replace("~~", "#")
            else -> inp
        }
    }


    fun createUpdateExecution(updateRequest: UpdateRequest, dataset: Dataset): UpdateProcessor {
        val context = Context()
        context.set(ARQ.symLogExec, true)  // 질의 실행 로깅 활성화
        context.set(ARQ.enableExecutionTimeLogging, true)  // 실행 시간 로깅 활성화
        context.set(ARQ.optFilterPlacement, false)  // 필터 배치 최적화 비활성화
        context.set(ARQ.optimization, false)  // 전반적인 최적화 비활성화
        context.set(ARQ.queryTimeout, 0)  // 질의 타임아웃 설정 (0은 무제한)

        return UpdateExecutionFactory.create(updateRequest, dataset, context)
    }

    fun browseQuery(q: String): List<Array<String>> {
        logger.info("Browse => ${q}")

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
        logger.info(ResultSetFormatter.asText(results))
        
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

//=============================================================================================

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

//=============================================================================================

    // Debug Tools

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

    fun TESTfetchObservations(dataset: Dataset): List<Pair<String, String>> {
        val fetchObservationsQueryString = """
            PREFIX tsc: <http://paper.9bon.org/ontologies/smartcity/0.2#>
            PREFIX sta: <http://paper.9bon.org/ontologies/sensorthings/1.1#>
            
            SELECT ?subject ?obs WHERE {
                ?subject sta:hasThing ?thing.
                ?thing sta:hasMultiDatastream/sta:hasObservation ?obs.
            }
        """.trimIndent()
    
        val query = QueryFactory.create(fetchObservationsQueryString)
        val qexec = QueryExecutionFactory.create(query, dataset)
    
        val observations = mutableListOf<Pair<String, String>>()
        try {
            val results = qexec.execSelect()
            while (results.hasNext()) {
                val soln = results.nextSolution()
                val subject = soln.getResource("subject").uri
                val obs = soln.getResource("obs").uri
                observations.add(subject to obs)
            }
        } catch (e: Exception) {
            logger.debug("Failed to fetch observations: $e")
        } finally {
            qexec.close()
        }
        return observations
    }


    fun generateRandom(pName: String): String {
        //logger.info("generateRandom")
        var ret = ""
        when (pName) {
            "air_temperature", "temperature" -> {
                val range = -10.0..25.0
                val randomValue = Random.nextDouble(range.start, range.endInclusive)
                ret = String.format("%.2f", randomValue)
            }
            "pm10", "pm25", "dustLevel", "fineDustLevel", "veryFineDustLevel" -> {
                val range = 10.0..80.0
                val randomValue = Random.nextDouble(range.start, range.endInclusive)
                ret = String.format("%.2f", randomValue)
            }
            "humidity" -> {
                val range = 40.0..80.0
                val randomValue = Random.nextDouble(range.start, range.endInclusive)
                ret = String.format("%.2f", randomValue)
            }
            "voc" -> {
                val ranges = listOf(
                    0.001..0.002, // Formaldehyde safe
                    0.003..0.75, // Formaldehyde warring
                    0.75..0.8 // Formaldehyde danger
                )
                val selectedRange = ranges.random()
                val randomValue = Random.nextDouble(selectedRange.start, selectedRange.endInclusive)    
                ret = String.format("%.3f", randomValue)
            }
            "noise" -> {
                val range = 0.0..120.0
                val randomValue = Random.nextDouble(range.start, range.endInclusive)
                ret = String.format("%.2f", randomValue)
            }
            "iluminance" -> {
                val ranges = listOf(
                    0.001..1.00,    // 어두운밤
                    10.0..20.0, // 도시평범한밤
                    10000.0..100000.0, // 맑은날
                    1000.0..20000.0  // 흐린날
                )
                val selectedRange = ranges.random()
                val randomValue = Random.nextDouble(selectedRange.start, selectedRange.endInclusive)    
                ret = String.format("%.3f", randomValue)
            }
            "traffic_volume", "visitor", "revisitor" -> {
                val ranges = listOf(
                    0..3077,    // Level A
                    3077..4310, // Level B
                    4310..7194, // Level C
                    7194..10753, // Level D
                    10753..21739, // Level E
                    21739..30000  // Level F, 최대값은 예시로 30000을 설정
                )
                val selectedRange = ranges.random()  // 리스트에서 무작위로 하나의 범위 선택
                ret = (selectedRange.random()).toString()  // 선택된 범위 내에서 무작위로 숫자 선택
            }
            
        }
        return ret
    }

    // Debug UPDATE


    fun debugUpdateRand(pName: String): Long {
        logger.info("Q:debugUpdateRand : " + pName)
        val dataset = DatasetFactory.create(ont) // `ont` should be your ontology model
        val observations = fetchObservations(dataset)
        val peopleCounts = observations.map { generateRandom(pName) }
        var valuesClause = ""
        var propertyFullName = ""
        when (pName) {
            "air_temperature", "pm10", "pm25", "humidity", "voc", "noise", "iluminance"  -> {
                valuesClause = observations.zip(peopleCounts).joinToString("\n") { (obs, count) ->
                    "(<${obs.second}> \"$count\"^^xsd:double)"
                }
                propertyFullName = pName
            }

            "traffic_volume", "visitor", "revisitor" -> {
                valuesClause = observations.zip(peopleCounts).joinToString("\n") { (obs, count) ->
                    "(<${obs.second}> \"$count\"^^xsd:integer)"
                }
                propertyFullName = pName
            }

            else -> { logger.info("Q:debugUpdateRand !!! ERROR !!!")
            }
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
                    ${valuesClause}
                }
                ?obs sta:hasresult ?result.
                ?result sta:hasObservedProperty ?obsProp;
                        sta:hasvalue ?oldValue.
                ?obsProp sta:hasname "${propertyFullName}"^^xsd:string.
            }
        """.trimIndent()

        val update = UpdateFactory.create(queryStringUpdate)
        val updateProcessor = UpdateExecutionFactory.create(update, dataset)
        val startTime = System.currentTimeMillis()
        try {
            updateProcessor.execute()
            logger.info("Random Updated successfully")
        } catch (e: Exception) {
            logger.info("Failed to update random people count: $e")
        }
        val endTime = System.currentTimeMillis()
        logger.info("END")
        return endTime - startTime
    }


    fun TESTdebugUpdateRand() {
        val propNames = listOf("temperature", "humidity", "dustLevel", "fineDustLevel", "veryFineDustLevel")
        for (pName in propNames) {
            logger.info("Q:TESTdebugUpdateRand : " + pName)
            val dataset = DatasetFactory.create(ont) // `ont` should be your ontology model
            val observations = TESTfetchObservations(dataset)
            val peopleCounts = observations.map { generateRandom(pName) }
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
                        ${valuesClause}
                    }
                    ?obs sta:hasresult ?result.
                    ?result sta:hasObservedProperty ?obsProp;
                            sta:hasvalue ?oldValue.
                    ?obsProp sta:hasname "${pName}"^^xsd:string.
                }
            """.trimIndent()

            val update = UpdateFactory.create(queryStringUpdate)
            val updateProcessor = UpdateExecutionFactory.create(update, dataset)
            val startTime = System.currentTimeMillis()
            try {
                updateProcessor.execute()
                logger.info("Random Updated successfully")
            } catch (e: Exception) {
                logger.info("Failed to update random people count: $e")
            }
            val endTime = System.currentTimeMillis()
            logger.info("End: ${endTime-startTime}")
        }
        return
    }

//=============================================================================================
    
    // Know

    fun qUpdate(qName: String): Long {
        val queryString = queries[qName]?.trimIndent() ?: return -1.0.toLong()
        val update = UpdateFactory.create(queryString)
        
        val dataset = DatasetFactory.create(ont)
        val updateProcessor = createUpdateExecution(update, dataset)

        val startTime = System.currentTimeMillis()  
        updateProcessor.execute()
        val endTime = System.currentTimeMillis()
        
        return endTime - startTime
    }
    
    
    fun qSelectOne(qName: String): List<String> {
        val queryString = queries[qName]?.trimIndent() ?: return emptyList()

        val query = QueryFactory.create(queryString)
        val qexec = QueryExecutionFactory.create(query, ont)

        val startTime = System.currentTimeMillis()
        val resultSet = qexec.execSelect()
        val endTime = System.currentTimeMillis()
    
        var resultList: List<String> = emptyList()
        if (resultSet.hasNext()) {
            val qs = resultSet.nextSolution()
            if (qName == "selectTempMax0" || qName == "selectTempMax1") {
                val area = qs.getResource("area")?.uri ?: "Unknown"
                val areaName = qs.getLiteral("areaName")?.string ?: "Unknown"
                val resultTime = qs.getLiteral("resultTime")?.string ?: "Unknown"
                val temperature = qs.getLiteral("temperature")?.string ?: "Unknown"
                resultList = listOf(
                    (endTime - startTime).toString(),
                    area,
                    areaName,
                    resultTime,
                    temperature
                )
            }
            else if (qName == "selectPMAvgMax0" || qName == "selectPMAvgMax1") {
                val area = qs.getResource("area")?.uri ?: "Unknown"
                val areaName = qs.getLiteral("areaName")?.string ?: "Unknown"
                val latestResultTime = qs.getLiteral("latestResultTime")?.string ?: "Unknown"
                val latestTemperature = qs.getLiteral("latestPM")?.string ?: "Unknown"
                val avgPM = qs.getLiteral("avgPM")?.string ?: "Unknown"

                resultList = listOf(
                    (endTime - startTime).toString(),
                    area,
                    areaName,
                    latestResultTime,
                    latestTemperature,
                    avgPM
                )
            }
            else if (qName == "selectLLToLight0" || qName == "selectLLToLight1") {
                val area = qs.getResource("area")?.uri ?: "Unknown"
                val areaName = qs.getLiteral("areaName")?.string ?: "Unknown"
                val avgTraffic = qs.getLiteral("avgTraffic")?.string ?: "Unknown"
                val avgIlluminance = qs.getLiteral("avgIlluminance")?.string ?: "Unknown"
                val ratio = qs.getLiteral("ratio")?.string ?: "Unknown"

                resultList = listOf(
                    (endTime - startTime).toString(),
                    area,
                    areaName,
                    avgTraffic,
                    avgIlluminance,
                    ratio
                )
            }
        }

        qexec.close()
        return resultList
    }


    fun qSelectMany(qName: String): MutableList<List<String>>  {
        val queryString = queries[qName]?.trimIndent() ?: return mutableListOf()
    
        val query = QueryFactory.create(queryString)
        val qexec = QueryExecutionFactory.create(query, ont)
    
        val startTime = System.currentTimeMillis()
        val resultSet = qexec.execSelect()
        val endTime = System.currentTimeMillis()
    
        val executionTime = (endTime - startTime).toString()
        val resultList: MutableList<List<String>> = mutableListOf()
    
        if (qName == "selectLevel") {
            while (resultSet.hasNext()) {
                val qs = resultSet.nextSolution()
    
                val level = qs.getResource("level")?.uri ?: "Unknown"
                val area = qs.getResource("area")?.uri ?: "Unknown"
                val areaName = qs.getLiteral("areaName")?.string ?: "Unknown"
    
                resultList.add(
                    listOf(
                        executionTime,
                        level,
                        area,
                        areaName
                    )
                )
            }
        }

        else if (qName == "selectRoomQ") {
            while (resultSet.hasNext()) {
                val qs = resultSet.nextSolution()

                
                val buildingPart = qs.getResource("buildingPart")?.uri ?: "Unknown" // 방의 URI
                val buildingPartLabel = qs.getLiteral("buildingPartLabel")?.string ?: "Unknown"
                val roomUri = qs.getResource("room")?.uri ?: "Unknown" // 방의 URI
                val roomLabel = qs.getLiteral("roomLabel")?.string ?: "Unknown"
                val roomGeo = qs.getLiteral("asGML")?.string ?: "Unknown"
                val qualityIndex = qs.getLiteral("Q")?.double ?: Double.NaN // 계산된 Q 값
                resultList.add(
                    listOf(
                        executionTime,
                        buildingPart,
                        buildingPartLabel,
                        roomUri,
                        roomLabel,
                        roomGeo,
                        qualityIndex.toString()
                    )
                )
            }
        }
    
        qexec.close()
        return resultList
    }

    
    fun TESTqSelectOne(q0: String, q1: String, q2: String): List<String> {
        val queryString = when (q0) {
            "realtime" -> queries["_TEST_realTime"]?.trimIndent()
            "all" -> queries["_TEST_all"]?.trimIndent()
            else -> return emptyList()
        } ?: return emptyList()
    
        val modifiedQueryString = queryString
            .replace("{{SORT}}", q1)
            .replace("{{PROP}}", q2)

        val query = QueryFactory.create(modifiedQueryString)
        val qexec = QueryExecutionFactory.create(query, ont)

        val startTime = System.currentTimeMillis()
        val resultSet = qexec.execSelect()
        val endTime = System.currentTimeMillis()
    
        var resultList: List<String> = emptyList()
        if (resultSet.hasNext()) {
            val qs = resultSet.nextSolution()
            val id = qs.getResource("a")?.uri ?: "Unknown"
            val value = when (q0) {
                "realtime" -> qs.getLiteral("aggregateValue")?.string ?: "Unknown"
                "all" -> qs.getLiteral("averageValue")?.string ?: "Unknown"
                else -> "?"
            }
            val resultTime = when (q0) {
                "realtime" -> qs.getLiteral("latestResultTime")?.string ?: "Unknown"
                "all" -> "?"
                else -> "?"
            }
        
            resultList = listOf(
                (endTime - startTime).toString(),
                id,
                value,
                resultTime
            )
        }

        qexec.close()
        return resultList
    }
    
    fun deleteObservation(n: Int): List<String> {
        val dataset = DatasetFactory.create(ont)
        val queryString = queries["deleteObservation"]?.trimIndent() ?: return emptyList()
        val modifiedQueryString = queryString.replace("{{n}}", n.toString())
        print(modifiedQueryString)
        // 🔹 UpdateRequest로 변환
        val updateRequest: UpdateRequest = UpdateFactory.create(modifiedQueryString)
        
        // 🔹 UpdateExecution을 생성
        val qexec = UpdateExecutionFactory.create(updateRequest, dataset)
    
        val startTime = System.currentTimeMillis()
        
        // 🔹 DELETE는 execSelect()가 아니라 execute()로 실행해야 함
        qexec.execute()
        
        val endTime = System.currentTimeMillis()
        // 실행 시간을 반환
        return listOf((endTime - startTime).toString())
    }
/*
    // UPDATE SUB_Graph
    fun insertSubgraphIntoOntology(filePath: String) {
        logger.info("Starting to insert subgraph from: $filePath")

        val dataset = DatasetFactory.create(ont)
        // 서브그래프 파일 불러오기
        val subgraphModel = ModelFactory.createDefaultModel()
        FileManager.get().readModel(subgraphModel, filePath)
        
        // 서브그래프를 메인 온톨로지에 추가
        dataset.defaultModel.add(subgraphModel)
        
        logger.info("Subgraph from $filePath successfully inserted into the ontology.")
    }
        */
}