import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.spark.streaming._
import org.apache.spark.streaming.kafka010._
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.sql.functions.date_format
import scala.math.{sqrt, pow}
import org.apache.spark.sql.functions.{avg, col, window, stddev}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._


import scala.math.Ordering.Implicits._
import scala.math.abs
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.sql.Timestamp
import java.time.{LocalDate, LocalDateTime}
import scala.util.control._
import java.sql.Timestamp
import org.apache.spark.sql.functions.udf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row}
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.Duration
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._

import scala.collection.mutable

object Main {
  def main(args: Array[String]): Unit = {
    Logger.getLogger("org").setLevel(Level.ERROR)
    val spark = SparkSession.builder
      .appName("Spark Kafka Example")
      .master("local[*]")
      .config("spark.sql.codegen.hugeMethodLimit", "32000")
      .config("spark.ui.port", "5050")
      .config("spark.sql.codegen.wholeStage", false)
      .config("spark.sql.legacy.timeParserPolicy", "LEGACY")
      .getOrCreate()
    spark.sparkContext.setLogLevel("INFO")
    import spark.implicits._

    // Your Kafka code goes here
    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> "localhost:9092",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "spark-kafka-example",
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )

    val topics = Array("topic_test1")
    val streamingContext = new StreamingContext(spark.sparkContext, Seconds(5))
    streamingContext.checkpoint("checkpoint")
    streamingContext.sparkContext.setLogLevel("DEBUG")
    val kafkaStream = KafkaUtils.createDirectStream[String, String](
      streamingContext,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](topics, kafkaParams)
    )

    val schema = StructType(Array(
      StructField("station", StringType, true),
      StructField("valid", StringType, true),
      StructField("tmpf", StringType, true),
      StructField("dwpf", StringType, true),
      StructField("relh", StringType, true),
      StructField("feel", StringType, true),
      StructField("drct", StringType, true),
      StructField("sped", StringType, true),
      StructField("alti", StringType, true),
      StructField("mslp", StringType, true),
      StructField("p01m", StringType, true),
      StructField("vsby", StringType, true),
      StructField("skyc1", StringType, true),
      StructField("skyl1", StringType, true),
      StructField("wxcodes", StringType, true),
      StructField("ice_acceretion_1hr", StringType, true)
    ))

    def updateFunction(newValues: Seq[(Double, Timestamp, Timestamp)], runningCount: Option[List[(Double, Timestamp, Timestamp)]])
    : Option[List[(Double, Timestamp, Timestamp)]] = {
      val newList = runningCount.getOrElse(List[(Double, Timestamp, Timestamp)]()) ++ newValues
      Some(newList)
    }

    // feature Engineering and preprocessing starts here:

    def featureEngineering(df_formatted: DataFrame): DataFrame = {

      //      Missing value imputation
      val df_new = df_formatted
        .withColumn("mslp", when(col("mslp") === -1, 0).otherwise(col("mslp")))

      val df = df_new
        .withColumn("skyl1", when(col("skyl1") === 'M', 0).otherwise(col("skyl1")))

      val df_wxcodes = df
        .withColumn("wxcodes", when(col("wxcodes") === 'M', 88).otherwise(col("wxcodes")))

      val df_iA = df_wxcodes
        .withColumn("ice_acceretion_1hr", when(col("ice_acceretion_1hr") === 'M', 0).otherwise(col("ice_acceretion_1hr")))

      val df_skyc1 = df_iA
        .withColumn("skyc1", when(col("skyc1") === 'M', "CLR").otherwise(col("skyc1")))

      //    Label Encoding
      val df_skyc1_encoded1 = df_skyc1.withColumn("skyc1", when(col("skyc1") === "CLR", 0).otherwise(col("skyc1")))
      val df_skyc1_encoded2 = df_skyc1_encoded1.withColumn("skyc1", when(col("skyc1") === "VV", 1).otherwise(col("skyc1")))
      val df_skyc1_encoded3 = df_skyc1_encoded2.withColumn("skyc1", when(col("skyc1") === "SCT", 2).otherwise(col("skyc1")))
      val df_skyc1_encoded4 = df_skyc1_encoded3.withColumn("skyc1", when(col("skyc1") === "FEW", 3).otherwise(col("skyc1")))
      val df_skyc1_encoded5 = df_skyc1_encoded4.withColumn("skyc1", when(col("skyc1") === "OVC", 4).otherwise(col("skyc1")))
      val df_skyc1_encoded6 = df_skyc1_encoded5.withColumn("skyc1", when(col("skyc1") === "BKN", 5).otherwise(col("skyc1")))

      val dfse1 = df_skyc1_encoded6.withColumn("station", when(col("station") === "CDA", 0).otherwise(col("station")))
      val dfse2 = dfse1.withColumn("station", when(col("station") === "EFK", 1).otherwise(col("station")))
      val dfse3 = dfse2.withColumn("station", when(col("station") === "FSO", 2).otherwise(col("station")))
      val dfse4 = dfse3.withColumn("station", when(col("station") === "MVL", 3).otherwise(col("station")))
      val dfse5 = dfse4.withColumn("station", when(col("station") === "RUT", 4).otherwise(col("station")))
      val dfse6 = dfse5.withColumn("station", when(col("station") === "MPV", 5).otherwise(col("station")))
      val dfse7 = dfse6.withColumn("station", when(col("station") === "VSF", 6).otherwise(col("station")))
      val dfse8 = dfse7.withColumn("station", when(col("station") === "DDH", 7).otherwise(col("station")))
      val dfse9 = dfse8.withColumn("station", when(col("station") === "1V4", 8).otherwise(col("station")))
      val dfse10 = dfse9.withColumn("station", when(col("station") === "BTV", 9).otherwise(col("station")))
      var dfse11 = dfse10.withColumn("station", when(col("station") === "6B0", 10).otherwise(col("station")))

      // Normalization

      dfse11 = dfse11.withColumn("tmpf", col("tmpf") / 100)
      dfse11 = dfse11.withColumn("dwpf", col("dwpf") / 100)
      dfse11 = dfse11.withColumn("relh", col("relh") / 100)
      dfse11 = dfse11.withColumn("drct", col("drct") / 100)
      dfse11 = dfse11.withColumn("sped", col("sped") / 100)
      dfse11 = dfse11.withColumn("feel", col("feel") / 100)
      dfse11 = dfse11.withColumn("alti", col("alti") / 100)
      dfse11 = dfse11.withColumn("vsby", col("vsby") / 100)
      dfse11 = dfse11.withColumn("skyl1", col("skyl1") / 100)
      dfse11 = dfse11.withColumn("mslp", col("mslp") / 1000)

//      dfse11.show()

      return dfse11
    }


