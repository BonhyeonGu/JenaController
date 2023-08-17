package JenaController

import org.apache.jena.ontology.OntModel
import org.apache.jena.ontology.OntModelSpec
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.reasoner.ValidityReport
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.apache.jena.reasoner.Reasoner
import org.apache.jena.reasoner.ReasonerRegistry
import org.apache.jena.rdf.model.InfModel


class JenaValidate {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JenaValidate::class.java)
    }

    init {
        logger.info("Model TestLoad Complete")
    }

    fun validationTest_OWL(owl: String) {
        logger.debug("About to validate the following OWL models: $owl")
        val model = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RULE_INF)
        model.read(owl)

        // 유효성 검사
        val reasoner: Reasoner = ReasonerRegistry.getOWLReasoner()
        val infModel: InfModel = ModelFactory.createInfModel(reasoner, model)

        val validity = infModel.validate()
        if (validity.isValid) {
            logger.info("The OWL file is valid.")
        } else {
            logger.info("The OWL file is not valid. Errors:")
            validity.reports.forEach { report ->
                logger.error(" - ${report.description}")
            }
        }
    }

    fun validationTest_OWLandRDF(owl: String, rdf: String) {
        val ontologyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RULE_INF)
        ontologyModel.read(owl)

        val dataModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RULE_INF)
        dataModel.addSubModel(ontologyModel)
        dataModel.read(rdf)

        val validityReport = dataModel.validate()

        if (validityReport.isValid) {
            logger.info("RDF data is valid against the given OWL ontology")
        } else {
            logger.info("RDF data is NOT valid against the given OWL ontology")
            val i = validityReport.reports.iterator()
            while (i.hasNext()) {
                val report = i.next()
                logger.error(" - ${report.description}")
            }
        }
    }

    fun execute(): String {
        // 원하는 로직 실행
        return "Service executed successfully!"
    }
}