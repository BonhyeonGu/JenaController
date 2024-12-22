package JenaController

//--------------------------------------------------------------------
import org.slf4j.Logger
import org.slf4j.LoggerFactory
//--------------------------------------------------------------------
import java.io.File

class UpdateTemplate(private val dirs: List<String>) {
    private val fileContents: MutableList<String> = mutableListOf()

    init {
        if (dirs.isEmpty()) {
            logger.warn("No directory paths provided.")
        }
    }

    fun ready() {
        dirs.forEach { dirPath ->
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().filter { it.isFile && it.extension == "txt" }.forEach { file ->
                    fileContents.add(file.readText())
                    logger.info("File loaded: ${file.absolutePath}")
                }
            } else {
                logger.warn("Directory not found or invalid: $dirPath")
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UpdateTemplate::class.java)
    }
}
