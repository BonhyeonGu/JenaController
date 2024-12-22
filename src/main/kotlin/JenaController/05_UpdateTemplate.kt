package JenaController

//--------------------------------------------------------------------
import org.slf4j.Logger
import org.slf4j.LoggerFactory
//--------------------------------------------------------------------
import java.io.File

class UpdateTemplate(private val paths: List<String>) {
    private val fileContents: MutableList<String> = mutableListOf()

    init {
        if (paths.isEmpty()) {
            logger.warn("No file paths provided.")
        }
    }

    fun ready() {
        paths.forEach { path ->
            val file = File(path)
            if (file.exists() && file.isFile) {
                fileContents.add(file.readText())
                logger.info("File loaded: $path")
            } else {
                logger.warn("File not found or invalid: $path")
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UpdateTemplate::class.java)
    }
}