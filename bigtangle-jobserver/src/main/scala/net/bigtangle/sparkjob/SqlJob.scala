package net.bigtangle.sparkjob

import scala.reflect.runtime.universe
import scala.util.parsing.json.JSON

import org.apache.spark.SparkConf
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SaveMode

import com.mongodb.spark.MongoSpark
import com.mongodb.spark.config.ReadConfig
import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions

import spark.jobserver.NamedObjectSupport
import spark.jobserver.SparkJobValid
import spark.jobserver.SparkJobValidation

import spark.jobserver.SparkSessionJob
import org.scalactic.Good
import org.scalactic.Bad
import spark.jobserver.api.JobEnvironment
import org.apache.spark.sql.SparkSession
import spark.jobserver.api.SingleProblem

import com.typesafe.config.Config
import org.apache.spark.sql.SparkSession
import org.scalactic._
import spark.jobserver.SparkSessionJob
import spark.jobserver.api.{ JobEnvironment, SingleProblem, ValidationProblem }
import scala.util.Try
/**
 * This job   runs the SQL .
 */
object SqlJob extends SparkSessionJob with NamedObjectSupport {

  type JobData = String
  type JobOutput = Any

  // val logger = LoggerFactory.getLogger(getClass())
  val limit = 100

  override def validate(sparkSession: SparkSession, runtime: JobEnvironment, config: Config): JobData Or Every[ValidationProblem] = {

    val jsonReqStr = config.getObject("jsonRequest")
    val jsonObjList = jsonReqStr.render(ConfigRenderOptions.concise())

    Good(jsonObjList)
  }

  def runJob(sparkSession: SparkSession, runtime: JobEnvironment, data: JobData): JobOutput = {
    try {
      doRunJob(sparkSession.sqlContext, runtime, data)
    } catch {
      case t: Throwable => {
        t.printStackTrace()
        throw t
      }

    }
  }
  def doRunJob(sql: SQLContext, runtime: JobEnvironment, data: JobData): Any = {

    val json = data

    println("got json: " + json)
    val parsed = JSON.parseFull(json)
    parsed match {
      case Some(jsonMap: Map[String, String]) =>
        val operation = jsonMap.getOrElse("operation", "no_operation")
        println(s"operation: $operation")
        operation match {
          case "select" => {
            selectOperation(sql, jsonMap)
          }
          case "filter" => {
            filterOperation(sql, jsonMap)
          }
          case "join" => {
            joinOperation(sql, jsonMap)
          }
          case "cleansing_missing" => {
            cleansing_missing(sql, jsonMap)
          }
          case "cleansing_replace" => {
            cleansing_replace(sql, jsonMap)
          }
          case "sample" => {
            samplingOperation(sql, jsonMap)
          }
          case "aggregate" => {
            aggregateOperation(sql, jsonMap)
          }

          case "schema" => {
            getSchema(sql, jsonMap)
          }
          case "register" => {
            register(sql, jsonMap)
          }
          case "write" => {
            write(sql, jsonMap)
          }
          case "update" => {
            update(sql, jsonMap)
          }
          case "sql" => {
            sqlExecute(sql, jsonMap)
          }
          case "deregister" => {
            deregister(sql, jsonMap)
          }

          case "deletePath" => {
            deletePath(sql, jsonMap)
          }
          case "registerUDF" => {
            registerUDF(sql, jsonMap)
          }
          case "saveTable" => {
            saveTable(sql, jsonMap)
          }
          case "ingest" => {
            ingest(sql, jsonMap)
          }
          case "registerMongo" => {
            registerMongo(sql, jsonMap)
          }
          case "normalization" => {
            normalize(sql, jsonMap)
          }
          case "registerList" => {
            registerList(sql, jsonMap)
          }
          case "registermysql" => {
            registermysql(sql, jsonMap)
          }
          case _ => throw new RuntimeException("Operation is unknown")
        }
      case None => throw new RuntimeException("Couldnt parse json $None")
      case _ => throw new RuntimeException("unknown error")
    }

    //   throw new RuntimeException();

  }
  def recursiveJoin(table: List[DataFrame], matchCol: String, joinType: String): DataFrame = {
    if (table.isEmpty) {
      null
    } else if (table.size > 1) {
      table.head.join(
        recursiveJoin(table.tail, matchCol: String, joinType: String),
        Seq(matchCol), joinType)
    } else {
      table.head
    }
  }