//    def two_window_old(): DStream[((String, Timestamp), (Double, Timestamp, Timestamp))] = {
//      val windowSize = 7200 // 2 hours in seconds
//      kafkaStream.map(record => record.value)
//        .transform { rdd =>
//          val df_parsed = rdd.toDF("value")
//            .select(from_json($"value", schema).as("data"))
//            .select(
//              date_format($"data.valid", "yyyy-MM-dd HH:mm:ss").as("valid"), // <-- example format
//              $"data.station",
//              $"data.tmpf",
//              $"data.dwpf",
//              $"data.relh",
//              $"data.feel",
//              $"data.drct",
//              $"data.sped",
//              $"data.alti",
//              $"data.mslp",
//              $"data.p01m",
//              $"data.vsby",
//              $"data.skyc1",
//              $"data.skyl1",
//              $"data.wxcodes",
//              $"data.ice_acceretion_1hr")
//
//          val df_formatted = df_parsed
//            .withColumn("valid", to_timestamp($"valid", "yyyy-MM-dd HH:mm:ss"))
//            .withColumn("tmpf", $"tmpf".cast("double"))
//
//          val operatingDF = featureEngineering(df_formatted)
//          operatingDF.rdd.map(row => {
//            val validTimestamp = row.getAs[Timestamp]("valid")
//            val window_start = Math.floor(validTimestamp.getTime.toDouble / (windowSize * 1000)).toLong * windowSize * 1000
//            val window_start_timestamp = if (validTimestamp.getTime >= Timestamp.valueOf("2013-01-01 00:00:00").getTime && validTimestamp.getTime < Timestamp.valueOf("2013-01-08 00:00:00").getTime) {
//              Timestamp.valueOf("2013-01-01 00:00:00")
//            } else {
//              new Timestamp(window_start)
//            }
//
//            val prevWeekStart = if (validTimestamp.getTime < Timestamp.valueOf("2013-01-08 00:00:00").getTime) {
//              Timestamp.valueOf("2013-01-01 00:00:00")
//            } else {
//              new Timestamp(validTimestamp.getTime - (7 * 24 * 60 * 60 * 1000))
//            }
//            val prevWeekEnd = if (validTimestamp.getTime < Timestamp.valueOf("2013-01-02 00:00:00").getTime) {
//              Timestamp.valueOf("2013-01-01 00:00:00")
//            } else {
//              new Timestamp(validTimestamp.getTime - (1 * 24 * 60 * 60 * 1000))
//            }
//            ((row.getAs[String]("station"), window_start_timestamp),
//              (row.getAs[Double]("tmpf"),
//                prevWeekStart,
//                prevWeekEnd
//              ))
//          })
//        }
//        .updateStateByKey(updateFunction)
//        .transform { rdd =>
//          rdd.flatMap { case ((station, window_start_timestamp), temp_list) =>
//            temp_list.map { case (temp, prevWeekStart, prevWeekEnd) =>
//              ((station, window_start_timestamp), (temp, prevWeekStart, prevWeekEnd))
//            }
//          }
//        }
//    }

        def two_window(): DStream[((String, Timestamp), (Double, Timestamp, Timestamp))] = {
          val windowSize = 2 * 60 * 60 // 2 hours in seconds
          val slideInterval = 15 * 60 // 15 minutes in seconds
          kafkaStream.map(record => record.value)
            .transform { rdd =>
              val df_parsed = rdd.toDF("value")
                .select(from_json($"value", schema).as("data"))
                .select(
                  date_format($"data.valid", "yyyy-MM-dd HH:mm:ss").as("valid"), // <-- example format
                  $"data.station",
                  $"data.tmpf",
                  $"data.dwpf",
                  $"data.relh",
                  $"data.feel",
                  $"data.drct",
                  $"data.sped",
                  $"data.alti",
                  $"data.mslp",
                  $"data.p01m",
                  $"data.vsby",
                  $"data.skyc1",
                  $"data.skyl1",
                  $"data.wxcodes",
                  $"data.ice_acceretion_1hr")

              val df_formatted = df_parsed
                .withColumn("valid", to_timestamp($"valid", "yyyy-MM-dd HH:mm:ss"))
                .withColumn("tmpf", $"tmpf".cast("double"))

              val operatingDF = featureEngineering(df_formatted)
              val windowSpec = Window.partitionBy("station")
                .orderBy($"valid".cast("timestamp").cast("long"))
                .rangeBetween(-windowSize, 0)

              val df_windowed = operatingDF
                .withColumn("window_start", $"valid" - expr(s"INTERVAL $windowSize seconds"))
                .withColumn("previous_week_start", when(
                  $"valid" < to_timestamp(lit("2013-01-08 00:00:00")),
                  to_timestamp(lit("2013-01-01 00:00:00"))
                ).otherwise($"valid" - expr(s"INTERVAL 7 days")))
                .withColumn("previous_week_end", when(
                  $"valid" < to_timestamp(lit("2013-01-02 00:00:00")),
                  $"valid" - expr(s"INTERVAL 15 minutes")
                ).otherwise($"valid" - expr(s"INTERVAL 1 days")))
                .filter(unix_timestamp($"valid") % slideInterval === 0)
                .withColumn("temps", collect_list($"tmpf").over(windowSpec))
                .select("station", "window_start", "temps", "previous_week_start", "previous_week_end")


                .rdd
                .flatMap(row => {
                  val station = row.getAs[String]("station")
                  val window_start = row.getAs[Timestamp]("window_start")
                  val prevWeekStart = row.getAs[Timestamp]("previous_week_start")
                  val prevWeekEnd = row.getAs[Timestamp]("previous_week_end")
                  row.getAs[mutable.WrappedArray[Double]]("temps").toSeq.map(temp => (
                    (station, window_start),
                    (temp, prevWeekStart, prevWeekEnd)
                  ))
                })

              df_windowed

            }
            .updateStateByKey(updateFunction)
            .transform { rdd =>
              rdd.flatMap { case ((station, window_start_timestamp), temp_list) =>
                temp_list.map { case (temp, prevWeekStart, prevWeekEnd) =>
                  ((station, window_start_timestamp), (temp, prevWeekStart, prevWeekEnd))
                }
              }
            }
        }


    def seven_window(): DStream[((String, Timestamp, Timestamp), (Double, Double))] = {
      kafkaStream.map(record => record.value)
        .transform(rdd => {
          val df_parsed = rdd.toDF("value")
            .select(from_json($"value", schema).as("data"))
            .select(
              date_format($"data.valid", "yyyy-MM-dd HH:mm:ss").as("valid"),
              $"data.station",
              $"data.tmpf",
              $"data.dwpf",
              $"data.relh",
              $"data.feel",
              $"data.drct",
              $"data.sped",
              $"data.alti",
              $"data.mslp",
              $"data.p01m",
              $"data.vsby",
              $"data.skyc1",
              $"data.skyl1",
              $"data.wxcodes",
              $"data.ice_acceretion_1hr")

          val df_formatted = df_parsed
            .withColumn("valid", to_timestamp($"valid", "yyyy-MM-dd HH:mm:ss"))
            .withColumn("tmpf", $"tmpf".cast("double")) //

          val operatingDF = featureEngineering(df_formatted)

          val windowSize = "7 days"
          val slideInterval = "1 day"

          val windowSpec = org.apache.spark.sql.expressions.Window.partitionBy("station")
            .orderBy($"valid")
            .rangeBetween(-(86400 * 7), 0)


          val df_windowed = operatingDF
            .withColumn("window", org.apache.spark.sql.functions.window($"valid", windowSize, slideInterval))
            .groupBy("station", "window")
            .agg(avg("tmpf").alias("avg_temp"), stddev("tmpf").alias("stddev_temp"))
            .withColumn("windowStart", $"window.start")
            .withColumn("windowEnd", $"window.end")
            .drop("window")
            .rdd
            .map(row => (
              (row.getAs[String]("station"), row.getAs[Timestamp]("windowStart"), row.getAs[Timestamp]("windowEnd")),
              (row.getAs[Double]("avg_temp"), row.getAs[Double]("stddev_temp"))
            ))


          df_windowed
        })
    }


    def calculateAvgStdDev(temps: Seq[Double]): (Double, Double) = {
      val n = temps.length
      if (n < 2) {
        // if there is only one element, return its value as both the average and standard deviation
        (temps.headOption.getOrElse(0.0), 0.0)
      } else {
        val avg = temps.sum / n
        val stdDev = sqrt(temps.map(x => pow(x - avg, 2)).sum / (n - 1))
        (avg, stdDev)
      }
    }

    val seven_window_rdd = seven_window()

    val transformed_seven_RDD = seven_window_rdd.transform { rdd =>
      rdd.map {
        case ((station, seven_windowStart, valid), (avgTemp, stddevTemp)) =>
          (station, (valid, seven_windowStart, avgTemp, stddevTemp))
      }
    }

    transformed_seven_RDD.foreachRDD { rdd =>
      println("New seven_window_batch")
      rdd.take(100).foreach(println) // Print the first 10 elements
    }


    val two_window_rdd=two_window()
    val transformed_two_RDD = two_window_rdd.transform { rdd =>
      rdd.map {
        case ((station, two_window_start), (temp, prevWeekStart, prevWeekEnd)) =>
          (station, (two_window_start, temp, prevWeekStart, prevWeekEnd))
      }
    }
    //
    //    // Now you can perform any operation on the transformedRDD, for example, print the first 10 elements:
    ////    transformed_two_RDD.print(10)
    //
    transformed_two_RDD.foreachRDD { rdd =>
      println("New two_window_batch")
      rdd.take(10).foreach(println) // Print the first 10 elements
    }
    //
