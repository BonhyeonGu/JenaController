package JenaController
//--------------------------------------------------------------------
import org.slf4j.Logger
import org.slf4j.LoggerFactory
//--------------------------------------------------------------------
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody // 문자열을 렌더링 없이 간단한 방법으로 출력

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController // JSON&XML
//--------------------------------------------------------------------
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.ResultSet
import org.apache.jena.query.ResultSetFormatter
import org.apache.jena.ontology.OntModelSpec // RULE
//--------------------------------------------------------------------
import JenaController.Ontology
import JenaController.Validate
import JenaController.OntQuery
//--------------------------------------------------------------------
import java.time.LocalDateTime // 시간
import java.time.format.DateTimeFormatter
import java.io.FileOutputStream // 파일 입출력
import org.json.JSONObject // JSON 객체
import java.io.ByteArrayOutputStream // JSON 변환
//--------------------------------------------------------------------
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
//--------------------------------------------------------------------


@Controller
class WebController : AutoCloseable {
    companion object {
        val RULE = OntModelSpec.OWL_MEM_TRANS_INF

        private val logger: Logger = LoggerFactory.getLogger(WebController::class.java)
        val ont = Ontology(rule = RULE).ontologyModel
        val ontQ = OntQuery(ont, cache = false)
        //-----------------------------------------------------------------------------
        const val TRACE_TIME_SWITCH = true
        val TRACE_FILE_NAME = "./" + "Trace.txt"
        val COMPLETE_FILE_NAME = "./" + "Complete.txt"
        //-----------------------------------------------------------------------------
    }

    private var bufferedWriter: BufferedWriter? = null

    init {
        if (TRACE_TIME_SWITCH) {
            File("./RUNNING.txt").createNewFile()
            val file = File(TRACE_FILE_NAME)
            if (!file.exists()) {
                file.createNewFile()
                println("New test start\n")
            }
            val fileWriter = FileWriter(file, true)
            bufferedWriter = BufferedWriter(fileWriter)
        }
    }

    fun traceWrite(message: String) {
        bufferedWriter?.let {
            it.write("$message\n")
            it.flush()
        }
    }

    fun setFilePerTo777(filePath: String) {
        val path = Paths.get(filePath)
        val permissions = PosixFilePermissions.fromString("rwxrwxrwx")
        Files.setPosixFilePermissions(path, permissions)
    }

    fun copyAndClearTraceFile() {
        val sFile = File(TRACE_FILE_NAME)
        val dFile = File(COMPLETE_FILE_NAME)
        sFile.copyTo(dFile, overwrite = true)
        FileWriter(sFile, false).close()
        setFilePerTo777(dFile.absolutePath)
    }

    override fun close() {
        bufferedWriter?.close()
    }

    fun modifyJsonResults(jsonResults: String, replacementMap: MutableMap<String, String>): String {
        val jsonObject = JSONObject(jsonResults)
        val resultsArray = jsonObject.getJSONObject("results").getJSONArray("bindings")
    
        for (i in 0 until resultsArray.length()) {
            val typeObj = resultsArray.getJSONObject(i).getJSONObject("type")
            val originalValue = typeObj.getString("value")
    
            // 문자열 교체
            val modifiedValue = replacementMap.entries.fold(originalValue) { acc, entry ->
                acc.replace(entry.key, entry.value)
            }
    
            // 새로운 "valueShort" 키 추가
            typeObj.put("valueShort", modifiedValue)
        }
    
        return jsonObject.toString()
    }



    @GetMapping("/")
    fun index(model: Model): String {
        logger.debug("User Request /")
        model.addAttribute("message", "Index")
        return "index"
    }

    //Validation
    @GetMapping("/test")
    fun test(model: Model): String {
        logger.debug("User Request /test")
        val vali = Validate(ont)
        //jenaValidate.validationTest_OWL("https://paper.9bon.org/ontologies/sensorthings/1.1")
        vali.validationTest_OWLandRDF()
        model.addAttribute("message", "Finish : test")
        return "index"
    }