  def recursiveReplace(table: DataFrame, cols: List[String], usrVals: List[String]): DataFrame = {
    val replaceCol = org.apache.spark.sql.functions.udf { (col: String) =>
      if (col == null || col == "") usrVals.head else col
    }
    if (cols.isEmpty) {
      table
    } else {
      var newtableDF = table.withColumn(cols.head, replaceCol(table(cols.head)))
      //    println(cols.tail)
      recursiveReplace(newtableDF, cols.tail, usrVals.tail)
    }
  }

  def getList(str: String, usrRegex: String): Array[String] = {
    str.split(usrRegex).map(_.trim)
  }

  def selectOperation(sql: SQLContext, jsonMap: Map[String, String]): Any = {
    println("---------------------- Select Operation ----------------------" + jsonMap)
    val table = jsonMap("table")

    val tableDF = SparkSQLData.table(sql, table)
    if (jsonMap("columns").equalsIgnoreCase("*")
      || jsonMap("columns").trim().equals(""))
      return tempTableLimit(tableDF)
    else {
      val fields = jsonMap("columns").split(",").map(_.trim)
      val j = tableDF.select(fields.head, fields.tail: _*)
      return tempTableLimit(j)
    }

  }

  def tempTableLimit(df: DataFrame): Any = {
    val tablename = "temp_" + java.util.UUID.randomUUID().toString().replace("-", "");
    println("register temp table  :" + tablename)
    SparkSQLData.register(df, tablename)
    return (tablename, df.take(limit))
  }
  def tempTable(df: DataFrame): Any = {
    val tablename = "temp_" + java.util.UUID.randomUUID().toString().replace("-", "");
    println("register temp table  :" + tablename)
    SparkSQLData.register(df, tablename)
    return (tablename, df.collect())
  }
  def sqlExecute(sql: SQLContext, jsonMap: Map[String, String]): Any = {

    val sqlQuery = jsonMap("sqlQuery")
    val start = System.currentTimeMillis()
    println("" + start)
    val d = sql.sql(sqlQuery)
    println(" query duration " + (System.currentTimeMillis() - start))
    tempTable(d)
  }

  def getSchema(sql: SQLContext, jsonMap: Map[String, String]): Any = {
    println("---------------------- getSchema Operation ----------------------" + jsonMap)
    val table = jsonMap("table")
    SparkSQLData.table(sql, table).schema
  }

  def filterOperation(sql: SQLContext, jsonMap: Map[String, String]): Any = {
    println("---------------------- Filter Operation ----------------------")

    val columns = jsonMap("columns").split(",").map(_.trim)
    val ops = jsonMap("operator").split(",").map(_.trim)
    val values = jsonMap("values").split(",").map(_.trim)
    val other_filter_columns = jsonMap("other_filter_columns").split(",").map(_.trim)

    var sqlQuery = " select "
    if (columns.length < 1) sqlQuery += " * "
    else {
      for (i <- 0 until columns.length) {
        if (columns(i).equalsIgnoreCase("")) sqlQuery += " *"
        else
          sqlQuery += columns(i);
        if (i < columns.length - 1) sqlQuery += ", "
      }
    }
    sqlQuery += " from " + jsonMap("table")
    if (other_filter_columns.length > 0)
      sqlQuery += " where "
    for (i <- 0 until other_filter_columns.length) {
      //SQL SELECT case insensitive queries - Use upper or lower functions
      sqlQuery += "upper(" + other_filter_columns(i) + ")"
      var op = ops(i)
      if (ops(i).trim().equalsIgnoreCase("contains")) op = "  like "
      if (ops(i).trim().equalsIgnoreCase("equals")
        || ops(i).trim().equalsIgnoreCase("is Equal")) op = " = "
      sqlQuery += op + " '"
      if (ops(i).trim().equalsIgnoreCase("contains")) sqlQuery += "%"
      sqlQuery += values(i).toUpperCase()
      if (ops(i).trim().equalsIgnoreCase("contains")) sqlQuery += "%"
      sqlQuery += "'"
      if (i < other_filter_columns.length - 1) sqlQuery += " and "

    }
    println(sqlQuery)
    tempTable(sql.sql(sqlQuery))
  }