//    val joinedRDD = transformed_two_RDD.join(transformed_seven_RDD)
    val joinedRDD = transformed_two_RDD.join(transformed_seven_RDD)
      .map { case (key, (value1, value2)) => ((key, value1, value2), 1) } // add a dummy value of 1 to each tuple
      .reduceByKey(_ + _) // reduce by key to remove duplicates
      .map { case ((key, value1, value2), _) => (key, (value1, value2)) } // remove the dummy value from each tuple


    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    val filteredRDD = joinedRDD.filter {
      case (_, ((two_window_start, _, prevWeekStart, prevWeekEnd), (valid, seven_windowStart, _, _))) =>
        val seven_windowStart_date = LocalDateTime.ofInstant(seven_windowStart.toInstant, ZoneId.systemDefault()).toLocalDate()
        val prevWeekStart_date = LocalDateTime.ofInstant(prevWeekStart.toInstant, ZoneId.systemDefault()).toLocalDate()
        val prevWeekEnd_date = LocalDateTime.ofInstant(prevWeekEnd.toInstant, ZoneId.systemDefault()).toLocalDate()
        val seven_windowEnd_date = seven_windowStart_date.plusDays(7)
        //      22
        seven_windowStart_date.isEqual(prevWeekStart_date)
    }
//
//
      .map {
        case (station, ((twoWinStart, temp, twoWinPrevWeekStart, twoWinPrevWeekEnd), (valid, sevenWinStart, avgTemp, stddevTemp))) =>
          (station, twoWinStart, temp, twoWinPrevWeekStart, twoWinPrevWeekEnd, sevenWinStart, avgTemp, stddevTemp)
      }
