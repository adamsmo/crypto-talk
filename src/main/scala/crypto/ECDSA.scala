package crypto

import java.math.BigInteger
import java.security.SecureRandom

import akka.util.ByteString
import domain.{ Address, PrvKey, PubKey, Signature }
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.{ ECDomainParameters, ECKeyGenerationParameters, ECPrivateKeyParameters, ECPublicKeyParameters }
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.math.ec.ECFieldElement

import scala.language.implicitConversions

object ECDSA {

  private val ecSpec = ECNamedCurveTable.getParameterSpec("secp256k1")
  private val domainParams = new ECDomainParameters(ecSpec.getCurve, ecSpec.getG, ecSpec.getN, ecSpec.getH)
  private val random = new SecureRandom()

  def generateKeyPair(): (PrvKey, PubKey) = {
    val gen = new ECKeyPairGenerator
    val genParams = new ECKeyGenerationParameters(domainParams, random)
    gen.init(genParams)
    val pair = gen.generateKeyPair

    val prv = pair.getPrivate.asInstanceOf[ECPrivateKeyParameters]
    val pub = pair.getPublic.asInstanceOf[ECPublicKeyParameters]

    (PrvKey(prv.getD), PubKey(pub.getQ.getAffineXCoord, pub.getQ.getAffineYCoord))
  }

  def sign(data: ByteString, prvKey: PrvKey): Signature = {
    val params = new ECPrivateKeyParameters(prvKey.d, domainParams)
    val signer = new ECDSASigner()
    signer.init(true, params)

    val (r :: s :: Nil) = signer.generateSignature(SHA3.calculate(data)).toList
    //to not implement recovery just pass public key
    val pubKey = ecSpec.getG.multiply(prvKey.d.bigInteger).normalize()

    Signature(r, s, PubKey(pubKey.getAffineXCoord, pubKey.getAffineYCoord))
  }

  def verify(sig: Signature, data: ByteString): Option[Address] = {
    val pub = ecSpec.getCurve.createPoint(sig.pubKey.x, sig.pubKey.y)
    val params = new ECPublicKeyParameters(pub, domainParams)
    val signer = new ECDSASigner()
    signer.init(false, params)
    val valid = signer.verifySignature(SHA3.calculate(data), sig.r, sig.s)
    if (valid) {
      Some(Address(sig.pubKey))
    } else {
      None
    }
  }

  implicit def toBigInt(fe: ECFieldElement): BigInt = fe.toBigInteger

  implicit def toBigInteger(n: BigInt): BigInteger = n.bigInteger

  implicit def toByteArray(s: ByteString): Array[Byte] = s.toArray
}
