from Crypto.Cipher import AES
import hashlib
import base64
import argparse
from Crypto.Util.Padding import pad

#https://github.com/jiru/kakaodecrypt
#removed unnecessary codes and added encrypt

#update BOT_USER_ID
BOT_USER_ID = 326419021


class KakaoDecrypt:
  key_cache = {}

  # Reimplementation of com.kakao.talk.dream.Projector.incept() from libdream.so
  @staticmethod
  def incept(n):
    dict1 = ['adrp.ldrsh.ldnp', 'ldpsw', 'umax', 'stnp.rsubhn', 'sqdmlsl', 'uqrshl.csel', 'sqshlu', 'umin.usubl.umlsl', 'cbnz.adds', 'tbnz',
             'usubl2', 'stxr', 'sbfx', 'strh', 'stxrb.adcs', 'stxrh', 'ands.urhadd', 'subs', 'sbcs', 'fnmadd.ldxrb.saddl',
             'stur', 'ldrsb', 'strb', 'prfm', 'ubfiz', 'ldrsw.madd.msub.sturb.ldursb', 'ldrb', 'b.eq', 'ldur.sbfiz', 'extr',
             'fmadd', 'uqadd', 'sshr.uzp1.sttrb', 'umlsl2', 'rsubhn2.ldrh.uqsub', 'uqshl', 'uabd', 'ursra', 'usubw', 'uaddl2',
             'b.gt', 'b.lt', 'sqshl', 'bics', 'smin.ubfx', 'smlsl2', 'uabdl2', 'zip2.ssubw2', 'ccmp', 'sqdmlal',
             'b.al', 'smax.ldurh.uhsub', 'fcvtxn2', 'b.pl']
    dict2 = ['saddl', 'urhadd', 'ubfiz.sqdmlsl.tbnz.stnp', 'smin', 'strh', 'ccmp', 'usubl', 'umlsl', 'uzp1', 'sbfx',
             'b.eq', 'zip2.prfm.strb', 'msub', 'b.pl', 'csel', 'stxrh.ldxrb', 'uqrshl.ldrh', 'cbnz', 'ursra', 'sshr.ubfx.ldur.ldnp',
             'fcvtxn2', 'usubl2', 'uaddl2', 'b.al', 'ssubw2', 'umax', 'b.lt', 'adrp.sturb', 'extr', 'uqshl',
             'smax', 'uqsub.sqshlu', 'ands', 'madd', 'umin', 'b.gt', 'uabdl2', 'ldrsb.ldpsw.rsubhn', 'uqadd', 'sttrb',
             'stxr', 'adds', 'rsubhn2.umlsl2', 'sbcs.fmadd', 'usubw', 'sqshl', 'stur.ldrsh.smlsl2', 'ldrsw', 'fnmadd', 'stxrb.sbfiz',
             'adcs', 'bics.ldrb', 'l1ursb', 'subs.uhsub', 'ldurh', 'uabd', 'sqdmlal']
    word1 = dict1[  n     % len(dict1) ]
    word2 = dict2[ (n+31) % len(dict2) ]
    return word1 + '.' + word2

  @staticmethod
  def genSalt(user_id, encType):
    if user_id <= 0:
      return b'\0'*16

    prefixes = ['','','12','24','18','30','36','12','48','7','35','40','17','23','29',
                'isabel','kale','sulli','van','merry','kyle','james', 'maddux',
                'tony', 'hayden', 'paul', 'elijah', 'dorothy', 'sally', 'bran',
                KakaoDecrypt.incept(830819)]
    try:
      salt = prefixes[encType] + str(user_id)
      salt = salt[0:16]
    except IndexError:
      raise ValueError('Unsupported encoding type %i' % encType)
    salt = salt + '\0' * (16 - len(salt))
    return salt.encode('UTF-8')

  @staticmethod
  def pkcs16adjust(a, aOff, b):
     x = (b[len(b) - 1] & 0xff) + (a[aOff + len(b) - 1] & 0xff) + 1
     a[aOff + len(b) - 1] = x % 256
     x = x >> 8;
     for i in range(len(b)-2, -1, -1):
       x = x + (b[i] & 0xff) + (a[aOff + i] & 0xff)
       a[aOff + i] = x % 256
       x = x >> 8

  # PKCS12 key derivation as implemented in Bouncy Castle (using SHA1).
  # See org/bouncycastle/crypto/generators/PKCS12ParametersGenerator.java.
  @staticmethod
  def deriveKey(password, salt, iterations, dkeySize):
    password = (password + b'\0').decode('ascii').encode('utf-16-be')

    hasher = hashlib.sha1()
    v = hasher.block_size
    u = hasher.digest_size

    D = [ 1 ] * v
    S = [ 0 ] * v * int((len(salt) + v - 1) / v)
    for i in range(0, len(S)):
      S[i] = salt[i % len(salt)]
    P = [ 0 ] * v * int((len(password) + v - 1) / v)
    for i in range(0, len(P)):
      P[i] = password[i % len(password)]

    I = S + P

    B = [ 0 ] * v
    c = int((dkeySize + u - 1) / u)

    dKey = [0] * dkeySize
    for i in range(1, c+1):
      hasher = hashlib.sha1()
      hasher.update(bytes(D))
      hasher.update(bytes(I))
      A = hasher.digest()

      for j in range(1, iterations):
        hasher = hashlib.sha1()
        hasher.update(A)
        A = hasher.digest()

      A = list(A)
      for j in range(0, len(B)):
        B[j] = A[j % len(A)]

      for j in range(0, int(len(I)/v)):
        KakaoDecrypt.pkcs16adjust(I, j * v, B)

      start = (i - 1) * u
      if i == c:
        dKey[start : dkeySize] = A[0 : dkeySize-start]
      else:
        dKey[start : start+len(A)] = A[0 : len(A)]

    return bytes(dKey)

  @staticmethod
  def decrypt(encType, b64_ciphertext, user_id=BOT_USER_ID):
    key = b'\x16\x08\x09\x6f\x02\x17\x2b\x08\x21\x21\x0a\x10\x03\x03\x07\x06'
    iv = b'\x0f\x08\x01\x00\x19\x47\x25\xdc\x15\xf5\x17\xe0\xe1\x15\x0c\x35'

    salt = KakaoDecrypt.genSalt(user_id, encType)
    #print(salt)
    if salt in KakaoDecrypt.key_cache:
      key = KakaoDecrypt.key_cache[salt]
    else:
      key = KakaoDecrypt.deriveKey(key, salt, 2, 32)
      KakaoDecrypt.key_cache[salt] = key
    encoder = AES.new(key, AES.MODE_CBC, iv)

    ciphertext = base64.b64decode(b64_ciphertext)
    #print(ciphertext)
    if len(ciphertext) == 0:
      return b64_ciphertext
    padded = encoder.decrypt(ciphertext)
    try:
      plaintext = padded[:-padded[-1]]
      #print(plaintext.decode('UTF-8'))
    except IndexError:
      raise ValueError('Unable to decrypt data', ciphertext)
    try:
      return plaintext.decode('UTF-8')
    except UnicodeDecodeError:
      return plaintext

  @staticmethod
  def encrypt(encType, plaintext, user_id=BOT_USER_ID):
    key = b'\x16\x08\x09\x6f\x02\x17\x2b\x08\x21\x21\x0a\x10\x03\x03\x07\x06'
    iv = b'\x0f\x08\x01\x00\x19\x47\x25\xdc\x15\xf5\x17\xe0\xe1\x15\x0c\x35'
    salt = KakaoDecrypt.genSalt(user_id, encType)
    if salt in KakaoDecrypt.key_cache:
      key = KakaoDecrypt.key_cache[salt]
    else:
      key = KakaoDecrypt.deriveKey(key, salt, 2, 32)
      KakaoDecrypt.key_cache[salt] = key
    encoder = AES.new(key, AES.MODE_CBC, iv)
    ciphertext = encoder.encrypt(pad(plaintext.encode('utf-8'),encoder.block_size))
    b64_ciphertext = base64.b64encode(ciphertext)
    return b64_ciphertext
