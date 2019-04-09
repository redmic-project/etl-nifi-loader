import groovy.json.JsonSlurper
import groovy.json.JsonOutput
@Grab(group='commons-io', module='commons-io', version='2.6')
import org.apache.commons.io.IOUtils
import org.apache.nifi.processor.io.StreamCallback
import java.time.*
import java.nio.charset.StandardCharsets


def flowFile = session.get();
if (flowFile == null) {
	return;
}

// CONSTANTS SCRIPT
DATE_FORMAT = "yyyy-mm-dd'T'HH:mm:ss.SSS"
REAL_TIME_VFLAG = "R"
DELAYED_VFLAG = "D"
GOOD_QFLAG = "1"
PROBABLY_BAD_QFLAG = "3"
BAD_QFLAG = "4"
QC_SUFFIX = "_qc"
// properties
DATA_DEFINITION_PROPERTY = "dataDefinition"
VALUE_PROPERTY = "value"
QFLAG_PROPERTY = "qFlag"
VFLAG_PROPERTY = "vFlag"
ID_PROPERTY = "id"
DATE_PROPERTY = "date"
// config
TRANSFORM_CONFIG_PROPERTY = "transform"
DECIMAL_PLACES_CONFIG_PROPERTY = "decimalPlaces"
QC_DEPENDENCIES_CONFIG_PROPERTY = "qCDependencies"
UPPER_LIMIT_CONFIG_PROPERTY = "upperLimit"
LOWER_LIMIT_CONFIG_PROPERTY = "lowerLimit"
UPPER_TOLERANCE_CONFIG_PROPERTY = "upperTolerance"
LOWER_TOLERANCE_CONFIG_PROPERTY = "lowerTolerance"
// common
ACTIVITY_ID_PROPERTY = "activityId"


flowFile = session.write(flowFile, { inputStream, outputStream ->

	def content = IOUtils.toString(inputStream, StandardCharsets.UTF_8)
	def data = new JsonSlurper().parseText(content)

	// Obtiene el fichero de configuración en formato json
	def config = getConfigFile(flowFile)

	// Procesa y transforma los datos de los sensores en base al fichero de configuración
	processSensorData(data, config)

	outputStream.write(JsonOutput.toJson(data).toString().getBytes(StandardCharsets.UTF_8))
} as StreamCallback)

def processSensorData(data, config) {

	def sensors = data["sensors"]
	def delayedQC = data["qc"]
	def toRemove = []
	def qCFlags = [:]

	for (sensor in sensors) {

		def dataDefinition = sensor[DATA_DEFINITION_PROPERTY]
		def sensorConfig = config[dataDefinition]

		if (sensorConfig != null) {

			def value = sensor[VALUE_PROPERTY]

			// unit transform
			def transform = sensorConfig[TRANSFORM_CONFIG_PROPERTY]
			if (transform != null) {
				value = transformValue(value, transform)
			}

			// trunc
			def decimalPlaces = sensorConfig[DECIMAL_PLACES_CONFIG_PROPERTY]
			if (decimalPlaces != null) {
				value = truncValue(value, decimalPlaces)
			}

			// Set activity id from config
			sensor[ACTIVITY_ID_PROPERTY] = config[ACTIVITY_ID_PROPERTY]

			sensor[VALUE_PROPERTY] = value

			// qualityControl
			qualityControl(sensor, delayedQC, dataDefinition, value, sensorConfig)

			sensor[DATA_DEFINITION_PROPERTY] = config[dataDefinition].dataDefinition

			// Add QFlag to new struct, index by dataDefinition
			qCFlags.put(sensor[DATA_DEFINITION_PROPERTY], sensor[QFLAG_PROPERTY])

			// Generate identifier
			sensor[ID_PROPERTY] = generateIdentifier(config[ACTIVITY_ID_PROPERTY], sensor[DATA_DEFINITION_PROPERTY], sensor[DATE_PROPERTY])
		}
		else {
			toRemove.add(sensor)
		}
	}

	sensors.removeAll { it in toRemove }

	checkQCDependencies(sensors, qCFlags, config[QC_DEPENDENCIES_CONFIG_PROPERTY])
}

