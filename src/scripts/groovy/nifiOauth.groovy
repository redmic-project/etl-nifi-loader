import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.SignatureException

def flowFile = session.get()
if (!flowFile) return

def static hmac(String data, String key) throws java.security.SignatureException {
    String result
    try {
        // get an hmac_sha1 key from the raw key bytes
        SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), "HmacSHA1")
        // get an hmac_sha1 Mac instance and initialize with the signing key
        Mac mac = Mac.getInstance("HmacSHA1")
        mac.init(signingKey)
        // compute the hmac on input data bytes
        byte[] rawHmac = mac.doFinal(data.getBytes())
        result = rawHmac.encodeBase64()
    } catch (Exception e) {
        throw new SignatureException("Failed to generate HMAC : " + e.getMessage())
    }
    return result
}

def attributes = flowFile.getAttributes()

// retrieve arguments of the target and split arguments
def arguments = attributes.arguments.tokenize('&')
def method = attributes.method
def base_url = attributes.base_url
def consumerSecret = attributes.oauth_consumer_secret
def tokenSecret = attributes.oauth_token_secret

TreeMap map = [:]

for (String item : arguments) {
    def (key, value) = item.tokenize('=')
    map.put(key, value)
}

map.put("oauth_consumer_key", attributes.oauth_consumer_key)
map.put("oauth_nonce", attributes.oauth_nonce)
map.put("oauth_signature_method", attributes.oauth_signature_method)
map.put("oauth_timestamp", attributes.oauth_timestamp)
map.put("oauth_token", attributes.oauth_token)
map.put("oauth_version", attributes.oauth_version)

String.metaClass.encode = {
    java.net.URLEncoder.encode(delegate, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")
}

String parameterString = map.collect { String key, String value ->
    "${key.encode()}=${value.encode()}"
}.join("&")

String signatureBaseString = ""

signatureBaseString += method.toUpperCase()
signatureBaseString += '&'
signatureBaseString += base_url.encode()
signatureBaseString += '&'
signatureBaseString += parameterString.encode()

String signingKey = consumerSecret.encode() + '&' + tokenSecret.encode()

String oauthSignature = hmac(signatureBaseString, signingKey)

flowFile = session.putAttribute(flowFile, 'oauth_signature', oauthSignature)

String oauth = 'OAuth '
oauth += 'oauth_consumer_key="'
oauth += attributes.oauth_consumer_key.encode()
oauth += '", '
oauth += 'oauth_nonce="'
oauth += attributes.oauth_nonce.encode()
oauth += '", '
oauth += 'oauth_signature="'
oauth += oauthSignature.encode()
oauth += '", '
oauth += 'oauth_signature_method="'
oauth += attributes.oauth_signature_method.encode()
oauth += '", '
oauth += 'oauth_timestamp="'
oauth += attributes.oauth_timestamp.encode()
oauth += '", '
oauth += 'oauth_token="'
oauth += attributes.oauth_token.encode()
oauth += '", '
oauth += 'oauth_version="'
oauth += attributes.oauth_version.encode()
oauth += '"'

flowFile = session.putAttribute(flowFile, 'oauth_header', oauth)

session.transfer(flowFile, REL_SUCCESS)
