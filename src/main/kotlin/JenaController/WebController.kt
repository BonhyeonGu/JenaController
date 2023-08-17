package JenaController

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

import JenaController.JenaValidate

@Controller
class WebController {
    @GetMapping("/")
    fun index(model: Model): String {
        model.addAttribute("message", "zzz")
        return "index"
    }

    @GetMapping("/test")
    fun test(model: Model): String {
        val jenaValidate = JenaValidate()
        val result = jenaValidate.validationTest_OWL("https://paper.9bon.org/ontologies/sensorthings/1.1")
        jenaValidate.validationTest_OWLandRDF("https://paper.9bon.org/ontologies/sensorthings/1.1", "out.rdf")
        model.addAttribute("message", result)
        return "index"
    }
}
