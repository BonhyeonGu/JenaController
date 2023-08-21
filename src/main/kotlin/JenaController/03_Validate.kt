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

import org.apache.jena.vocabulary.RDFS

class Validate {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(Validate::class.java)
    }

    init {
        logger.info("Model TestLoad Complete")
    }


    fun getLabelOrUri(model: OntModel, resource: org.apache.jena.rdf.model.Resource): String {
        if (resource.isAnon) {
            return "AnonNode_" + resource.id.labelString
        }
        val labelStmt = resource.getProperty(RDFS.label)
        return labelStmt?.string ?: resource.uri
    }
    

    fun validationTest_OWL(owl: String) {
        logger.debug("About to validate the following OWL models: $owl")
        val model = ModelFactory.createDefaultModel()
        model.read(owl)

        // 유효성 검사
        val reasoner: Reasoner = ReasonerRegistry.getOWLReasoner()
        val infModel: InfModel = ModelFactory.createInfModel(reasoner, model)

        val validity = infModel.validate()
        if (validity.isValid) {
            println("The OWL file is valid.")
        } else {
            println("The OWL file is not valid. Errors:")
            validity.reports.forEach { report ->
                println(" - ${report.description}")
            }
        }
    }

    fun validationTest_OWLandRDF(owl: String, rdf: String) {
        //val rule = OntModelSpec.OWL_DL_MEM//null
        //val rule = OntModelSpec.OWL_DL_MEM_RULE_INF//Funtional Error
        val rule = OntModelSpec.OWL_DL_MEM_TRANS_INF//ok

        
        val ontologyModel = ModelFactory.createOntologyModel(rule)
        ontologyModel.read(owl)

        val dataModel = ModelFactory.createOntologyModel(rule)
        dataModel.addSubModel(ontologyModel)
        dataModel.read(rdf)

        val validityReport = dataModel.validate()

        if (validityReport.isValid) {
            println("RDF data is valid against the given OWL ontology")
            val iter = dataModel.listStatements()
            var count = 0
            while (iter.hasNext() && count < 20) {
                val stmt = iter.nextStatement()
                val subjectLabel = getLabelOrUri(dataModel, stmt.subject)
                val predicateLabel = getLabelOrUri(dataModel, stmt.predicate)
                val objectLabel = if (stmt.`object`.isResource) {
                    getLabelOrUri(dataModel, stmt.`object`.asResource())
                } else {
                    stmt.`object`.toString()
                }
                println("$subjectLabel, $predicateLabel, $objectLabel")
                count++
            }
        } else {
            println("RDF data is NOT valid against the given OWL ontology")
            val i = validityReport.reports.iterator()
            while (i.hasNext()) {
                val report = i.next()
                println(" - ${report.description}")
            }
        }
    }

    fun execute(): String {
        // 원하는 로직 실행
        return "Service executed successfully!"
    }
}