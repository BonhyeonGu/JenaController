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
        val OWL_LOCALE = "./_OWL"
        /*
        val OWL_LOCALES: Array<String> = arrayOf(
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
        */

        //val RULE = OntModelSpec.OWL_MEM_RULE_INF
        val RULE = OntModelSpec.OWL_MEM_TRANS_INF

        val ontologyModel = ModelFactory.createOntologyModel(RULE)
        init {
            /*
            OWL_LOCALES.forEach { url ->
                var type = ""
                try {
                    ontologyModel.read(url, "text/turtle");
                    type = "Tuttle"
                } catch (e: Exception) {
                    ontologyModel.read(url, "application/rdf+xml");
                    type = "RDF/XML"
                }
                logger.info("Load -- type=" + type + "  url=" + url)
            }
            */

            //!!!!OWL과 RDF를 읽는 방법이 다른지 추가적인 조사가 필요하다.!!!!
            readRDF(OWL_LOCALE)
            readRDF(RDF_LOCALE)
            logger.info("Clear OWL, RDF take")
        }
        
        private fun readRDF(locale: String) {
            val directory = File(locale)
            if (directory.exists() && directory.isDirectory) {
                val files = directory.listFiles()
                files?.forEach { file ->
                    ontologyModel.read(file.absolutePath)
                    logger.info("Read RDF => ${RDF_LOCALE}/${file.name}")
                }
            } else {
                logger.error("The provided path is not a valid directory.")
            }
        }
    }

}