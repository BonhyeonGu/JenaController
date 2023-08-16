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
        val result = jenaValidate.validationTest_OWL("sensorthings.owl")
        model.addAttribute("message", result)
        return "index"
    }
}
