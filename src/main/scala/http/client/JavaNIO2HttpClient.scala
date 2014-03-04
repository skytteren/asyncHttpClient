package no.skytteren.http.client

import scala.concurrent.Future
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.Executors
import java.net.URL
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import scala.concurrent.Promise
import java.nio.ByteBuffer
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLContext
import scala.util.Try
import scala.concurrent.ExecutionContext
import java.net.StandardSocketOptions
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ActorLogging

case class HttpStatus(code: Int, msg: String)

trait HttpClient{
	
	type Duration = Long
}

case class Query(url: String)
case class Data(msg: String)
case class Problem(throwable: Throwable)

object HttpActor{
	def props(originalSender: ActorRef): Props = Props(new HttpActor(originalSender))
}

class HttpActor(originalSender: ActorRef) extends Actor{
	type Body = String
	type Header = (String, String)
	type Headers = List[Header]
	var allData: List[String] = Nil
	var status: Option[HttpStatus] = None
	var headers: Option[Headers] = None
	var length = 0
	var body = ""
	def receive = {
		case Data(data) => 
			allData +:= data
			if(headers.isEmpty){
				val (s, h, b) = extractStatusAndBody(data)
				status = Option(s)
				headers = Option(h)
				body = b
			} else{
				body += data
			}
			for{
				s <- status 
				heads <- headers 
				length <- heads.find(_._1.trim == "Content-Length").map(_._2.toLong)
			}{
				if(body.length >= length){
					originalSender ! (s, heads, body)
					sender ! ConnectionActor.Close
				}else {
					sender ! ConnectionActor.Read
				}
			}
		case Problem(p) => originalSender ! p 
	}
	
	
	def extractStatusAndBody(result: String): (HttpStatus, Headers, Body) = {
		println("extractStatusAndBody")
		val headAndBody = result.split("\\r\\n\\r\\n")
		println("HEAD: " + headAndBody(0))
		val fullHead = headAndBody(0).split("\\r\\n")
		
		val statusLine = fullHead.head.trim
		
		println("FIRSTLINE: " + statusLine)
		val status = {
			val statusRegExp = """HTTP.{4} (\d{3}) (.*)""".r
			val statusRegExp(code, msg) = statusLine
			HttpStatus(code.toInt, msg)
		}
		println("status: " + status)
		
		val heads = fullHead.tail.map(h => {
			val hs = h.split(":")
			hs.head -> hs.tail.mkString(":").trim
		}).toList
		println("heads: " + heads)
		
		val body = if(headAndBody.length == 2) headAndBody(1) else ""
		(status, heads, body)
	}
	
}

class JavaNIO2HttpClient extends Actor with HttpClient{
	
	def receive = {
		case Query(url) => 
			query(url, sender)
	}
	
	def query(urlString: String, originalSender: ActorRef) {
		val start = System.currentTimeMillis
		
		val url = new URL(urlString)
		val asc = AsynchronousSocketChannel.open()
//		val promise = Promise[(HttpStatus, Body, Duration)]()
		val secure = url.getProtocol().toLowerCase == "https" 
		val port = if(url.getPort() < 0 ){
			if(secure) 443
			else 80
		} else url.getPort

		val dataActor = context.actorOf(HttpActor.props(originalSender))
		context.actorOf(ConnectionActor.props(dataActor, url, asc, port, secure)) ! ConnectionActor.Connect
		
	}
}

object ConnectionActor{
	case object Connect
	case object Read
	
	case object Close
	def props(dataActor: ActorRef, url: URL, asc: AsynchronousSocketChannel, port:Int, secure: Boolean): Props = 
			Props(new ConnectionActor(dataActor, url, asc, port, secure))
}

class ConnectionActor(dataActor: ActorRef, url: URL, asc: AsynchronousSocketChannel, port:Int, secure: Boolean) extends Actor with ActorLogging{
	import ConnectionActor._
	lazy val sslEngine = {
		val eng = SSLContext.getDefault().createSSLEngine(url.getHost, port)
		eng.setUseClientMode(true)
		eng
	}
	
	def receive = {
		case Read => read()
		case Connect => connect()
		case Close if !asc.isOpen => asc.close()
	}
	
	private def read(){
		
		val bufferSize = asc.getOption(StandardSocketOptions.SO_RCVBUF)
		val toBuffer = ByteBuffer.allocate(bufferSize)
		asc.read(toBuffer, toBuffer, new CompletionHandler[Integer, ByteBuffer]{
			override def completed(result: Integer, attachment: ByteBuffer){
				val s = attachment.array().take(result).map(_.toChar).mkString
				print(s + " !!!!!! " + s.size + "--" + bufferSize + "!!!" + result + "")
				dataActor ! Data(s)
			}
			
			override def failed(throwable: Throwable, attachment: ByteBuffer){
				log.warning("unable to read, url: " + url, throwable)
				
				throwable.printStackTrace()
				dataActor ! Problem(throwable)
			}
		})
	}
	
	private def connect(){
		asc.connect(new java.net.InetSocketAddress(url.getHost(), port), "", new CompletionHandler[Void, String]{
			override def completed(result: Void, attachment: String){
				val heads = headers(url, port)
				val toWrite: ByteBuffer = if(secure){
					val writeTo = ByteBuffer.allocate(asc.getOption(StandardSocketOptions.SO_SNDBUF))
					sslEngine.wrap(ByteBuffer.wrap(heads.getBytes()), writeTo)
					writeTo
				} else {
					ByteBuffer.wrap(heads.getBytes())
				}
				println("Writing: " + heads)
				asc.write(toWrite, heads, new CompletionHandler[Integer, String]{
					override def completed(result: Integer, attachment: String){
						read()
					}
					override def failed(throwable: Throwable, attachment: String){
						log.warning("unable to connect, url: " + url, throwable)
						asc.close()
						dataActor ! Problem(throwable)
					}
				})
			}
			
			override def failed(throwable: Throwable, attachment: String){
				asc.close()
				dataActor ! Problem(throwable)
			}
		})
	}
		
//		promise.future
	
	private def headers(url: URL, port: Int): String = {
		s"""GET ${if(url.getPath.trim.isEmpty())"/" else url.getPath()} HTTP/1.1
				|Host: ${url.getHost()}:${port}\n\r\n\r
				|""".stripMargin
	}
	
	
}