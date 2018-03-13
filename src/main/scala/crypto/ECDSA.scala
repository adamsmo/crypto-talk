package crypto

import akka.util.ByteString
import domain.{Address, PubKey, Signature}

object ECDSA {
  def sign(): ByteString = {
    ByteString.empty
  }

  def getAddress(sig: Signature, data: ByteString): Address = {
    Address(PubKey(42, 42))
  }
}