  def joinOperation(sql: SQLContext, jsonMap: Map[String, String]): Any = {
    println("---------------------- Join Operation ----------------------")
    val tablelist =
      getList(jsonMap("tablelist").
        replaceAll(" |\\]|\\[| \\[ ", ""), ",").toList.distinct
    // Get the list of tablelist

    // Get the list of tableattributes NOTE: WE CAN SEND AS ONE STRING
    val tableAttributes = getList(jsonMap("tableattribute").
      replaceAll(" |\\]|\\[| \\[ ", ""), ",").toList.distinct

    val match_on =
      getList(jsonMap("match_on").
        replaceAll(" |\\]|\\[| \\[ ", ""), ",").toList.distinct
    var joinType = jsonMap.getOrElse("type", "inner")
    if (joinType.trim().equalsIgnoreCase("rightinnerjoin")) {
      joinType = "right inner"
    }
    var sqlQuery = " select " + jsonMap("tableattribute") + " from "
    //  FROM <left_table>    <join_type> JOIN <right_table>
    //    ON <join_condition>
    if (match_on.size > 1) {
      sqlQuery += tablelist(0) + " JOIN " + tablelist(1) + " ON "
      sqlQuery += tablelist(0) + "." + match_on(0) + " = " + tablelist(1) + "." + match_on(1)
      //may be user defined functions
      sqlQuery += " or getYearMonth (" + tablelist(0) + "." + match_on(0) + ") = " + tablelist(1) + "." + match_on(1)
      sqlQuery += " or " + tablelist(0) + "." + match_on(0) + "  =   getYearMonth (" + tablelist(1) + "." + match_on(1) + ")"

    } else {
      sqlQuery += tablelist(0) + " JOIN " + tablelist(1) + " ON " + tablelist(0) + "." + match_on(0) + " = " + tablelist(1) + "." + match_on(0)
    }
    println("query: " + sqlQuery)

    tempTable(sql.sql(sqlQuery))
  }

  def cleansing_missing(sql: SQLContext, jsonMap: Map[String, String]): Any = {
    var d = sql.table(jsonMap("table"))
    cleansing_missing(sql, jsonMap, d)

  }

  def cleansing_missing(sql: SQLContext, jsonMap: Map[String, String], prevDF: DataFrame): Any = {
    println("----------------------   Cleansing Operation ----------------------")
    tempTable(prevDF.na.drop())
  }

  def cleansing_replace(sql: SQLContext, jsonMap: Map[String, String]): Any = {
    var d = sql.table(jsonMap("table"))
    cleansing_replace(sql, jsonMap, d)

  }

  def cleansing_replace(sql: SQLContext, jsonMap: Map[String, String], prevDF: DataFrame): Any = {

    val cols = getList(jsonMap("other_attributes_name"), ",").toList
    val usrVals = getList(jsonMap("other_attributes_values"), ",").toList
    val prevDF1 = recursiveReplace(prevDF, cols, usrVals)

    return tempTable(prevDF1)

  }

  def samplingOperation(sql: SQLContext, jsonMap: Map[String, String]): Any = {

    samplingOperation(sql, jsonMap, sql.table(jsonMap("table")))

  }

  def samplingOperation(sql: SQLContext, jsonMap: Map[String, String], prevDF: DataFrame): Any = {
    println("----------------------   Sampling Operation ----------------------")
    val replaceBool = jsonMap("withreplacement")
    var sampleSize = jsonMap("sample_size")
    if (sampleSize == null || "".equals(sampleSize)) sampleSize = "0.1"
    var sampleSeed = jsonMap("sample_seed")
    if (sampleSeed == null || "".equals(sampleSeed)) sampleSeed = "100"
    tempTable(prevDF.sample(replaceBool.toBoolean, sampleSize.toDouble, sampleSeed.toInt))

  }

