package JenaController

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.io.File

import org.apache.jena.ontology.OntModel
import org.apache.jena.ontology.OntModelSpec
import org.apache.jena.ontology.OntDocumentManager

import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.tdb.TDBFactory
import org.apache.jena.reasoner.ValidityReport

import org.apache.jena.reasoner.Reasoner
import org.apache.jena.reasoner.ReasonerRegistry
import org.apache.jena.rdf.model.InfModel

import org.apache.jena.vocabulary.RDFS
import org.apache.jena.riot.RiotException

class Ontology {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(Ontology::class.java)
        val TDB_LOCALE = "./_TDB"
        val RDF_LOCALE = "./_RDF"
        val OWL_LOCALE = "./_OWL"
        val OWL_LOCALES: Array<String> = arrayOf(
            "https://paper.9bon.org/ontologies/sensorthings/1.1",
            "https://paper.9bon.org/ontologies/smartcity/0.2"
        )
        val OWL_LOCALES2: Array<String> = arrayOf(
            "https://paper.9bon.org/ontologies/sensorthings/1.1",

            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/appearance",
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/bridge",
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/building",
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/cityfurniture",
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/cityobjectgroup",
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/core",
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/generics",
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/landuse",
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/relief",
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/transportation",
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/tunnel",
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/vegetation",
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/CityGML/2.0/waterbody",

            "https://www.w3.org/2009/08/skos-reference/skos.rdf",
            "http://schemas.opengis.net/geosparql/1.0/geosparql_vocab_all.rdf",
            "http://schemas.opengis.net/gml/3.2.1/gml_32_geometries.rdf",
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/Alignments/CityGML2-GeoSPARQL",
            "https://dataset-dl.liris.cnrs.fr/rdf-owl-urban-data-ontologies/Ontologies/Alignments/CityGML2-ISO19136",
            "https://def.isotc211.org/ontologies/iso19136/2007/Feature.rdf",
            "https://def.isotc211.org/ontologies/iso19107/2003/CoordinateGeometry.rdf"
        )

        //val RULE = OntModelSpec.OWL_MEM_RULE_INF
        val RULE = OntModelSpec.OWL_MEM_TRANS_INF
        private val readStatusMap: MutableMap<String, Boolean> = mutableMapOf()
        //메니저 생성
        private val ontDocMgr = OntDocumentManager().apply {
            //setProcessImports(false)
            setReadFailureHandler { uri, model, e ->
                logger.error("Read Fail, URI => $uri, Handle => OntManager, ${e.message}")
                readStatusMap[uri] = false
            }
        }
        //온톨로지 모델의 메니저 정의
        private val ontModelSpec = OntModelSpec(RULE).apply {
            documentManager = ontDocMgr
        }
        val ontologyModel: OntModel = ModelFactory.createOntologyModel(ontModelSpec)
        
        init {
            //작성한 OWL들을 불러옴
            OWL_LOCALES.forEach { url ->
                logger.info("Try read URL => " + url)
                readStatusMap[url] = true
                try {
                    ontologyModel.read(url, "text/turtle")
                    logger.info("Read Complite, Type => Turtle")
                } catch (e: Exception) {
                    try {
                        ontologyModel.read(url, "application/rdf+xml")
                        logger.info("Read Complite, Type => RDF/XML")
                    } catch (e: Exception) {
                        logger.error("Read Fail, URI => $url, Handle => OntManager, ${e.message}")
                        readStatusMap[url] = false
                    }
                }
            }

            //!!!!OWL과 RDF를 읽는 방법이 다른지 추가적인 조사가 필요하다.!!!!
            readRDF(OWL_LOCALE)
            readRDF(RDF_LOCALE)
            logger.info("")
            logger.info("")
            logger.info("Successfully read the following URLs without errors:")
            //에러없는 OWL, RDF 리스트
            readStatusMap.forEach { (url, success) ->
                if (success) {
                    logger.info(url)
                }
            }
            logger.info("")
            logger.info("")
        }
        
        private fun readRDF(locale: String) {
            val directory = File(locale)
            if (directory.exists() && directory.isDirectory) {
                val files = directory.listFiles()
                files?.forEach { file ->
                    logger.info("Read RDF => ${RDF_LOCALE}/${file.name}")
                    try {
                        ontologyModel.read(file.absolutePath)
                    } catch (e: RiotException) {
                        logger.error("RiotException => ${RDF_LOCALE}/${file.name}")
                    }
                }
            } else {
                logger.error("The provided path is not a valid directory.")
            }
        }
    }

}