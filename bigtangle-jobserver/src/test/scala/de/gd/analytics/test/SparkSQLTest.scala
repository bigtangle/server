package de.gd.analytics.test

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext

import com.typesafe.config.ConfigFactory

import de.gd.analytics.sparkjob.SparkSQLData
import java.text.SimpleDateFormat
import java.sql.Timestamp

object SparkSQLTest {
  def main(args: Array[String]) {
    val conf = new SparkConf().setMaster("local[4]").setAppName("aJob")
    val sc = new SparkContext(conf)
    val config = ConfigFactory.parseString("")
    System.setProperty("HADOOP_USER_NAME", "hdfs");
    val sqlContext = new SQLContext(sc)

    val getYearMonth = (date: String) => {
      val myformatString = "yyyy-MM-dd hh:mm:ss"
      val d =  getTimestamp(date, myformatString)
      val format = new SimpleDateFormat("MM-yy")
      format.format(d)
    }

    val getYearMonthDay = (date: String) => {
      val myformatString = "yyyy-MM-dd hh:mm:ss"
      val d =  getTimestamp(date, myformatString)
      val format = new SimpleDateFormat("dd.MM.yyyy")
      format.format(d)
    }
    sqlContext.udf.register("getYearMonth", getYearMonth)

    sqlContext.udf.register("getYearMonthDay", getYearMonthDay)

    val banknote = SparkSQLData.register("hdfs://quickstart.cloudera:8020/user/archiv/Poznan_Banknote/1", "banknote", sqlContext)
    val finance = SparkSQLData.register("hdfs://quickstart.cloudera:8020/user/archiv/Poland_Finance/1", "finance", sqlContext)

    val labour = SparkSQLData.register("hdfs://quickstart.cloudera:8020/user/archiv/Poland_Labour/1", "labour", sqlContext)
    val weather = SparkSQLData.register("hdfs://quickstart.cloudera:8020/user/archiv/Poznan_Weather/1", "weather", sqlContext)

    sqlContext.sql("select  *   from  banknote , weather where   "
      + "   getYearMonthDay(banknote.TimeStart) = weather.Date ").show()

    labour.printSchema()

    sqlContext.sql("select  *   from  banknote ,labour   where   "
      + "   getYearMonth(banknote.TimeStart) = labour.ECONOMIC_PROPERTY ").show()

    sqlContext.sql("select  *   from  banknote , finance,labour, weather where getYearMonth(banknote.TimeStart)=   finance.ECONOMIC_PROPERTY "
      + " and getYearMonth(banknote.TimeStart) =labour.ECONOMIC_PROPERTY and getYearMonthDay(banknote.TimeStart) = weather.Date ").show()

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
}