  def aggregateOperation(sql: SQLContext, jsonMap: Map[String, String]): Any = {

    println("---------------------- Aggregation Operation ----------------------")

    val aggcols = getList(jsonMap("aggregate_column_names"), ",")
    val grpcols = getList(jsonMap("groupby"), ",")
    val aggFunctions = getList(jsonMap("aggregate_function"), ",")
    val aggregate_columns = getList(jsonMap("aggregate_columns"), ",")
    var sqlQuery = " select "

    for (i <- 0 until aggFunctions.length) {
      var op = aggFunctions(i).toLowerCase()
      if (op.trim().equalsIgnoreCase(""))
        sqlQuery += aggcols(i);
      else
        sqlQuery += op + "(" + aggcols(i) + ")";
      if (i < aggFunctions.length - 1) sqlQuery += ", "
    }

    sqlQuery += " from " + jsonMap("table")
    sqlQuery += " group by  "
    for (i <- 0 until grpcols.length) {
      sqlQuery += grpcols(i)
      if (i < grpcols.length - 1) sqlQuery += " , "
    }
    println(sqlQuery)
    tempTable(sql.sql(sqlQuery))

  }

  def deregister(sql: SQLContext, jsonMap: Map[String, String]): Unit = {
    val tablename = jsonMap("table")
    SparkSQLData.deRegister(tablename, sql)
  }
  def register(sql: SQLContext, jsonMap: Map[String, String]): Unit = {
    println("---------------------- register file as table ----------------------")
    val filePathName = jsonMap("filePathName")
    val skip = jsonMap("skip")
    val format = jsonMap("format")

    val tablename = jsonMap("table")

    format match {
      case "csv" => {
        SparkSQLData.register(filePathName, tablename, sql)
      }
      case "parquet" => {
        SparkSQLData.registerParquet(filePathName, tablename, sql)
      }
      case "json" => {
        SparkSQLData.registerJSON(filePathName, tablename, sql)
      }
      case "avro" => {
        SparkSQLData.registerAVRO(filePathName, tablename, sql)
      }
      case _ => {
        println(": csv, file:  " + filePathName)
        SparkSQLData.register(filePathName, tablename, sql)
      }
    }
  }

  def registermysql(sql: SQLContext, jsonMap: Map[String, String]): Unit = {
    println("---------------------- register file as table ----------------------")

    val tablename = jsonMap("table")

    SparkSQLData.registermysql("", tablename, sql)
  }

  def registerList(sqlContext: SQLContext, jsonMap: Map[String, String]): Unit = {
    println("---------------------- register list of files as table ----------------------")
    val filePathName = getList(jsonMap("filePathName"), ",")
    val skip = jsonMap("skip")
    val format = jsonMap("format")

    val tablename = jsonMap("table")

    var registerFile = sqlContext.read.format("com.databricks.spark.avro").load(filePathName(0))

    for (i <- 1 until filePathName.length) {
      registerFile = registerFile.unionAll(sqlContext.read.format("com.databricks.spark.avro").load(filePathName(i)))
    }
    SparkSQLData.register(registerFile, tablename)
  }

  /*
   * read the  table data from json and write to file with format
   */
  def write(sql: SQLContext, jsonMap: Map[String, String]): Unit = {
    println("---------------------- write  table to file ----------------------")
    val filePathName = jsonMap("filePathName")
    val format = jsonMap("format")
    val tablename = jsonMap("table")
    val tabledata = jsonMap("tabledata")

    val rdd = sql.sparkContext parallelize (Seq(tabledata))
    val df = sql.read.json(rdd)
    // df.printSchema()
    val savemode = SaveMode.Append
    format match {
      case "csv" => {
        df.write.mode(savemode).json(filePathName)
      }
      case "parquet" => {
        df.write.mode(savemode).parquet(filePathName)
      }
      case "json" => {
        df.write.mode(savemode).json(filePathName)
      }

      case _ => {
        df.write.mode(savemode).parquet(filePathName)
      }

    }
  }
  /*
    * update the  table data from json and write to file
   */
  def update(sql: SQLContext, jsonMap: Map[String, String]): Unit = {
    println("---------------------- update  table to file ----------------------")
    val filePathName = jsonMap("filePathName")
    val format = jsonMap("format")
    val tablename = jsonMap("table")
    val coldata = jsonMap("coldata")
    val col = jsonMap("col")

    val rdd = sql.sparkContext parallelize (Seq(coldata))
    var df = sql.table(tablename)
    //  df.withColumn(col, coldata)
    //  df.write.mode(SaveMode.Overwrite).parquet(filePathName)
    //  SparkSQLData.writeTable(filePathName, tablename, sql, format)

  }
  def deletePath(sql: SQLContext, jsonMap: Map[String, String]): Unit = {
    println("---------------------- delete file ----------------------")
    val filePathName = jsonMap("filePathName")
    val uri = jsonMap("uri")
    deletePath(uri, filePathName)
  }
  def deletePath(uri: String, filePathName: String): Unit = {
    val hadoopConf = new org.apache.hadoop.conf.Configuration()
    val hdfs = org.apache.hadoop.fs.FileSystem.get(new java.net.URI(uri), hadoopConf)
    try { hdfs.delete(new org.apache.hadoop.fs.Path(filePathName), true) } catch { case _: Throwable => {} }
  }

