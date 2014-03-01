//package isitdown.service.http
//
//import scala.concurrent.Future
//import java.nio.channels.AsynchronousChannelGroup
//import java.util.concurrent.Executors
//import java.net.URL
//import java.nio.channels.AsynchronousSocketChannel
//import java.nio.channels.CompletionHandler
//import scala.concurrent.Promise
//import java.nio.ByteBuffer
//import javax.net.ssl.SSLEngine
//import javax.net.ssl.SSLContext
//import scala.util.Try
//import scala.concurrent.ExecutionContext
//import java.net.StandardSocketOptions
//
//class JavaNIO2HttpClient(implicit ex: ExecutionContext) extends HttpClient{
//	
//	def query(urlString: String): Future[(HttpStatus, Body, Duration)] = {
//		val start = System.currentTimeMillis
//		
//		val url = new URL(urlString)
//		val asc = AsynchronousSocketChannel.open()
//		val promise = Promise[(HttpStatus, Body, Duration)]()
//		val secure = url.getProtocol().toLowerCase == "https" 
//		val port = if(url.getPort() < 0 ){
//			if(secure) 443
//			else 80
//		} else url.getPort
//		
//		def read(): Future[String] = {
//			val bufferSize = asc.getOption(StandardSocketOptions.SO_RCVBUF)
//			val toBuffer = ByteBuffer.allocate(bufferSize)
//			val res = Promise[String]()
//			asc.read(toBuffer, toBuffer, new CompletionHandler[Integer, ByteBuffer]{
//				override def completed(result: Integer, attachment: ByteBuffer){
//					val t = Try{
//						val s = attachment.array().take(result).map(_.toChar).mkString
//						print(s + " !!!!!! " + s.size + "--" + bufferSize + "!!!" + result + "")
//						s
//					}
//					if(t.map(i => result).getOrElse(true))
//						res.complete(t)
//					else
//						read().map(s => t.get + s)
//				}
//				
//				override def failed(throwable: Throwable, attachment: ByteBuffer){
//					throwable.printStackTrace()
//					res.failure(throwable)
//				}
//			})
//			res.future
//		}
//		
//		asc.connect(new java.net.InetSocketAddress(url.getHost(), port), "", new CompletionHandler[Void, String]{
//			override def completed(result: Void, attachment: String){
//				lazy val sslEngine = SSLContext.getDefault().createSSLEngine(url.getHost, port)
//				sslEngine.setUseClientMode(true)
//				val heads = headers(url, port)
//				val toWrite: ByteBuffer = if(secure){
//					val writeTo = ByteBuffer.allocate(asc.getOption(StandardSocketOptions.SO_SNDBUF))
//					sslEngine.wrap(ByteBuffer.wrap(heads.getBytes()), writeTo)
//					writeTo
//				} else {
//					ByteBuffer.wrap(heads.getBytes())
//				}
//				println("Writing: " + heads)
//				asc.write(toWrite, heads, new CompletionHandler[Integer, String]{
//					override def completed(result: Integer, attachment: String){
//						promise.completeWith(read().map(i => {
//							val sb = extractStatusAndBody(i)
//							(sb._1, sb._2, System.currentTimeMillis - start)
//						}))
//					}
//					override def failed(throwable: Throwable, attachment: String){
//						throwable.printStackTrace()
//						asc.close()
//						promise.failure(throwable)
//					}
//				})
//			}
//			
//			override def failed(throwable: Throwable, attachment: String){
//				asc.close()
//				promise.failure(throwable)
//			}
//		})
//		
//		promise.future
//	}
//	
//	private def headers(url: URL, port: Int): String = {
//		s"""GET ${if(url.getPath.trim.isEmpty())"/" else url.getPath()} HTTP/1.1
//Host: ${url.getHost()}:${port}\n\r\n\r
//"""
//	}
//	
//	def extractStatusAndBody(result: String): (HttpStatus, Body) = {
//		println("extractStatusAndBody")
//		val headAndBody = result.split("\n\n")
//		
//		val StatusRegExp = """.+\s(\d+)\s(.*)""".r
//		
//		println("FIRSTLINE: " + headAndBody(0).split("\n")(0))
//		
//		val StatusRegExp(code, msg) = headAndBody(0).split("\n")(0)
//		
//		
//		val body = if(headAndBody.length == 2) headAndBody(1) else ""
//		(HttpStatus(code.toInt, msg), body)
//	}
//	
//	def close(){}
//	
//}
//
//object JavaNIO2HttpClient{
//	
//	 val threadPool = AsynchronousChannelGroup.withFixedThreadPool(10, Executors.defaultThreadFactory());
//	
//}