package jenacontroller;

import java.util.Iterator;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.ValidityReport;
//-------------------------------------
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelTester {
    private static final Logger logger = LoggerFactory.getLogger(ModelTester.class);

    public ModelTester() {
        logger.info("Model TestLoad Complite");
    }

    public void validationTest_OWL(String owl) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RULE_INF);
        ontModel.read(owl);
    
        ValidityReport validityReport = ontModel.validate();
    
        if (validityReport.isValid()) {
            System.out.println("Ontology is valid");
        }
        else {
            System.out.println("Ontology is NOT valid");
            Iterator<ValidityReport.Report> i = validityReport.getReports();
            while (i.hasNext()) {
                ValidityReport.Report report = i.next();
                System.out.println(" - " + report.getDescription());
            }
        }
        return;
    }

    public void validationTest_OWLandRDF(String owl, String rdf) {
        OntModel ontologyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        ontologyModel.read(owl);
    
        OntModel dataModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RULE_INF, null);
        dataModel.addSubModel(ontologyModel);
        dataModel.read(rdf);
    
        ValidityReport validityReport = dataModel.validate();
    
        if (validityReport.isValid()) {
            System.out.println("RDF data is valid against the given OWL ontology");
        }
        else {
            System.out.println("RDF data is NOT valid against the given OWL ontology");
            Iterator<ValidityReport.Report> i = validityReport.getReports();
            while (i.hasNext()) {
                ValidityReport.Report report = i.next();
                System.out.println(" - " + report.getDescription());
            }
        }
        return;
    }
}
