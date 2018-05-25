package de.gd.analytics.sparkjob

import java.sql.Timestamp
import java.text.SimpleDateFormat

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SaveMode
import org.apache.spark.sql.api.java.UDF1
import org.apache.spark.storage.StorageLevel

import com.typesafe.config.Config

import spark.jobserver.DataFramePersister
import spark.jobserver.NamedDataFrame
import spark.jobserver.NamedObjectPersister
import spark.jobserver.NamedRDD
import spark.jobserver.RDDPersister
import spark.jobserver.SparkJobValid
import spark.jobserver.SparkJobValidation

import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.types.IntegerType
import spark.jobserver.SparkSessionJob
import spark.jobserver.NamedObjectSupport
import spark.jobserver.NamedObjects
import spark.jobserver.SparkSessionJob
import spark.jobserver.api.{ JobEnvironment, SingleProblem, ValidationProblem }
import scala.util.Try
import org.apache.spark.sql.SparkSession
import org.scalactic.Bad
import com.typesafe.config.Config
import org.apache.spark.sql.SparkSession
import org.scalactic._
import spark.jobserver.SparkSessionJob
import spark.jobserver.api.{ JobEnvironment, SingleProblem, ValidationProblem }
import scala.util.Try
import com.typesafe.config.ConfigRenderOptions
import spark.jobserver.JobServerNamedObjects
import akka.actor.ActorSystem

object SparkSQLData extends SparkSessionJob   {

  type JobData = String
  type JobOutput = Any

  implicit def rddPersister[T]: NamedObjectPersister[NamedRDD[T]] = new RDDPersister[T]
  implicit val dataFramePersister = new DataFramePersister
  var namedObjects: NamedObjects = _
  val doAs = "hdfs";

  override def validate(sparkSession: SparkSession, runtime: JobEnvironment, config: Config): JobData Or Every[ValidationProblem] = {
 
    Good("")
  }

  def runJob(sparkSession: SparkSession, runtime: JobEnvironment, data: JobData): JobOutput = {
    try {
       namedObjects = new JobServerNamedObjects (ActorSystem("NamedObjectsSpec")) 
      doRunJob(sparkSession.sqlContext, runtime, data)
    } catch {
      case t: Throwable => {
        t.printStackTrace()
        throw t
      }

    }
  }
  def doRunJob(sqlContext: SQLContext, runtime: JobEnvironment, data: JobData): Any = {

    registerUDF(sqlContext)
    sqlContext.udf.register("getYearMonthDayNumber", getYearMonthDayNumber _)
    ""
  }
  def registerUDF(sqlContext: SQLContext): Unit = {

    val getYearMonth = (date: String) => {
      try {
        val myformatString = "yyyy-MM-dd hh:mm:ss"
        val d = getTimestamp(date, myformatString)
        val format = new SimpleDateFormat("MM-yy")
        format.format(d)
      } catch { case _: Throwable => { "NOTDATE-TRY" } }

    }

    val getYearMonthDay = (date: String) => {

      try {
        val myformatString = "yyyy-MM-dd hh:mm:ss"
        val d = getTimestamp(date, myformatString)
        val format = new SimpleDateFormat("dd.MM.yyyy")
        format.format(d)
      } catch { case _: Throwable => { "NOTDATE-TRY" } }
    }

    sqlContext.udf.register("getYearMonth", getYearMonth)

    sqlContext.udf.register("getYearMonthDay", getYearMonthDay)

  }

  def getYearMonthDayNumber(date: String): Long = {
    getTimestampLongFunc(date, "yyyy-MM-dd hh:mm:ss")
  }

  def getTimestampLongFunc(x: String, formatString: String): Long = {
    try {
      return getTimestamp(x, formatString).getTime
    } catch {
      case _: Throwable => { 0l }
    }

  }
  def getTimestamp(x: String, formatString: String): java.sql.Timestamp = {
    //
    var myformatString = "yyyy-MM-dd hh:mm:ss"
    if (formatString != null) myformatString = formatString
    val format = new SimpleDateFormat(myformatString)
    if (x.toString() == "")
      return null
    else {

      val d = format.parse(x.toString());
      val t = new Timestamp(d.getTime());
      return t

    }
  }

  def writeTable(filelocaltion: String, tablename: String, sqlContext: SQLContext, writeFormat: String) {
    val fileTable = table(sqlContext, tablename)
    writeTable(filelocaltion, fileTable, writeFormat)
  }

