package com.twitter.zipkin.redis

import java.util.concurrent.TimeUnit

import com.twitter.util.{Try, Duration}
import com.twitter.conversions.time._

case class Config(host: String,
                  port: Int,
                  ttl: Duration = 7.days,
                  authPassword: Option[String] = None)

object Config {
  // TODO - If only we had tagged types for these parameters
  def fromEnv(hostEnv: String = "ZIPKIN_REDIS_HOST", portEnv: String = "ZIPKIN_REDIS_PORT", ttlDaysEnv: String = "ZIPKIN_REDIS_TTL", authPasswordEnv: String = "ZIPKIN_REDIS_PASSWORD"): Config = {
    val redisHost = Option(System.getenv(hostEnv)).getOrElse("0.0.0.0")
    val redisPort = intOr(portEnv, 6379)
    val ttl = Duration(intOr(ttlDaysEnv, 7), TimeUnit.DAYS)
    val authPassword = Option(System.getenv(authPasswordEnv))
    Config(redisHost, redisPort, ttl, authPassword)
  }

  private def intOr(env: String, default: Int): Int =
    Option(System.getenv(env)).flatMap { ps => Try(ps.toInt).toOption }.getOrElse(default)
}