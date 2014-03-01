//package isitdown.service.http
//
//import org.scalatest.FunSpec
//import scala.concurrent.Await
//import scala.concurrent.duration._
//import scala.concurrent.ExecutionContext.Implicits.global
//
//class JavaNIO2HttpClientSpec extends FunSpec {
//
//	describe("User Agent"){
//			
//		val httpClient = new JavaNIO2HttpClient
//		//val url = "https://kundeportal.opf.no/kontrollstasjon"
//		//val url = "https://webmail.bekk.no"
//		val url = "http://isitdown.no"
//				
//		it("should save result"){
//			val (status, response,time) = Await.result(httpClient.query(url), 15 seconds)
//			assert(status.code === 200)
//			assert(response.contains("password"))
//		}
//	}
//	
//}