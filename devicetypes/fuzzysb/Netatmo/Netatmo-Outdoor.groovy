/**
 *
 *	Based on Brian Steere's original Code
 *
 */

metadata {
	definition (name: "Netatmo Outdoor Module", namespace: "scrool", author: "Stuart Buchanan, Pavol Babinčák") {
		capability "Relative Humidity Measurement"
		capability "Temperature Measurement"
		capability "Sensor"
		capability "Battery"
		capability "Refresh"
		capability "Thermostat"

		attribute "min_temp", "number"
		attribute "max_temp", "number"
		attribute "temp_trend", "string"
		attribute "lastupdate", "string"
	}



	preferences {
		input title: "Settings", description: "To change units and time format, go to the Netatmo Connect App", displayDuringSetup: false, type: "paragraph", element: "paragraph"
		input title: "Information", description: "Your Netatmo station updates the Netatmo servers approximately every 10 minutes. The Netatmo Connect app polls these servers every 5 minutes. If the time of last update is equal to or less than 10 minutes, pressing the refresh button will have no effect", displayDuringSetup: false, type: "paragraph", element: "paragraph"
        input "logEnable", "bool", title: "Enable debug logging", defaultValue: true
	}
}

// parse events into attributes
def parse(String description) {
	if (logEnable) log.debug "Parsing '${description}'"
}

def poll() {
	if (logEnable) log.debug "Polling"
	parent.poll()
}

def refresh() {
	if (logEnable) log.debug "Refreshing"
	parent.poll()
}
