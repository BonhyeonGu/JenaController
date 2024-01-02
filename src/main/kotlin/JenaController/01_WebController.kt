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


@Controller
class WebController {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(WebController::class.java)
        val ont = Ontology.ontologyModel
        val ontQ = OntQuery(ont)
    }

    @GetMapping("/")
    fun index(model: Model): String {
        logger.debug("User Request /")
        model.addAttribute("message", "Index")
        return "index"
    }

    @GetMapping("/test")
    fun test(model: Model): String {
        logger.debug("User Request /test")
        val vali = Validate(ont)
        //jenaValidate.validationTest_OWL("https://paper.9bon.org/ontologies/sensorthings/1.1")
        vali.validationTest_OWLandRDF()
        model.addAttribute("message", "test")
        return "index"
    }

    //http://localhost:8080/browse/sts-EL_SMARTPOLE_MEDIAPOLE-W_1(scanner)+Thing_00
    @GetMapping("/browse/{resource}")
    fun browseResource(@PathVariable resource: String, model: Model): String {
        logger.debug("User Request /browse/$resource")
        val resourceURI = ontQ.deShort(resource)
        model.addAttribute("resourceURI", resourceURI)
        if (ontQ.isProperty(resourceURI)) {
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
            model.addAttribute("propertyDetails", propertyDetails)
            return "browseProperty"
        } else {
            var q = """
                SELECT ?property ?value WHERE {
                    <$resourceURI> ?property ?value.
                }
            """.trimIndent()

            var resourceInfo = ontQ.browseQuery(q)
            model.addAttribute("resourceInfo", resourceInfo)

            q = """
                SELECT ?property ?value WHERE {
                    ?value ?property <$resourceURI>.
                }
            """.trimIndent()
            
            resourceInfo = ontQ.browseQuery(q)
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

//=====================================================================================================

    @GetMapping("/selectJSON/{bldgname}/{type}")
    @ResponseBody
    fun selectJSON(@PathVariable bldgname: String, @PathVariable type: String): String {
        val fullBldgName = ontQ.deShort(bldgname)
        val resultsList = ontQ.selectType("$fullBldgName", type)
        
        // ByteArrayOutputStream을 사용하여 결과를 JSON 형식으로 변환
        val outputStream = ByteArrayOutputStream()
        ResultSetFormatter.outputAsJSON(outputStream, resultsList)
        val jsonResults = outputStream.toString("UTF-8")
    
        return jsonResults
    }

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

    @GetMapping("/getMetaJSON/{gmlID}")
    @ResponseBody
    fun getMetaJSON(@PathVariable gmlID: String, model: Model): String {
        val resultsList = ontQ.idToMeta(gmlID)
        
        // ByteArrayOutputStream을 사용하여 결과를 JSON 형식으로 변환
        val outputStream = ByteArrayOutputStream()
        ResultSetFormatter.outputAsJSON(outputStream, resultsList)
        val jsonResults = outputStream.toString("UTF-8")
    
        return jsonResults
    }

//=====================================================================================================

    @GetMapping("/getGmlXML/{gmlID}", produces = ["application/xml"])
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

//=====================================================================================================


}