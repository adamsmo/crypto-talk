package crypto

import domain.Address
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}



class MinerPowSpec extends FlatSpec with GeneratorDrivenPropertyChecks with Matchers {
  "miner" should "calculate and verify pow" in new Env {
//    forAll(keys, msgs) {
//      case ((prvKey, pubKey), msg) =>
//        val sig = ECDSA.sign(msg, prvKey)
//        val sender = ECDSA.verify(sig, msg)
//        sender shouldBe Some(Address(pubKey))
//    }
  }
}

trait Env {

}