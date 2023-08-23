package JenaController

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.PathVariable

import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryExecutionFactory

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
        model.addAttribute("message", "zzz")
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

    @GetMapping("/browse/{resource}")
    fun browseResource(@PathVariable resource: String, model: Model): String {
        logger.debug("User Request /browse/$resource")
        val resourceURI = ontQ.deShort(resource)
        model.addAttribute("resourceURI", resourceURI)

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
}
