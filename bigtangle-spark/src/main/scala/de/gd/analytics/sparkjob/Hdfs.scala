package de.gd.analytics.sparkjob

import scala.util.parsing.json.JSON

import org.apache.hadoop.fs.Path
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions

import de.gd.analytics.hdfs.DirectoryContentsUtils
import de.gd.analytics.hdfs.HDFSConfigUtils
import spark.jobserver.DataFramePersister
import spark.jobserver.NamedObjectPersister
import spark.jobserver.NamedRDD
import spark.jobserver.RDDPersister
import spark.jobserver.SparkJob
import spark.jobserver.SparkJobValid
import spark.jobserver.SparkJobValidation

object Hdfs extends SparkJob {
 
  override def validate(sc: SparkContext, config: Config): SparkJobValidation = SparkJobValid

  override def runJob(sc: SparkContext, config: Config): Any = {

    val jsonConfigOpt = if (config.hasPath("jsonRequest")) {
      val jsonReqStr = config.getObject("jsonRequest") 
      val jsonObjList = jsonReqStr.render(ConfigRenderOptions.concise())
 
      Some(jsonObjList)
    } else {
      None
    }

    (jsonConfigOpt) match {
      case (Some(json)) => {
        println("got json")
        val parsed = JSON.parseFull(json)
        parsed match {
          case Some(jsonMap: Map[String, String]) =>
            val operation = jsonMap.getOrElse("operation", "no_operation")
            println(s"operation: $operation")
            operation match {
              case "get" => {
                get(sc, jsonMap)
              }
            }
          case None => println(s"Couldnt parse json $None")
          case _ => println("unknown error")
        }
      }

      case _ => println("You have to specify exactly one")
    }
  }
  def get(sc: SparkContext, jsonMap: Map[String, String]): Any = {
    println("---------------------- get Operation ----------------------" + jsonMap)

    val path = jsonMap("path")
    getWithPath(sc, path, "hdfs")

  }
  def getWithPath(sc: SparkContext, path: String, doAs: String): Any = {

    //  "hdfs" //for non-kerberized cluster, you can set user to perform hdfs operations, using hdfs you won't have permissions issues. if you are using a kerberized cluster, grant read access to user performing this operation (you can use Ranger for this)

    val hdfs = HDFSConfigUtils.loadConfigsAndGetFileSystem("/etc/hadoop/conf", doAs); //specify directory containing hadoop config files
    val currentLevel = 0; //current level, default = 0
    val maxLevelThreshold = -1; //max number of directories do drill down. -1 means no limit. for example: maxLevelThreshold=3 means drill down will stop after 3 levels of subdirectories
    val minSizeThreshold = -1; //min number of bytes in a directory to continue drill down. -1 means no limit. minSizeThreshold=1000000 means only directories greater > 1000000 bytes will be drilled down
    val showFiles = false; //whether to show information about files. showFiles=false will show summary information about files in each directory/subdirectory.
    val excludeList = ""; //directories to exclude from drill down, for example: /tmp/,/user/ won't present information about those directories.
    val verbose = false; //when true print processing info into System.err (not applied for zeppelin)

    val hdfsPath = new Path(path); //path to start analysis
    val dirInfo = DirectoryContentsUtils.listContents(hdfs, hdfsPath, currentLevel, maxLevelThreshold, minSizeThreshold, showFiles, verbose, excludeList);
    return DirectoryContentsUtils.directoryInfoToJson(dirInfo);

  }

  def main(args: Array[String]) {
    val conf = new SparkConf().setMaster("local[4]").setAppName("FirstJob")
    val sc = new SparkContext(conf)
    val config = ConfigFactory.parseString("")
    System.setProperty("HADOOP_USER_NAME", "hdfs");

    getWithPath(sc, "/", "hdfs")
  }
}
 
