package jenacontroller;
//-------------------------------------
import org.apache.jena.rdf.model.*;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.query.Dataset;
//-------------------------------------
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.reasoner.ValidityReport;
import java.util.Iterator;
//-------------------------------------
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ImportRDF_MakeModel {
    private Model model;
    private static final Logger logger = LoggerFactory.getLogger(ImportRDF_MakeModel.class);

    public ImportRDF_MakeModel(String path, String format) {
        //this.model = ModelFactory.createDefaultModel();
        //this.model.read(path, format);
        Dataset dataset = TDBFactory.createDataset("./_db");
        model = dataset.getDefaultModel();
        model.read(path, format);
    }

    public void getLogInfo() {
        logger.info("This is an information message.");
    }

    public Model getModel() {
        return this.model;
    }

    public Model validationTest(String owl) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RULE_INF);
        ontModel.read(owl);
    
        ValidityReport validityReport = ontModel.validate();
    
        if (validityReport.isValid()) {
            System.out.println("Ontology is valid");
        } else {
            System.out.println("Ontology is NOT valid");
            Iterator<ValidityReport.Report> i = validityReport.getReports();
            while (i.hasNext()) {
                ValidityReport.Report report = i.next();
                System.out.println(" - " + report.getDescription());
            }
        }
        return this.model;
    }
}