  def writeTable(filelocaltion: String, fileTable: DataFrame, writeFormat: String) {

    // System.setProperty("HADOOP_USER_NAME", doAs);

    writeFormat match {
      case "parquet" => {
        fileTable.write.mode(SaveMode.Overwrite).parquet(filelocaltion)
      }
      case "json" => {
        fileTable.write.mode(SaveMode.Overwrite).json(filelocaltion)
      }
      case "csv" =>
        {
          fileTable.write.format("com.databricks.spark.csv")
            .option("header", "true") //
            .option("inferSchema", "true") //
            .save(filelocaltion)
        }
      case "avro" =>
        {
          fileTable.write.mode(SaveMode.Overwrite).format("avro").save(filelocaltion)
        }
        //default
        fileTable.write.mode(SaveMode.Overwrite).text(filelocaltion)
    }
  }
  def registerParquet(file: String, tablename: String, sqlContext: SQLContext) {
    val parquetFile = sqlContext.read.parquet(file)
    parquetFile.registerTempTable(tablename)
    namedObjects.update("df:" + tablename, NamedDataFrame(parquetFile, forceComputation = false, storageLevel = StorageLevel.NONE))
  }
  def registerJSON(file: String, tablename: String, sqlContext: SQLContext) {
    val registerFile = sqlContext.read.json(file)
    registerFile.registerTempTable(tablename)
    this.namedObjects.update("df:" + tablename, NamedDataFrame(registerFile, forceComputation = false, storageLevel = StorageLevel.NONE))
  }
  def registerAVRO(file: String, tablename: String, sqlContext: SQLContext) {
    val registerFile = sqlContext.read.format("com.databricks.spark.avro").load(file)
    registerFile.registerTempTable(tablename)
    if (this.namedObjects != null) {
      this.namedObjects.update("df:" + tablename, NamedDataFrame(registerFile, forceComputation = false, storageLevel = StorageLevel.NONE))
    }

  }
  def register(file: String, tablename: String, sqlContext: SQLContext): DataFrame = {
    System.setProperty("HADOOP_USER_NAME", "hdfs");
    val textFileTable = sqlContext.read.format("com.databricks.spark.csv")
      .option("header", "true") //
      .option("inferSchema", "true") //
      .load(file)

    // textFileTable.printSchema()
    textFileTable.registerTempTable(tablename)

    if (this.namedObjects != null) {
      this.namedObjects.update("df:" + tablename, NamedDataFrame(textFileTable, forceComputation = false, storageLevel = StorageLevel.NONE))
    }
    return textFileTable
  }

  def registermysql(file: String, tablename: String, sqlContext: SQLContext): DataFrame = {
    // $example on:jdbc_dataset$
    // Note: JDBC loading and saving can be achieved via either the load/save or jdbc methods
    // Loading data from a JDBC source
    val textFileTable = sqlContext.read 
      .format("jdbc")
      .option("url", "jdbc:mysql://localhost:3306/info")
      .option("dbtable", "info.headers")
      .option("user", "root")
      .option("password", "test1234")
      .load()
    // textFileTable.printSchema()
    textFileTable.registerTempTable(tablename)

    if (this.namedObjects != null) {
      this.namedObjects.update("df:" + tablename, NamedDataFrame(textFileTable, forceComputation = false, storageLevel = StorageLevel.NONE))
    }
    return textFileTable

  }

  def register(registerFile: DataFrame, tablename: String) {

    registerFile.registerTempTable(tablename)
    if (this.namedObjects != null) {
      this.namedObjects.update("df:" + tablename, NamedDataFrame(registerFile, forceComputation = false, storageLevel = StorageLevel.NONE))
    }

  }

  def deRegister(tablename: String, sqlContext: SQLContext): Unit = {
    try {
      val NamedDataFrame(mytableDF, _, _) = this.namedObjects.get[NamedDataFrame]("df:" + tablename).get
      sqlContext.dropTempTable(tablename)
      // this.namedObjects.update("df:" + tablename, None)
    } catch {
      case t: java.util.NoSuchElementException =>
    }
  }
  /*
   * the table must be registered
   */
  def table(sqlContext: SQLContext, tablename: String): DataFrame = {

    val NamedDataFrame(mytableDF, _, _) = this.namedObjects.get[NamedDataFrame]("df:" + tablename).get

    sqlContext.table(tablename)
  }

}