    //http://localhost:8080/browse/sta-EL_SMARTPOLE_MEDIAPOLE-W_1(scanner)+Thing_00
    @GetMapping("/browse/{resource}")
    fun browseResource(@PathVariable resource: String, model: Model): String {
        logger.debug("User Request /browse/$resource")

        var startTime: Long = 0
        var endTime: Long = 0
        var executionTime: Long = 0

        val resourceURI = ontQ.deShort(resource)
        model.addAttribute("resourceURI", resourceURI)
        if (ontQ.isProperty(resourceURI)) {
            startTime = System.currentTimeMillis()
            val q = """ 
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        
                SELECT ?property ?value WHERE {
                    {
                        <$resourceURI> rdfs:domain ?domainClass.
                        ?domainClass owl:unionOf ?classList.
                        ?classList rdf:rest*/rdf:first ?value.
                        BIND (rdfs:domain AS ?property)
                    }
                    UNION
                    {
                        <$resourceURI> rdfs:range ?value.
                        BIND (rdfs:range AS ?property)
                    }
                    UNION
                    {
                        <$resourceURI> owl:restriction ?value.
                        BIND (owl:restriction AS ?property)
                    }
                }
            """.trimIndent()
            val propertyDetails = ontQ.browseQuery(q)
            endTime = System.currentTimeMillis()
            executionTime = endTime - startTime
            model.addAttribute("executionTime", executionTime)
            model.addAttribute("propertyDetails", propertyDetails)
            return "browseProperty"
        } else {
            startTime = System.currentTimeMillis()
            var q = """
                SELECT ?property ?value WHERE {
                    <$resourceURI> ?property ?value.
                }   
            """.trimIndent()
            var resourceInfo = ontQ.browseQuery(q)
            endTime = System.currentTimeMillis()
            val executionTime0 = endTime - startTime
            model.addAttribute("executionTime0", executionTime0)
            model.addAttribute("resourceInfo", resourceInfo)

            startTime = System.currentTimeMillis()
            q = """
                SELECT ?property ?value WHERE {
                    ?value ?property <$resourceURI>.
                }
            """.trimIndent()
            
            resourceInfo = ontQ.browseQuery(q)
            endTime = System.currentTimeMillis()
            val executionTime1 = endTime - startTime
            model.addAttribute("executionTime1", executionTime1)
            model.addAttribute("resourceInfoReverse", resourceInfo)

            return "browse"
        }
    }

    @GetMapping("/reloadQuery")
    fun reloadQuery(model: Model): String {
        logger.info("User Request /reloadQuery")
        ontQ.reloadQuery()
        model.addAttribute("message", "Finish : reloadQuery")
        return "index"
    }
    

    @GetMapping("/queryForm")
    fun showQueryForm(): String {
        return "queryForm"
    }

    @PostMapping("/executeQuery")
    fun executeQuery(@RequestParam sparqlQuery: String, model: Model): String {
        val resultsList = ontQ.executeSPARQL(sparqlQuery)
        model.addAttribute("results", resultsList)
        return "queryResults"
    }


    @GetMapping("/save")
    fun save(model: Model): String {
        val startTime = System.currentTimeMillis()
        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
        val filename = "./" + currentDateTime.format(formatter) + ".rdf"
        FileOutputStream(filename).use { outStream ->
            ont.write(outStream, "RDF/XML")
        }
        val endTime = System.currentTimeMillis()
        val executionTime = endTime - startTime

        if (TRACE_TIME_SWITCH) {
            traceWrite("save: $executionTime")
        }

        model.addAttribute("message", "Execution time: $executionTime ms")
        return "index"
    }
//=====================================================================================================

    @GetMapping("/traceComplite")
    fun traceComplites(model: Model): String {
        copyAndClearTraceFile()
        model.addAttribute("message", "traceComplite")
        return "index"
    }

//===========================================

