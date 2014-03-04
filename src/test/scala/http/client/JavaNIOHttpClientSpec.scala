package no.skytteren.http.client

import org.scalatest.FunSpec
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import akka.testkit.TestKitBase
import org.scalatest.BeforeAndAfterAll

class JavaNIO2HttpClientSpec(val system: ActorSystem) extends FunSpec with TestKitBase with BeforeAndAfterAll {

  def this() = this(ActorSystem("HttpClientSpec"))
	
  implicit val _system = system 
  
  import system._
  
  override def afterAll {
    system.shutdown()
  }
	
	describe("User Agent"){
		
		val httpClient = system.actorOf(Props(new JavaNIO2HttpClient))
		//val url = "https://kundeportal.opf.no/kontrollstasjon"
//		val url = "https://webmail.bekk.no"
//		val url = "http://isitdown.no"
		val url = "http://timily.org"
				
		implicit val timeout = Timeout(15 seconds)
		it("should save result"){
			val (status, headers, body) = Await.result((httpClient ? Query(url)).mapTo[(HttpStatus, Any, String)], 15 seconds)
			assert(status.code === 200)
			assert(body.contains("IsItDown.no 2013"))
		}
	}
	
}