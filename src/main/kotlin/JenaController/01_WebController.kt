package JenaController

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.PathVariable
//문자열을 랜더링없이 그대로
import org.springframework.web.bind.annotation.ResponseBody
//JSON변환을 위함
import org.apache.jena.query.ResultSetFormatter
import java.io.ByteArrayOutputStream

import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.ResultSet

import JenaController.Ontology
import JenaController.Validate
import JenaController.OntQuery

import org.json.JSONObject


import java.io.FileOutputStream

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Controller
class WebController {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(WebController::class.java)
        val ont = Ontology.ontologyModel
        val ontQ = OntQuery(ont)

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
        model.addAttribute("message", "test")
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
        model.addAttribute("message", "Execution time: $executionTime ms")
        return "index"
    }
//=====================================================================================================

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

    /*
    @GetMapping("/getGmlJSON/{gmlID}")
    @ResponseBody
    fun getGmlJSON(@PathVariable gmlID: String, model: Model): String {
        val resultsList = ontQ.idToGML(gmlID)
        
        // ByteArrayOutputStream을 사용하여 결과를 JSON 형식으로 변환
        val outputStream = ByteArrayOutputStream()
        ResultSetFormatter.outputAsJSON(outputStream, resultsList)
        val jsonResults = outputStream.toString("UTF-8")
    
        return jsonResults
    }
    */

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

    @GetMapping("/updateLevel0")
    fun updateLevel0(model: Model): String {
        logger.debug("User Request /updateLevel0")
        val executionTime = ontQ.updateLevel0()
        model.addAttribute("message", "Execution time: $executionTime ms")
        return "index"
    }
    

    @GetMapping("/updateLevel1")
    fun updateLevel1(model: Model): String {
        logger.debug("User Request /updateLevel1")
        val executionTime = ontQ.updateLevel1()
        model.addAttribute("message", "Execution time: $executionTime ms")
        return "index"
    }


    @GetMapping("/selectTempMax0")
    fun selectTempMax0(model: Model): String {
        logger.debug("User Request /selectTempMax0")
        val resultList = ontQ.selectTempMax0()
        if (resultList.isNotEmpty()) {
            model.addAttribute("message", """
                Execution time: ${resultList[0]} ms<br><br>
                Area Name : ${resultList[1]}<br>
                Obs Time : ${resultList[2]}<br>
                Temperature value : ${resultList[3]}
            """)
        } else {
            model.addAttribute("message", "No results found")
        }
        return "index"
    }


    @GetMapping("/selectTempMax1")
    fun selectTempMax1(model: Model): String {
        logger.debug("User Request /selectTempMax1")
        val resultList = ontQ.selectTempMax1()
        if (resultList.isNotEmpty()) {
            model.addAttribute("message", """
                Execution time: ${resultList[0]} ms<br><br>
                Area Name : ${resultList[1]}<br>
                Obs Time : ${resultList[2]}<br>
                Temperature value : ${resultList[3]}
            """)
        } else {
            model.addAttribute("message", "No results found")
        }
        return "index"
    }


    @GetMapping("/debugUpdateVisitRand")
    fun debugUpdateVisitRand(model: Model): String {
        logger.debug("User Request /debugUpdateVisitRand")
        val executionTime = ontQ.debugUpdateVisitRand()
        model.addAttribute("message", "Execution time: $executionTime ms")
        return "index"
    }


    @GetMapping("/debugUpdateTempRand")
    fun debugUpdateTempRand(model: Model): String {
        logger.debug("User Request /debugUpdateTempRand")
        val executionTime = ontQ.debugUpdateTempRand()
        model.addAttribute("message", "Execution time: $executionTime ms")
        return "index"
    }   
}