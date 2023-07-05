package jenacontroller;
import org.apache.jena.rdf.model.*;
//import org.apache.jena.tdb.TDBFactory;

public class ImportRDF_MakeModel {
    private Model model;

    public ImportRDF_MakeModel(String path, String format) {
        this.model = ModelFactory.createDefaultModel();
        //this.model = TDBFactory.createDataset("directory");
        this.model.read(path, format);
    }

    public Model getModel() {
        return this.model;
    }
}