  /*
   * write existing table in another file format
   */
  def saveTable(sql: SQLContext, jsonMap: Map[String, String]): Unit = {
    println("---------------------- write  table to file ----------------------")
    val filePathName = jsonMap("filePathName")
    val format = jsonMap("format")
    val tablename = jsonMap("table")

    SparkSQLData.writeTable(filePathName, tablename, sql, format)

  }
  def registerUDF(sqlContext: SQLContext, jsonMap: Map[String, String]): Unit = {
    SparkSQLData.registerUDF(sqlContext)
  }

  def ingest(sql: SQLContext, jsonMap: Map[String, String]): Any = {
    println("---------------------- ingest Operation ----------------------" + jsonMap)
    val sc = sql.sparkContext
    val source_uri = jsonMap("source_uri")
    val filePathName = jsonMap("filePathName")
    val format = jsonMap("format")
    // Loading data with a custom ReadConfig
    val conf = new SparkConf()
      .setMaster("local[*]")
      .setAppName("MongoSparkConnectorTour")
      .set("spark.app.id", "MongoSparkConnectorTour")
      .set("spark.mongodb.input.uri", source_uri)
      .set("spark.mongodb.output.uri", source_uri)
    val writeConfig = ReadConfig.apply(conf)

    val df = MongoSpark.load(sc, writeConfig).toDF

    SparkSQLData.writeTable(filePathName, df, format)
  }

  def registerMongo(sql: SQLContext, jsonMap: Map[String, String]): Any = {
    println("---------------------- ingest Operation ----------------------" + jsonMap)
    val sc = sql.sparkContext
    val source_uri = jsonMap("source_uri")
    val table = jsonMap("table")

    // Loading data with a custom ReadConfig
    val conf = new SparkConf()
      .setMaster("local[*]")
      .setAppName("MongoSparkConnectorTour")
      .set("spark.app.id", "MongoSparkConnectorTour")
      .set("spark.mongodb.input.uri", source_uri)
      .set("spark.mongodb.output.uri", source_uri)
    val writeConfig = ReadConfig.apply(conf)

    val df = MongoSpark.load(sc, writeConfig).toDF

    SparkSQLData.register(df, table)
  }
  def normalize(sql: SQLContext, jsonMap: Map[String, String]): Any = {
    println("---------------------- normalize Operation ----------------------" + jsonMap)
    //  zi=(xi−min(x) )/ (max(x)−min(x)) where x=(x1,...,xn)
    val tablename = jsonMap("table")

    val columns = jsonMap("columns").split(",").map(_.trim)
    /*
    var sqlQuery = " select "
    for (i <- 0 until columns.length) {
      sqlQuery += "max(" + columns(i) + ") ,"

      sqlQuery += "min(" + columns(i) + ") "
      if (i < columns.length - 1) sqlQuery += " , "
    }
    println(sqlQuery)
    val maxmin = sql.sql(sqlQuery)
   *
   *
   */
    val table = SparkSQLData.table(sql, tablename)
    for (i <- 0 until columns.length) {
      val maxVal = table.agg(org.apache.spark.sql.functions.max((columns(i))))
      val minVal = table.agg(org.apache.spark.sql.functions.min((columns(i))))
    }

  }
  def recursiveNormalize(table: DataFrame, col: String, maxVal: Double, minVal: Double): DataFrame = {
    //    val maxHead=table.agg(max(maxVals.head))

    val replaceCol = org.apache.spark.sql.functions.udf { (col: String) =>
      if (col == null || col == "")
        (col.toDouble - minVal) / (maxVal - minVal)
      else 0
    }
    table.withColumn(col, replaceCol(table(col)))

  }
}