package JenaController

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

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
        val jenaValidate = Validate()
        //jenaValidate.validationTest_OWL("https://paper.9bon.org/ontologies/sensorthings/1.1")
        jenaValidate.validationTest_OWLandRDF("https://paper.9bon.org/ontologies/sensorthings/1.1", "out.rdf")
        model.addAttribute("message", "test")
        return "index"
    }

    @GetMapping("/browse/{resource}")
    fun browseResource(@PathVariable resource: String, model: Model): String {
        logger.debug("User Request /browse/$resource")
        val resourceURI = ontQ.deShort(resource)
        val resourceInfo = ontQ.browseQuery(resourceURI)
        model.addAttribute("resourceInfo", resourceInfo)
        return "browse"
    }
}