    @GetMapping("/select/id/{bldgname}/{type}")
    @ResponseBody
    fun getId(@PathVariable bldgname: String, @PathVariable type: String): String {
        val fullBldgName = ontQ.deShort(bldgname)
        val resultsList = ontQ.bldgnameTypeToId("$fullBldgName", type)
        
        // ByteArrayOutputStream을 사용하여 결과를 JSON 형식으로 변환
        val outputStream = ByteArrayOutputStream()
        ResultSetFormatter.outputAsJSON(outputStream, resultsList)
        val jsonResults = outputStream.toString("UTF-8")
    
        return jsonResults
    }

    @GetMapping("/select/meta/{gmlID}")
    @ResponseBody
    fun getMeta(@PathVariable gmlID: String, model: Model): String {
        val resultsList = ontQ.idToMeta(gmlID)
        
        // ByteArrayOutputStream을 사용하여 결과를 JSON 형식으로 변환
        val outputStream = ByteArrayOutputStream()
        ResultSetFormatter.outputAsJSON(outputStream, resultsList)
        val jsonResults = outputStream.toString("UTF-8")
    
        return jsonResults
    }

//=====================================================================================================
    
    //Get Part of GML
    @GetMapping("/select/gml/{gmlID}", produces = ["application/xml"])
    @ResponseBody
    fun getGmlXML(@PathVariable gmlID: String, model: Model): String {
        val resultsList = ontQ.idToGML(gmlID)
        
        // 결과를 XML 형식으로 변환
        val xmlStringBuilder = StringBuilder()
        while (resultsList.hasNext()) {
            val querySolution = resultsList.next()
            val gmlValue = querySolution.get("asGML").asLiteral().string
            xmlStringBuilder.append(gmlValue)
        }
    
        return xmlStringBuilder.toString()
    }

    //Get BB, Doesn't think about tree structure, lower x, y, z, upper x, y, z
    @GetMapping("/select/bb/{gmlID}", produces = ["text/plain"])
    @ResponseBody
    fun getBB(@PathVariable gmlID: String): String {
        val resultsList = ontQ.idToBB(gmlID) // 함수 이름을 idToBB로 변경했습니다.
    
        // 결과를 단일 문자열로 변환
        val resultStringBuilder = StringBuilder()
        while (resultsList.hasNext()) {
            val querySolution = resultsList.next()
            val lowerCorner = querySolution.get("lowerCorner").asLiteral().string
            val upperCorner = querySolution.get("upperCorner").asLiteral().string
    
            // lowerCorner와 upperCorner 값을 공백으로 구분하여 추가
            if (resultStringBuilder.isNotEmpty()) {
                resultStringBuilder.append(" ")
            }
            resultStringBuilder.append("$lowerCorner $upperCorner")
        }
    
        return resultStringBuilder.toString()
    }
    

//=====================================================================================================

    @GetMapping("/select/bldgtype")
    @ResponseBody
    fun getType(): String {
        val resultsList = ontQ.bldgType()

        // ByteArrayOutputStream을 사용하여 결과를 JSON 형식으로 변환
        val outputStream = ByteArrayOutputStream()
        ResultSetFormatter.outputAsJSON(outputStream, resultsList)
        val jsonResults = outputStream.toString("UTF-8")
    
        // 교체 매핑 정의
        val replacementMap = mutableMapOf(
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/building#" to "bldg:"
        )
    
        // JSON 결과 수정
        val modifiedJsonResults = modifyJsonResults(jsonResults, replacementMap)
    
        return modifiedJsonResults
    }


//=====================================================================================================