def getConfigFile(flowFile) {

	def attrName = "configFilePath"

	def attr = null
	// Read config from variables
	try {
		attr = binding.getVariable(attrName).evaluateAttributeExpressions(flowFile).value
	}
	catch(Exception ex) {}

	if (attr == null) // if is null, read config from attributes
		attr = flowFile.getAttribute(attrName)

	def configFile = new File(attr).getText("UTF-8")

	return new JsonSlurper().parseText(configFile)
}

def generateIdentifier(activityId, dataDefinition, dateTime) {
	def time = Date.parse(DATE_FORMAT, dateTime)
	return activityId + "-" + dataDefinition + "-" + time.getTime();
}

def transformValue(sensorValue, transform) {
	return (sensorValue * transform)
}

def truncValue(double sensorValue, decimalPlaces) {
	return sensorValue.trunc(decimalPlaces)
}

def qualityControl(sensor, delayedQC, dataDefinition, value, sensorConfig) {

	// qualityControl
	def delayedQFlag = (delayedQC != null ? delayedQC[dataDefinition + QC_SUFFIX] : null)
	if (delayedQFlag == null) { // Si no llega con control de calidad
		sensor[QFLAG_PROPERTY] = getQFlag(value, sensorConfig)
		sensor[VFLAG_PROPERTY] = REAL_TIME_VFLAG
	} else {
		sensor[VFLAG_PROPERTY] = DELAYED_VFLAG
		if (delayedQFlag == GOOD_QFLAG) { // Si el valor es considerado bueno se vuelve a pasar el control
			sensor[QFLAG_PROPERTY] = getQFlag(value, sensorConfig)
		} else { // Si el valor no es considerado bueno se respeta el control de calidad recibido
			sensor[QFLAG_PROPERTY] = delayedQFlag
		}
	}
}

def getQFlag(value, sensorConfig) {

	def upperLimit = sensorConfig[UPPER_LIMIT_CONFIG_PROPERTY]
	def lowerLimit = sensorConfig[LOWER_LIMIT_CONFIG_PROPERTY]
	def upperTolerance = sensorConfig[UPPER_TOLERANCE_CONFIG_PROPERTY]
	def lowerTolerance = sensorConfig[LOWER_TOLERANCE_CONFIG_PROPERTY]

	def qFlag = BAD_QFLAG

	if ((lowerLimit <=  value) && (value <= upperLimit)) {
		qFlag = GOOD_QFLAG
	}
	else if ((value >= lowerLimit - lowerTolerance) || ( value <= upperLimit + upperTolerance )) {
		qFlag = PROBABLY_BAD_QFLAG
	}

	return qFlag;
}


def checkQCDependencies(sensors, qCFlags, qCDependencies) {

	if (qCDependencies == null)
		return;

	for (sensor in sensors) {
		def dd = sensor[DATA_DEFINITION_PROPERTY]
		if (qCDependencies.containsKey(dd)) {

			def dependencyQflags = qCDependencies.get(dd)
			String qFlag = sensor[QFLAG_PROPERTY]

			for (dependency in dependencyQflags) {
				String dependencyQFlag = qCFlags.get(dependency)
				qFlag = getRealQFlag(qFlag, dependencyQFlag)
			}
			sensor[QFLAG_PROPERTY] = qFlag
		}
	}
}

def getRealQFlag(source, dependency) {

	if (BAD_QFLAG.equals(source) || BAD_QFLAG.equals(dependency)) {
		return BAD_QFLAG
	}
	if (PROBABLY_BAD_QFLAG.equals(source) || PROBABLY_BAD_QFLAG.equals(dependency)) {
		return PROBABLY_BAD_QFLAG
	}
	return source;
}