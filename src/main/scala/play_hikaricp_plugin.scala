package com.autma.play.hikari

import play.api.db.DBPlugin
import play.api.db.DBApi
import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }
import play.api.{ Application, Configuration }

import java.sql.{ Driver, DriverManager }
import javax.sql.DataSource

class HikariCPPlugin(app: Application) extends DBPlugin {
  lazy val config_db = app.configuration.getConfig("db").getOrElse(Configuration.empty)

  private lazy val hikari_db_api: DBApi = new HikariConnectionPoolApi(config_db, app.classloader)

  override def enabled = true

  def api: DBApi = hikari_db_api

  override def onStart() {
    // Try to connect to each, this should be the first access to dbApi
    api.datasources.map { ds =>
      try {
        ds._1.getConnection.close()
      } catch {
        case t: Throwable => {
          throw config_db.reportError(ds._2 + ".url", "Cannot connect to database [" + ds._2 + "]", Some(t.getCause))
        }
      }
    }
  }

  override def onStop() {
    api.datasources.foreach {
      case (ds, _) => try {
        api.shutdownPool(ds)
      } catch { case t: Throwable => }
    }
    val drivers = DriverManager.getDrivers()
    while (drivers.hasMoreElements) {
      val driver = drivers.nextElement
      DriverManager.deregisterDriver(driver)
    }
  }

}

object PimpedPlayConfiguration {
  implicit class ConfigurationPimp(val self: Configuration) extends AnyVal {
    def getStringOrReportError(key: String, error_msg: String = "missing configuration %s"): String = {
      self.getString(key).fold[String]
      { throw self.reportError(key, error_msg.format(key)) }
      { _ }
    }

    def getConfigOrReportError(key: String, error_msg: String = "missing configuration %s"): Configuration = {
      self.getConfig(key).fold[Configuration]
      { throw self.reportError(key, error_msg.format(key)) }
      { _ }
    }
  }
}

private class HikariConnectionPoolApi(config: Configuration, classloader: ClassLoader) extends DBApi {
  import PimpedPlayConfiguration.ConfigurationPimp

  lazy val datasource_configs = config.subKeys.map {
    ds_name => ds_name -> config.getConfigOrReportError(ds_name)
  }

  val datasources: List[(DataSource, String)] = datasource_configs.map { case (ds_name, ds_config) =>
    val driver = ds_config.getStringOrReportError("driver")

    val hikari = new HikariConfig
    register_driver(driver, ds_config)
    hikari.addDataSourceProperty("url", ds_config.getStringOrReportError("url"))
    ds_config.getString("user").foreach(hikari.addDataSourceProperty("user", _))
    ds_config.getString("username").foreach(hikari.addDataSourceProperty("user", _))
    ds_config.getString("pass").foreach(hikari.addDataSourceProperty("password", _))
    ds_config.getString("password").foreach(hikari.addDataSourceProperty("password", _))
    ds_config.getString("datasource").foreach(hikari.setDataSourceClassName)
    ds_config.getInt("maxPoolSize").foreach(hikari.setMaximumPoolSize)
    ds_config.getInt("minPoolSize").foreach(hikari.setMinimumPoolSize)
    ds_config.getInt("acquireIncrement").foreach(hikari.setAcquireIncrement)
    ds_config.getInt("acquireRetryAttempts").foreach(hikari.setAcquireRetries)
    ds_config.getMilliseconds("acquireRetryDelay").foreach(v => hikari.setAcquireRetryDelay(v.toInt))
    ds_config.getMilliseconds("maxIdleTime").foreach(v => hikari.setIdleTimeout((v / 1000L).toInt))
    ds_config.getMilliseconds("maxConnectionAge").foreach(v => hikari.setMaxLifetime((v / 1000L).toInt))

    new HikariDataSource(hikari) -> ds_name
  }.toList

  def shutdownPool(ds: DataSource) = {
    ds match {
      case ds: HikariDataSource => ds.shutdown()
    }
  }

  def getDataSource(name: String): DataSource = {
    datasources.find(_._2 == name).map(e => e._1).getOrElse(error(" - could not find datasource for " + name))
  }

  private def register_driver(driver: String, c: Configuration): Unit = {
    try {
      DriverManager.registerDriver(new play.utils.ProxyDriver(Class.forName(driver, true, classloader).newInstance.asInstanceOf[Driver]))
    } catch {
      case t: Throwable => throw c.reportError("driver", "Driver not found: [" + driver + "]", Some(t))
    }
  }
}