    @GetMapping("/category/{qName}")
    @ResponseBody
    fun category(@PathVariable qName: String, model: Model): Map<String, Any> {
        logger.info("User Request /category/$qName")
        if (qName == "updateLevel0" || qName == "updateLevel1" || qName == "addPro" || qName == "test") {
            val executionTime = ontQ.qUpdate(qName)
            if (TRACE_TIME_SWITCH) {
                traceWrite("${qName}: ${executionTime}")
            }
            return mapOf(
                "et" to executionTime
            )
        }
        else if (qName == "selectTempMax0" || qName == "selectTempMax1") {
            val resultList = ontQ.qSelectOne(qName)
            if (TRACE_TIME_SWITCH) {
                traceWrite("${qName}: ${resultList[0]}")
            }
            return mapOf(
                "et" to resultList[0],
                "area" to resultList[1],
                "areaName" to resultList[2],
                "resultTime" to resultList[3],
                "value" to resultList[4]
            )
        }
        else if (qName == "selectPMAvgMax0" || qName == "selectPMAvgMax1") {
            val resultList = ontQ.qSelectOne(qName)
            if (TRACE_TIME_SWITCH) {
                traceWrite("${qName}: ${resultList[0]}")
            }
            return mapOf(
                "et" to resultList[0],
                "area" to resultList[1],
                "areaName" to resultList[2],
                "latestResultTime" to resultList[3],
                "value" to resultList[4],
                "Average" to resultList[5]
            )
        }
        else if (qName == "selectLLToLight0" || qName == "selectLLToLight1") {
            val resultList = ontQ.qSelectOne(qName)
            if (TRACE_TIME_SWITCH) {
                traceWrite("${qName}: ${resultList[0]}")
            }
            return mapOf(
                "et" to resultList[0],
                "area" to resultList[1],
                "areaName" to resultList[2],
                "avgTraffic" to resultList[3],
                "avgIlluminance" to resultList[4],
                "ratio" to resultList[5]
            )
        }
        else if (qName == "updateSelectLevel") {
            ontQ.qUpdate("updateLevel0")
            val resultList = ontQ.qSelectMany("selectLevel")
            
            val levelMap = mutableMapOf<String, MutableList<Map<String, String>>>()
        
            for (result in resultList) {
                val level = result[1]
                val areaInfo = mapOf(
                    "area" to result[2],
                    "areaName" to result[3]
                )

                levelMap.computeIfAbsent(level) { mutableListOf() }.add(areaInfo)
            }

            // levelMap을 JSON으로 반환
            return levelMap
        }
        else if (qName == "selectRoomQ") {
            val resultList = ontQ.qSelectMany("selectRoomQ")
            
            val roomMap = mutableMapOf<String, Map<String, Any>>() // 방 정보와 Q 값을 저장할 맵
        
            for (result in resultList) {
                val buildingPartUri = result[1]
                val buildingPartLabel = result[2]
                val roomUri = result[3]  // 방의 URI
                val roomLabel = result[4]  // 방의 레이블
                val qualityIndex = result[5].toDouble()  // 계산된 Q 값
        
                // 방 URI를 키로 하고, 레이블과 Q 값을 맵에 저장
                roomMap[roomUri] = mapOf("buildingPartUri" to buildingPartUri, "buildingPartLabel" to buildingPartLabel, "roomLabel" to roomLabel, "qualityIndex" to qualityIndex)
            }
            
            // roomMap을 JSON으로 변환
            return roomMap
        }

        return mapOf(
            "et" to "Error Unknown Query Name"
        )
    }

    @GetMapping("/debugUpdate/{pName}")
    fun debugUpdate(@PathVariable pName: String, model: Model): String {
        logger.info("User Request /debugUpdate/${pName}")
        val executionTime = ontQ.debugUpdateRand(pName)

        if (TRACE_TIME_SWITCH) {
            traceWrite("debug:${pName} ${executionTime}")
        }

        model.addAttribute("message", "Execution time: $executionTime ms")
        return "index"
    }

//=====================================================================================================

    @GetMapping("/TESTdebugUpdate")
    fun TESTdebug(model: Model): String {
        logger.info("User Request /TESTdebugUpdate")
        ontQ.TESTdebugUpdateRand()
        return "index"
    }

    @GetMapping("/TESTcategory/{q0}/{q1}/{q2}")
    @ResponseBody
    fun TESTcategory(@PathVariable q0: String, @PathVariable q1: String, @PathVariable q2: String): Map<String, Any> {
        logger.info("User Request /TESTcategory/$q0/$q1/$q2")
        val resultList = ontQ.TESTqSelectOne(q0, q1, q2)
        return mapOf(
            "et" to resultList[0],
            "uri" to resultList[1],
            "value" to resultList[2],
            "resultTime" to resultList[3]
        )
    }
 
//=====================================================================================================


}