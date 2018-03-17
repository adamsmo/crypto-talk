package crypto

import akka.util.ByteString
import domain.{ Address, PrvKey, PubKey }
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{ FlatSpec, Matchers }

class ECDSASpec extends FlatSpec with GeneratorDrivenPropertyChecks with Matchers {
  "ECDSA" should "sign and verify signatures" in new Env {
    forAll(keys, msgs) {
      case ((prvKey, pubKey), msg) =>
        val sig = ECDSA.sign(msg, prvKey)
        val sender = ECDSA.verify(sig, msg)
        sender shouldBe Some(Address(pubKey))
    }

    forAll(keys, msgs) {
      case ((prvKey, p), msg) =>
        val sig = ECDSA.sign(msg, prvKey)
        val sender = ECDSA.verify(sig, msg ++ ByteString(42))
        sender shouldBe None
    }
  }
}

trait Env {
  val keys: Gen[(PrvKey, PubKey)] = Gen.const().map(_ => ECDSA.generateKeyPair())
  val msgs: Gen[ByteString] = Gen.listOf(Gen.numChar.map(_.toByte)).map(b => ByteString(b: _*))
}