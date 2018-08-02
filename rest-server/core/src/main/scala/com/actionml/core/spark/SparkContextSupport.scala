/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * ActionML licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actionml.core.spark

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.validate._
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.{SparkConf, SparkContext}
import org.json4s._
import org.json4s.jackson.JsonMethods._


trait SparkContextSupport extends LazyLogging {
  import com.actionml.core.spark.SparkContextSupport._

  def createSparkContext(config: String): Validated[ValidateError, SparkContext] = {
    val configMap = parseAndValidate[Map[String, String]](config, transform = _ \ "sparkConf")
    (for {
      master <- configMap.get("master")
      appName <- configMap.get("appName")
      db <- configMap.get("mongo.database")
      collection <- configMap.get("mongo.collection")
    } yield SparkConfig(master, appName, db, collection, configMap -- Seq("master", "appName", "database", "collection")))
      .map(Valid(_))
      .getOrElse(Invalid(ParseError("Wrong format at sparkConfg field")))
  }.andThen { sparkConfig =>
    try {
      val conf = new SparkConf()
        .setMaster(sparkConfig.master)
        .setAppName(sparkConfig.appName)
      conf.set("deploy-mode", "cluster")
      conf.set("spark.mongodb.input.uri", "mongodb://localhost/")
      conf.set("spark.mongodb.input.database", sparkConfig.database)
      conf.set("spark.mongodb.input.collection", sparkConfig.collection)
      conf.setAll(sparkConfig.properties)
      Valid(new SparkContext(conf))
    } catch {
      case e: Exception =>
        logger.error("Can't create spark context", e)
        Invalid(ValidRequestExecutionError("Can't create spark context"))
    }
  }

  private def parseAndValidate[T](jsonStr: String, transform: JValue => JValue = a => a)(implicit mf: Manifest[T]): T = {
    implicit val _ = org.json4s.DefaultFormats
    transform(parse(jsonStr)).extract[T]
  }
}

object SparkContextSupport {
  private case class SparkConfig(master: String, appName: String, database: String, collection: String, properties: Map[String, String])
}
