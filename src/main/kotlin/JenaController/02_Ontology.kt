package JenaController

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.io.File

import org.apache.jena.ontology.OntModel
import org.apache.jena.ontology.OntModelSpec
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.tdb.TDBFactory
import org.apache.jena.reasoner.ValidityReport

import org.apache.jena.reasoner.Reasoner
import org.apache.jena.reasoner.ReasonerRegistry
import org.apache.jena.rdf.model.InfModel

import org.apache.jena.vocabulary.RDFS

class Ontology {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(Ontology::class.java)
        val TDB_LOCALE = "./_TDB"
        val RDF_LOCALE = "./_RDF"
        val OWL_LOCALE = "https://paper.9bon.org/ontologies/sensorthings/1.1"
        //val RULE = OntModelSpec.OWL_DL_MEM//null
        //val RULE = OntModelSpec.OWL_DL_MEM_RULE_INF//Funtional Error
        //val RULE = OntModelSpec.OWL_DL_MEM_TRANS_INF//ok
        //val RULE = OntModelSpec.OWL_LITE_MEM_RULES_INF//Funtional Error
        val RULE = OntModelSpec.OWL_MEM_MICRO_RULE_INF//ok
        //val RULE = OntModelSpec.OWL_MEM_RULE_INF//Funtional Error
        //val RULE = OntModelSpec.OWL_MEM_TRANS_INF//OK


        val ontologyModel = ModelFactory.createOntologyModel(RULE)
        init {
            ontologyModel.read(OWL_LOCALE)
            val directory = File(RDF_LOCALE)
            if (directory.exists() && directory.isDirectory) {
                val files = directory.listFiles()
                files?.forEach { file ->
                    val tempOnto = ModelFactory.createOntologyModel(RULE)
                    ontologyModel.read(file.absolutePath)
                    logger.info("Read RDF => ${RDF_LOCALE}/${file.name}")
                    tempOnto.close()
                }
            } else {
                logger.error("The provided path is not a valid directory.")
            }
            // owl 변수의 값을 정의하거나 올바르게 참조해야 합니다.
            logger.info("Clear OWL, RDF take")
        }
    }

}