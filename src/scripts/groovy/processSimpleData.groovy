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

// CONSTANTS
dateFormat = "yyyy-mm-dd'T'HH:mm:ss.SSS"
realTimeVFlag = "R"
goodQFlag = "1"
probablyBadQFlag = "3"
badQFlag = "4"

// input Data

flowFile = session.write(flowFile, { inputStream, outputStream ->

	def content = IOUtils.toString(inputStream, StandardCharsets.UTF_8)
	def inJson = new JsonSlurper().parseText(content)

	// Read config from file
	def config = getConfigFile(flowFile)

	def sensors = inJson["sensors"]
	def toRemove = []

	for (sensor in sensors) {

		def dataDefinition = sensor["dataDefinition"]
		def sensorConfig = config[dataDefinition]

		if (sensorConfig != null) {

			def value = sensor["value"]

			// unit transform
			def transform = sensorConfig["transform"]
			if (transform != null) {
				value = transformValue(value, transform)
			}

			// trunc
			def decimalPlaces = sensorConfig["decimalPlaces"]
			if (decimalPlaces != null) {
				value = truncValue(value, decimalPlaces)
			}

			// Set activity id from config
			sensor["activityId"] = config["activityId"]

			sensor["value"] = value

			// qualityControl
			sensor["qFlag"] = qualityControl(value, sensorConfig)
			sensor["vFlag"] = realTimeVFlag

			sensor["dataDefinition"] = config[dataDefinition].dataDefinition

			// Generate identifier
			sensor["id"] = generateIdentifier(config["activityId"], sensor["dataDefinition"], sensor["date"])
		}
		else {
			toRemove.add(sensor)
		}
	}

	sensors.removeAll { it in toRemove }
	outputStream.write(JsonOutput.toJson(inJson).toString().getBytes(StandardCharsets.UTF_8))
} as StreamCallback)

session.transfer(flowFile, REL_SUCCESS)

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
	def time = Date.parse(dateFormat, dateTime)
	return activityId + "-" + dataDefinition + "-" + time.getTime();
}

def transformValue(sensorValue, transform) {
	return (sensorValue * transform)
}

def truncValue(double sensorValue, decimalPlaces) {
	return sensorValue.trunc(decimalPlaces)
}

def qualityControl(value, sensorConfig) {

	def upperLimit = sensorConfig["upperLimit"]
	def lowerLimit = sensorConfig["lowerLimit"]
	def upperTolerance = sensorConfig["upperTolerance"]
	def lowerTolerance = sensorConfig["lowerTolerance"]

	def qFlag = badQFlag

	if ((lowerLimit <=  value) && (value <= upperLimit)) {
		qFlag = goodQFlag
	}
	else if ((value >= lowerLimit - lowerTolerance) || ( value <= upperLimit + upperTolerance )) {
		qFlag = probablyBadQFlag
	}

	return qFlag;
}