//
//
    filteredRDD.foreachRDD { rdd =>
      println("New filteredRDD batch:")
      rdd.take(10).foreach(println)
    }

    val filteredRDDWithZscore = filteredRDD.map {
      case (station,twoWinStart, temp, twoWinPrevWeekStart, twoWinPrevWeekEnd, sevenWinStart, avgTemp, stddevTemp) =>
        val zScore = (temp - avgTemp) / stddevTemp
        (station, twoWinStart, temp, twoWinPrevWeekStart, twoWinPrevWeekEnd, sevenWinStart, avgTemp, stddevTemp, zScore)
    }

    filteredRDDWithZscore.foreachRDD { rdd =>
      println("New filteredRDDWithZscore batch:")
      rdd.collect().take(10).foreach(println)
    }
    val filteredZscoreRDD = filteredRDDWithZscore.filter {
      case (_, _, _, _, _, _, _, _, zScore) => !zScore.isNaN && !zScore.isInfinite
    }.map {
      case (station, twoWinStart, temp, twoWinPrevWeekStart, twoWinPrevWeekEnd, sevenWinStart, avgTemp, stddevTemp, zScore) =>
        (station, twoWinStart, temp, twoWinPrevWeekStart, twoWinPrevWeekEnd, sevenWinStart, avgTemp, stddevTemp, zScore)
    }


    filteredZscoreRDD.foreachRDD { rdd =>
      println("New filteredZscore batch:")
      rdd.take(10) // Print the first 10 elements
    }

    streamingContext.start()
    streamingContext.awaitTermination()
  }
}
