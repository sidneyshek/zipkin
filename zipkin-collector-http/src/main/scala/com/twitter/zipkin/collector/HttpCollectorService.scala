package com.twitter.zipkin.collector

import java.net.{InetAddress, InetSocketAddress}
import java.nio.ByteBuffer

import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.path.{Root, Path}
import com.twitter.finagle.http.{Request, Http}
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.thrift.ThriftServerFramedCodec
import com.twitter.finagle.tracing.{Tracer, SpanId}
import com.twitter.finagle.{Service, Filter}
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.ServiceTracker
import com.twitter.ostrich.stats.Stats
import com.twitter.util.{Try, Duration, Future}
import com.twitter.zipkin.collector.builder.CollectorInterface
import com.twitter.zipkin.common._

import argonaut._, Argonaut._
import com.twitter.zipkin.storage.Store
import com.twitter.zipkin.thriftscala
import org.apache.thrift.protocol.TBinaryProtocol
import org.jboss.netty.handler.codec.http._

object HttpCollector {
  object Interface {
    def apply() = {
      type T = Seq[String]
      new CollectorInterface[T] {
        val filter = new HttpFilter

        def apply() = (writeQueue: WriteQueue[T], stores: Seq[Store], address: InetSocketAddress, statsReceiver: StatsReceiver, tracer: Tracer) => {
          Logger.get().info("Starting collector service on addr " + address)

          /* Start the service */
          val service = new HttpCollectorService(writeQueue, stores)
          service.start()
          ServiceTracker.register(service)

          /* Start the server */
          ServerBuilder()
            .codec(Http())
            .bindTo(address)
            .name("ZipkinCollector")
            .reportTo(statsReceiver)
            .tracer(tracer)
            .build(new Router(service))

        }
      }
    }
  }

  // Router needs to take in the service
  class Router(s: HttpCollectorService) extends Service[HttpRequest, HttpResponse] {
    import org.jboss.netty.handler.codec.http.HttpMethod.{ DELETE, GET, POST, PUT }
    import scalaz.syntax.id._
    private val log = Logger.get
    def apply(request: HttpRequest): Future[HttpResponse] = {
      val req = Request(request)
      (req.method, Path(req.path)) match {
        case (GET, Root) => Future { defaultEmptyHttpResponse(HttpResponseStatus.OK) }
        case (POST, Root) =>
          Future {
            if (s.write(req.getContentString()))
              defaultEmptyHttpResponse(HttpResponseStatus.OK)
            else {
              log.warning("Dropped message")
              defaultEmptyHttpResponse(HttpResponseStatus.OK)
            }
          }

      }
    }

    private def defaultEmptyHttpResponse(status: HttpResponseStatus): HttpResponse =
      new DefaultHttpResponse(HttpVersion.HTTP_1_1, status) <| { HttpHeaders.setContentLength(_, 0) }
  }
}

class HttpCollectorService(
  val writeQueue: WriteQueue[Seq[_ <: String]],
  val stores: Seq[Store]) extends CollectorService {

  // TODO - What do we do about the thrift methods for dealing with annotations?
  def write(s: String): Boolean =
    writeQueue.add(Seq(s))
}

class HttpFilter extends Filter[Seq[String], Unit, Span, Unit] {
  import Decoders._
  private val log = Logger.get

  def apply(logEntries: Seq[String], service: Service[Span, Unit]): Future[Unit] = {
    Future.join {
      logEntries.map { msg =>
        try {
          Stats.time("deserializeSpan") {
            msg.decodeEither[Span]
          }.fold({ e =>
            log.warning(e, "Invalid msg: %s", msg)
            Stats.incr("collector.invalid_msg")
            Future.Unit
          }, { s =>
            log.ifDebug("Processing span: " + s + " from " + msg)
            service(s)
          })
        } catch {
          case e: Exception => {
            // scribe doesn't have any ResultCode.ERROR or similar
            // let's just swallow this invalid msg
            log.warning(e, "Invalid msg: %s", msg)
            Stats.incr("collector.invalid_msg")
            Future.Unit
          }
        }
      }
    }
  }


}

object Decoders {
  implicit val SpanDecodeJson: DecodeJson[Span] =
    DecodeJson { c =>
      for {
        traceId <- c.get[Long]("trace_id")(HexEncodedLongDecodeJson)
        spanName <- c.get[String]("name")
        spanId <- c.get[Long]("span_id")(HexEncodedLongDecodeJson)
        parentSpanId <- c.get[Option[Long]]("parent_id")(OptionalHexEncodedLongDecodeJson)
        annotations <- c.get[List[Annotation]]("annotations")
        binaryAnnotations <- c.get[List[BinaryAnnotation]]("binary_annotations")
        debug <- c.get[Option[Boolean]]("debug")
      } yield Span(traceId, spanName, spanId, parentSpanId, annotations, binaryAnnotations.toSeq, debug.getOrElse(false))
    }

  lazy val HexEncodedLongDecodeJson: DecodeJson[Long] =
    DecodeJson { c =>
      for {
        str <- c.as[String]
        x <- SpanId.fromString(str).fold(DecodeResult.fail[SpanId](str, c.history))(DecodeResult.ok)
      } yield x.toLong
    }

  lazy val OptionalHexEncodedLongDecodeJson: DecodeJson[Option[Long]] =
    OptionDecodeJson[Long](HexEncodedLongDecodeJson)

  implicit val AnnotationDecodeJson: DecodeJson[Annotation] =
    DecodeJson { c =>
      for {
        timestamp <- c.get[Long]("timestamp")
        value <- c.get[String]("value")
        endpoint <- c.get[Option[Endpoint]]("endpoint")
        duration <- c.get[Option[Long]]("duration")
      } yield Annotation(timestamp, value, endpoint, duration.map { Duration.fromMilliseconds })
    }

  // TODO - expand this beyond strings
  implicit val BinaryAnnotationDecodeJson: DecodeJson[BinaryAnnotation] =
    DecodeJson { c =>
      for {
        key <- c.get[String]("key")
        value <- c.get[String]("value")
        host <- c.get[Option[Endpoint]]("host")
      } yield BinaryAnnotation(key, ByteBuffer.wrap(value.getBytes), AnnotationType.String, host)
    }

  implicit val EndpointDecodeJson: DecodeJson[Endpoint] =
    DecodeJson { c =>
      for {
        ipv4 <- c.get[String]("ipv4").flatMap { i => ipv4ToInt(i).fold(DecodeResult.fail[Int](i, c.history))(DecodeResult.ok) }
        port <- c.get[Short]("port")
        serviceName <- c.get[String]("service_name")
      } yield {
        Endpoint(ipv4, port, serviceName)
      }
    }

  private def ipv4ToInt(s: String): Option[Int] =
    Try(InetAddress.getByName(s)).map { i =>
      val bytes = i.getAddress
      bytes.zipWithIndex.foldLeft(0) { case (acc, (byte, index)) => acc + (byte << (24 - (8 * index))) }
    }.toOption
}