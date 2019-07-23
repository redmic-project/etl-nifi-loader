import geoscript.geom.Geometry
import geoscript.geom.io.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

@Grab(group='commons-io', module='commons-io', version='2.6')
import org.apache.commons.io.IOUtils
import org.apache.nifi.processor.io.StreamCallback
import java.nio.charset.StandardCharsets


BEACH_FIELD_NAMES = "beachFieldNames"
CAPITALIZE_FIELD_NAMES = "capitalizeFieldNames"
INTEGER_FIELD_NAMES = "integerFieldNames"
TRASH_REGEX_NAME = "trashRegex"

def flowFile = session.get()
if (flowFile == null) {
    return
}

flowFile = session.write(flowFile, { inputStream, outputStream ->

    def content = IOUtils.toString(inputStream, StandardCharsets.UTF_8)
    def json = new JsonSlurper().parseText(content)

    def beach_field_names = getConfigValue(flowFile, BEACH_FIELD_NAMES)
    def capitalize_field_names = getConfigValue(flowFile, CAPITALIZE_FIELD_NAMES)
    def integer_field_names = getConfigValue(flowFile, INTEGER_FIELD_NAMES)
    def trash_regex = getConfigValue(flowFile, TRASH_REGEX_NAME)

    // Procesa y transforma los datos de los sensores en base al fichero de configuración
    def beach = processBeach(json, beach_field_names, capitalize_field_names, integer_field_names)

    outputStream.write(JsonOutput.toJson(beach).toString().getBytes(StandardCharsets.UTF_8))
} as StreamCallback)

session.transfer(flowFile, REL_SUCCESS)

def getConfigValue(flowFile, attrName) {
    def value = binding.getVariable(attrName)?.evaluateAttributeExpressions(flowFile).value
    value ? parseVariable(value) : current
}

def parseVariable(value) {
    value ==~ /^\[.*\]$/ ? value.tokenize(',[]').collect{ it.replaceAll("\"", "")?.trim() } : value
}

def processBeach(json, beach_field_names = null, capitalize_field_names = null, integer_field_names = null,
                 trash_regex = ~/…|\d{2,}|PLAYA\sSIN\sNOMBRE|GESPLAN|NULL|NONE|[()]|PLAYA\s\d{2,}/ ) {
    json.geometry = convertGeoJsonToewkt(JsonOutput.toJson(json.geometry))

    // Choose name
    if (beach_field_names) {
        def names = beach_field_names.findResults {
            json.properties.containsKey(it) ? deleteTrash(json.properties.get(it), trash_regex) : null
        }
        json.properties.redmicName = names ? titlelize(names.first()) : null
    }

    // Capitalize titles
    if (capitalize_field_names) {

        capitalize_field_names.each {
            if (json.properties.get(it))
                json.properties[it] = capitalize(json.properties.get(it))
        }
    }

    // Convert to integer
    if (integer_field_names) {
        integer_field_names.each {
            if (json.properties.containsKey(it))
                json.properties[it] = convertToNumber(json.properties.get(it))
        }
    }

    qualityControl(json)
}

def convertGeoJsonToewkt(geojson) {
    Geometry geom = Geometry.fromString(geojson)
    WktWriter writer = new WktWriter()
    geom?.valid ? 'SRID=4326;' + writer.write(geom) : null
}

def deleteTrash(text, trash_regex = ~/…|\d{2,}|PLAYA\sSIN\sNOMBRE|GESPLAN|NULL|NONE|[()]|PLAYA\s\d{2,}/) {
    text?.replaceAll(trash_regex, "")?.trim() ?: null
}

def convertToNumber(text, type = "integer") {
    def result = null
    if (text instanceof Number || text?.isNumber()) {
        switch (type) {
            case "integer":
                result = text as Integer ?: null
                break
            case "long":
                result = text as Long ?: null
                break
            default:
                result = text
        }
    }
    result
}

def titlelize(text, linkers = ["de", "del", "baño", "o", "y"]) {
    def textNormalize = normalizeTitle(text)
    def tokens = textNormalize.trim().split("\\s+").collect {
        def token = null

        // Si es una preposición o conector, se queda en minúscula, en caso
        // contrario capitaliza la palabra
        if ( !linkers.contains(it.toLowerCase()) ) {
            // Detecta si hay números romanos, si es así los deja en mayúsculas
            if ( it ==~ /^(?=[MDCLXVI])M*(C[MD]|D?C*)(X[CL]|L?X*)(I[XV]|V?I*)$/ ) {
                token = it.toUpperCase()
            } else {
                token = it.toLowerCase().capitalize()
            }
        } else {
            token = it.toLowerCase()
        }

        return token
    }
    tokens.join(" ").capitalize()
}

def normalizeTitle(text) {
    // Reemplaza textos que empiezan con "LA PLAYA" por "Playa"
    // Sustituye '()' por '-' al principio
    text.replaceAll("^LA\\sPLAYA", "Playa").replaceAll("\\(", " - ").replaceAll("\\)", "")
}

def capitalize(text) {
    text.toLowerCase().capitalize()
}

def qualityControl(json) {
    json.properties.qFlag = json.properties.redmicName && json.geometry ? "1" : "4"
    json.properties.vFlag = "R"

    json
}
