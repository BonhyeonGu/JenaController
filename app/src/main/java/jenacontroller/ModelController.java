package jenacontroller;
//-------------------------------------
import org.apache.jena.rdf.model.*;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.query.Dataset;
//-------------------------------------
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ModelController {
    private Model model;
    private static final Logger logger = LoggerFactory.getLogger(ModelController.class);

    public ModelController(String path, String format) {
        //this.model = ModelFactory.createDefaultModel();
        //this.model.read(path, format);
        Dataset dataset = TDBFactory.createDataset("./_db");
        model = dataset.getDefaultModel();
        model.read(path, format);
        logger.info("Model Read Complite");
    }

    public Model getModel() {
        return this.model;
    }
}
