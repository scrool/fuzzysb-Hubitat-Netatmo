/**
 * Netatmo Connect
 */

import java.text.DecimalFormat
import groovy.json.JsonSlurper

private getApiUrl()			{ "https://api.netatmo.com" }
private getVendorName()		{ "netatmo" }
private getVendorAuthPath()	{ "/oauth2/authorize" }
private getVendorTokenPath(){ "${apiUrl}/oauth2/token" }
private getVendorIcon()		{ "https://s3.amazonaws.com/smartapp-icons/Partner/netamo-icon-1%402x.png" }
private getClientId()		{ settings.clientId }
private getClientSecret()	{ settings.clientSecret }
//private getClientId()		{ app.id }
//private getClientSecret()	{ state.accessToken }

private getCallbackUrl()	{ getServerUrl()+ "/oauth/callback?access_token=${state.accessToken}" }
private getBuildRedirectUrl() { getServerUrl() + "/oauth/initialize?access_token=${state.accessToken}" }
private getServerUrl()		{ return getFullApiServerUrl() }

// Automatically generated. Make future change here.
definition(
	name: "Netatmo (Connect)",
	namespace: "scrool",
	author: "Stuart Buchanan, Pavol Babinčák",
	description: "Netatmo Integration",
	category: "Weather",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/netamo-icon-1.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/netamo-icon-1%402x.png",
	oauth: true,
	singleInstance: true
)

preferences {
	page(name: "Credentials", title: "Fetch OAuth2 Credentials", content: "authPage", install: false)
	page(name: "listDevices", title: "Netatmo Devices", content: "listDevices", install: true)
}

mappings {
	path("/oauth/callback") {action: [GET: "callback"]}
}


def authPage() {
	if (logEnable) log.debug "In authPage"

	def description
	def uninstallAllowed = false
	def oauthTokenProvided = false

	if (!state.accessToken) {
		if (logEnable) log.debug "About to create access token."
		state.accessToken = createAccessToken()
		if (logEnable) log.debug "Access token is : ${state.accessToken}"
	}

	def redirectUrl = getBuildRedirectUrl()
	// if (logEnable) log.debug "Redirect url = ${redirectUrl}"

	if (state.authToken) {
		description = "Tap 'Next' to proceed"
		uninstallAllowed = true
		oauthTokenProvided = true
	} else {
		description = "Click to enter Credentials."
	}

	if (!oauthTokenProvided) {
		if (logEnable) log.debug "Showing the login page"
		return dynamicPage(name: "Credentials", title: "Authorize Connection", nextPage:"listDevices", uninstall: uninstallAllowed, install:false) {
			section("Enter Netatmo Application Details...") {
				paragraph "you can get these details after creating a new application on https:\\developer.netatmo.com"
				input(name: 'clientId', title: 'Client ID', type: 'text', required: true)
				input(name: 'clientSecret', title: 'Client secret (click away from this box before pressing the button below)', type: 'text', required: true, submitOnChange: true )
			}
			section() {
				paragraph "Tap below to log in to Netatmo and authorize Hubitat access."
				href url:oauthInitUrl(), external:true, required:false, title:"Connect to ${getVendorName()}:", description:description
			}
		}
	} else {
		if (logEnable) log.debug "Showing the devices page"
		return dynamicPage(name: "Credentials", title: "Connected", nextPage:"listDevices", uninstall: uninstallAllowed, install:false) {
			section() {
				input(name:"Devices", style:"embedded", required:false, title:"Netatmo is now connected to Hubitat!", description:description)
			}
		}
	}
}


def oauthInitUrl() {
	if (logEnable) log.debug "In oauthInitUrl"
	a
	state.oauthInitState = UUID.randomUUID().toString()
	if (logEnable) log.debug "oAuthInitStateIs: ${state.oauthInitState}"

	def oauthParams = [
		response_type: "code",
		client_id: getClientId(),
		client_secret: getClientSecret(),
		state: state.oauthInitState,
		redirect_uri: getCallbackUrl(),
		scope: "read_station"
	]

	def authMethod = [
		'location': [
			uri: getApiUrl(),
			path: getVendorAuthPath(),
			requestContentType: "application/json",
			query: [toQueryString(oauthParams)]
		]
	]

	def authRequest = authMethod.getAt(authMethod)
	try{
		if (logEnable) log.debug "Executing 'SendCommand'"
		if (authMethod == "location"){
			if (logEnable) log.debug "Executing 'SendAuthRequest'"
			httpGet(authRequest) { authResp ->
				parseAuthResponse(authResp)
			}
		}
	}
	catch(Exception e){
		if (logEnable) log.debug("___exception: " + e)
	}

	if (logEnable) log.debug "REDIRECT URL: ${getApiUrl()}${getVendorAuthPath()}?${toQueryString(oauthParams)}"

	return "${getApiUrl()}${getVendorAuthPath()}?${toQueryString(oauthParams)}"
}

private parseAuthResponse(resp) {
	if (logEnable) log.debug("Executing parseAuthResponse: "+resp.data)
	if (logEnable) log.debug("Output status: "+resp.status)
}

def callback() {
	if (logEnable) log.debug "callback()>> params: $params, params.code ${params.code}"

	def code = params.code
	def oauthState = params.state

	if (oauthState == state.oauthInitState) {

		def tokenParams = [
			client_secret: getClientSecret(),
			client_id : getClientId(),
			grant_type: "authorization_code",
			redirect_uri: getCallbackUrl(),
			code: code,
			scope: "read_station"
		]

		if (logEnable) log.debug "TOKEN URL: ${getVendorTokenPath() + toQueryString(tokenParams)}"

		def tokenUrl = getVendorTokenPath()
		def params = [
			uri: tokenUrl,
			contentType: 'application/x-www-form-urlencoded',
			body: tokenParams
		]

		if (logEnable) log.debug "PARAMS: ${params}"

		httpPost(params) { resp ->

			def slurper = new JsonSlurper()

			resp.data.each { key, value ->
				def data = slurper.parseText(key)

				state.refreshToken = data.refresh_token
				state.authToken = data.access_token
				state.tokenExpires = now() + (data.expires_in * 1000)
				// if (logEnable) log.debug "swapped token: $resp.data"
			}
		}

		// Handle success and failure here, and render stuff accordingly
		if (state.authToken) {
			success()
		} else {
			fail()
		}

	} else {
		log.error "callback() failed oauthState != state.oauthInitState"
	}
}

def success() {
	if (logEnable) log.debug "OAuth flow succeeded"
	def message = """
	<p>We have located your """ + getVendorName() + """ account.</p>
	<p>Close this page and install the application again. you will not be prompted for credentials next time.</p>
	"""
	connectionStatus(message)
}

def fail() {
	if (logEnable) log.debug "OAuth flow failed"
	def message = """
	<p>The connection could not be established!</p>
	<p>Close this page and attempt install the application again.</p>
	"""
	connectionStatus(message)
}

def connectionStatus(message, redirectUrl = null) {
	def redirectHtml = ""
	if (redirectUrl) {
		redirectHtml = """
			<meta http-equiv="refresh" content="3; url=${redirectUrl}" />
		"""
	}

	def html = """
		<!DOCTYPE html>
		<html>
		<head>
		<meta name="viewport" content="width=device-width, initial-scale=1">
		<title>${getVendorName()} Connection</title>
		<style type="text/css">
			* { box-sizing: border-box; }
			@font-face {
				font-family: 'Swiss 721 W01 Thin';
				src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot');
				src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot?#iefix') format('embedded-opentype'),
				url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.woff') format('woff'),
				url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.ttf') format('truetype'),
				url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.svg#swis721_th_btthin') format('svg');
				font-weight: normal;
				font-style: normal;
			}
			@font-face {
				font-family: 'Swiss 721 W01 Light';
				src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot');
				src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot?#iefix') format('embedded-opentype'),
				url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.woff') format('woff'),
				url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.ttf') format('truetype'),
				url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.svg#swis721_lt_btlight') format('svg');
				font-weight: normal;
				font-style: normal;
			}
			.container {
				width: 100%;
				padding: 40px;
				/*background: #eee;*/
				text-align: center;
			}
			img {
				vertical-align: middle;
			}
			img:nth-child(2) {
				margin: 0 30px;
			}
			p {
				font-size: 2.2em;
				font-family: 'Swiss 721 W01 Thin';
				text-align: center;
				color: #666666;
				margin-bottom: 0;
			}
			/*
			p:last-child {
				margin-top: 0px;
			}
			*/
			span {
				font-family: 'Swiss 721 W01 Light';
				}
		</style>
		</head>
		<body>
			<div class="container">
				<img src=""" + getVendorIcon() + """ alt="Vendor icon" />
				<img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/connected-device-icn%402x.png" alt="connected device icon" />
				<img src="https://cdn.shopify.com/s/files/1/2575/8806/t/20/assets/logo-image-file.png" alt="Hubitat logo" />
				${message}
			</div>
		</body>
		</html>
	"""
	render contentType: 'text/html', data: html
}

def refreshToken() {
	if (logEnable) log.debug "In refreshToken"

	def oauthParams = [
		client_secret: getClientSecret(),
		client_id: getClientId(),
		grant_type: "refresh_token",
		refresh_token: state.refreshToken
	]

	def tokenUrl = getVendorTokenPath()
	def params = [
		uri: tokenUrl,
		contentType: 'application/x-www-form-urlencoded',
		body: oauthParams,
	]

	// OAuth Step 2: Request access token with our client Secret and OAuth "Code"
	try {
		httpPost(params) { response ->
			def slurper = new JsonSlurper();

			response.data.each {key, value ->
				def data = slurper.parseText(key);
				// if (logEnable) log.debug "Data: $data"

				state.refreshToken = data.refresh_token
				state.accessToken = data.access_token
				state.tokenExpires = now() + (data.expires_in * 1000)
				return true
			}

		}
	} catch (Exception e) {
		if (logEnable) log.debug "Error: $e"
	}

	// We didn't get an access token
	if ( !state.accessToken ) {
		return false
	}
}

String toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

def installed() {
	if (logEnable) log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	if (logEnable) log.debug "Updated with settings: ${settings}"

	unsubscribe()
	unschedule()
	initialize()
}

def initialize() {
	if (logEnable) log.debug "Initialized with settings: ${settings}"

	// Pull the latest device info into state
	getDeviceList();

	settings.devices.each {
		def deviceId = it
		def detail = state?.deviceDetail[deviceId]

		try {
			switch(detail?.type) {
				case 'NAMain':
					if (logEnable) log.debug "Creating Base station, DeviceID: ${deviceId} Device name: ${detail.module_name}"
					createChildDevice("Netatmo Basestation", deviceId, "${detail.type}.${deviceId}", detail.module_name)
					break
				case 'NAModule1':
					if (logEnable) log.debug "Creating Outdoor module, DeviceID: ${deviceId} Device name: ${detail.module_name}"
					createChildDevice("Netatmo Outdoor Module", deviceId, "${detail.type}.${deviceId}", detail.module_name)
					break
				case 'NAModule3':
					if (logEnable) log.debug "Creating Rain Gauge, DeviceID: ${deviceId} Device name: ${detail.module_name}"
					createChildDevice("Netatmo Rain", deviceId, "${detail.type}.${deviceId}", detail.module_name)
					break
				case 'NAModule4':
					if (logEnable) log.debug "Creating Additional module, DeviceID: ${deviceId} Device name: ${detail.module_name}"
					createChildDevice("Netatmo Additional Module", deviceId, "${detail.type}.${deviceId}", detail.module_name)
					break
				case 'NAModule2':
					if (logEnable) log.debug "Creating Wind module, DeviceID: ${deviceId} Device name: ${detail.module_name}"
					createChildDevice("Netatmo Wind", deviceId, "${detail.type}.${deviceId}", detail.module_name)
					break
			}
		} catch (Exception e) {
			log.error "Error creating device: ${e}"
		}
	}

	// Cleanup any other devices that need to go away
	def delete = getChildDevices().findAll { !settings.devices.contains(it.deviceNetworkId) }
	if (logEnable) log.debug "Delete: $delete"
	delete.each { deleteChildDevice(it.deviceNetworkId) }

	// check if user has set location
	checkloc()
	// Do the initial poll
	poll()
	// Schedule it to run every 5 minutes
	runEvery5Minutes("poll")
}

def uninstalled() {
	if (logEnable) log.debug "In uninstalled"

	removeChildDevices(getChildDevices())
}

def getDeviceList() {
	if (logEnable) log.debug "Refreshing station data"
	def deviceList = [:]
	def moduleName = null
	state.deviceDetail = [:]
	state.deviceState = [:]

	apiGet("/api/getstationsdata",["get_favorites":true]) { resp ->
		state.response = resp.data.body
		resp.data.body.devices.each { value ->
			def key = value._id
			if (value.module_name != null) {
				deviceList[key] = "${value.station_name}: ${value.module_name}"
				state.deviceDetail[key] = value
				state.deviceState[key] = value.dashboard_data
			}

			value.modules.each { value2 ->
				def key2 = value2._id

				if (value2.module_name != null) {
					deviceList[key2] = "${value.station_name}: ${value2.module_name}"
					state.deviceDetail[key2] = value2
					state.deviceState[key2] = value2.dashboard_data
				}
				else {
					switch(value2.type) {
						case "NAModule1":
							moduleName = "Outdoor ${value.station_name}"
							break
						case "NAModule2":
							moduleName = "Wind ${value.station_name}"
							break
						case "NAModule3":
							moduleName = "Rain ${value.station_name}"
							break
						case "NAModule4":
							moduleName = "Additional ${value.station_name}"
							break
					}
					deviceList[key2] = "${value.station_name}: ${moduleName}"
					state.deviceDetail[key2] = value2 << ["module_name" : moduleName]
					state.deviceState[key2] = value2.dashboard_data
				}
			}
		}
	}
	return deviceList.sort() { it.value.toLowerCase() }
}

private removeChildDevices(delete) {
	if (logEnable) log.debug "In removeChildDevices"

	if (logEnable) log.debug "deleting ${delete.size()} devices"

	delete.each {
		deleteChildDevice(it.deviceNetworkId)
	}
}

def createChildDevice(deviceFile, dni, name, label) {
	if (logEnable) log.debug "In createChildDevice"

	try {
		def existingDevice = getChildDevice(dni)
		if(!existingDevice) {
			if (logEnable) log.debug "Creating child"
			def childDevice = addChildDevice("scrool", deviceFile, dni, null, [name: name, label: label, completedSetup: true])
		} else {
			if (logEnable) log.debug "Device $dni already exists"
		}
	} catch (e) {
		log.error "Error creating device: ${e}"
	}
}

def listDevices() {
	if (logEnable) log.debug "Listing devices $devices "

	def devices = getDeviceList()

	dynamicPage(name: "listDevices", title: "Choose devices", install: true) {
		section("Devices") {
			input "devices", "enum", title: "Select Device(s)", required: false, multiple: true, options: devices
		}

		section("Preferences") {
			input "rainUnits", "enum", title: "Rain Units", description: "Please select rain units", required: true, options: [mm:'Millimeters', in:'Inches']
			input "pressUnits", "enum", title: "Pressure Units", description: "Please select pressure units", required: true, options: [mbar:'mbar', inhg:'inhg']
			input "windUnits", "enum", title: "Wind Units", description: "Please select wind units", required: true, options: [kph:'kph', ms:'ms', mph:'mph', kts:'kts']
			input "time", "enum", title: "Time Format", description: "Please select time format", required: true, options: [12:'12 Hour', 24:'24 Hour']
			input "sound", "number", title: "Sound Sensor: \nEnter the value when sound will be marked as detected", description: "Please enter number", required: false
            input "logEnable", "bool", title: "Enable debug logging", defaultValue: true
		}
	}
}

def apiGet(String path, Map query, Closure callback) {
	if(now() >= state.tokenExpires) {
		refreshToken();
	}

	query['access_token'] = state.accessToken
	def params = [
		uri: getApiUrl(),
		path: path,
		'query': query
	]
	// if (logEnable) log.debug "API Get: $params"

	try {
		httpGet(params)	{ response ->
			callback.call(response)
		}
	} catch (Exception e) {
		// This is most likely due to an invalid token. Try to refresh it and try again.
		if (logEnable) log.debug "apiGet: Call failed $e"
		if(refreshToken()) {
			if (logEnable) log.debug "apiGet: Trying again after refreshing token"
			httpGet(params) { response ->
				callback.call(response)
			}
		}
	}
}

def apiGet(String path, Closure callback) {
	apiGet(path, [:], callback);
}

def poll() {
	if (logEnable) log.debug "Polling"
	getDeviceList();
	def children = getChildDevices()
	//if (logEnable) log.debug "State: ${state.deviceState}"
	//if (logEnable) log.debug "Time Zone: ${location.timeZone}"


	settings.devices.each { deviceId ->
		def detail = state?.deviceDetail[deviceId]
		def data = state?.deviceState[deviceId]
		def child = children?.find { it.deviceNetworkId == deviceId }

		//if (logEnable) log.debug "Update: $child";
		switch(detail?.type) {
			case 'NAMain':
				if (logEnable) log.debug "Updating Basestation $data"
				child?.sendEvent(name: 'temperature', value: cToPref(data['Temperature']) as float, unit: getTemperatureScale())
				child?.sendEvent(name: 'carbonDioxide', value: data['CO2'], unit: "ppm")
				child?.sendEvent(name: 'humidity', value: data['Humidity'], unit: "%")
				child?.sendEvent(name: 'temp_trend', value: data['temp_trend'], unit: "")
				child?.sendEvent(name: 'pressure', value: (pressToPref(data['Pressure'])).toDouble().trunc(2), unit: settings.pressUnits)
				child?.sendEvent(name: 'soundPressureLevel', value: data['Noise'], unit: "db")
				child?.sendEvent(name: 'sound', value: noiseTosound(data['Noise']))
				child?.sendEvent(name: 'pressure_trend', value: data['pressure_trend'], unit: "")
				child?.sendEvent(name: 'min_temp', value: cToPref(data['min_temp']) as float, unit: getTemperatureScale())
				child?.sendEvent(name: 'max_temp', value: cToPref(data['max_temp']) as float, unit: getTemperatureScale())
				child?.sendEvent(name: 'units', value: settings.pressUnits)
				child?.sendEvent(name: 'lastupdate', value: lastUpdated(data['time_utc']), unit: "")
				child?.sendEvent(name: 'date_min_temp', value: lastUpdated(data['date_min_temp']), unit: "")
				child?.sendEvent(name: 'date_max_temp', value: lastUpdated(data['date_max_temp']), unit: "")
				break;
			case 'NAModule1':
				if (logEnable) log.debug "Updating Outdoor Module $data"
				child?.sendEvent(name: 'temperature', value: cToPref(data['Temperature']) as float, unit: getTemperatureScale())
				child?.sendEvent(name: 'humidity', value: data['Humidity'], unit: "%")
				child?.sendEvent(name: 'temp_trend', value: data['temp_trend'], unit: "")
				child?.sendEvent(name: 'min_temp', value: cToPref(data['min_temp']) as float, unit: getTemperatureScale())
				child?.sendEvent(name: 'max_temp', value: cToPref(data['max_temp']) as float, unit: getTemperatureScale())
				child?.sendEvent(name: 'battery', value: detail['battery_percent'], unit: "%")
				child?.sendEvent(name: 'lastupdate', value: lastUpdated(data['time_utc']), unit: "")
				child?.sendEvent(name: 'date_min_temp', value: lastUpdated(data['date_min_temp']), unit: "")
				child?.sendEvent(name: 'date_max_temp', value: lastUpdated(data['date_max_temp']), unit: "")
				break;
			case 'NAModule3':
				if (logEnable) log.debug "Updating Rain Module $data"
				child?.sendEvent(name: 'rain', value: (rainToPref(data['Rain'])), unit: settings.rainUnits)
				child?.sendEvent(name: 'rainSumHour', value: (rainToPref(data['sum_rain_1'])), unit: settings.rainUnits)
				child?.sendEvent(name: 'rainSumDay', value: (rainToPref(data['sum_rain_24'])), unit: settings.rainUnits)
				child?.sendEvent(name: 'units', value: settings.rainUnits)
				child?.sendEvent(name: 'battery', value: detail['battery_percent'], unit: "%")
				child?.sendEvent(name: 'lastupdate', value: lastUpdated(data['time_utc']), unit: "")
				child?.sendEvent(name: 'rainUnits', value: rainToPrefUnits(data['Rain']), displayed: false)
				child?.sendEvent(name: 'rainSumHourUnits', value: rainToPrefUnits(data['sum_rain_1']), displayed: false)
				child?.sendEvent(name: 'rainSumDayUnits', value: rainToPrefUnits(data['sum_rain_24']), displayed: false)
				break;
			case 'NAModule4':
				if (logEnable) log.debug "Updating Additional Module $data"
				child?.sendEvent(name: 'temperature', value: cToPref(data['Temperature']) as float, unit: getTemperatureScale())
				child?.sendEvent(name: 'carbonDioxide', value: data['CO2'], unit: "ppm")
				child?.sendEvent(name: 'humidity', value: data['Humidity'], unit: "%")
				child?.sendEvent(name: 'temp_trend', value: data['temp_trend'], unit: "")
				child?.sendEvent(name: 'min_temp', value: cToPref(data['min_temp']) as float, unit: getTemperatureScale())
				child?.sendEvent(name: 'max_temp', value: cToPref(data['max_temp']) as float, unit: getTemperatureScale())
				child?.sendEvent(name: 'battery', value: detail['battery_percent'], unit: "%")
				child?.sendEvent(name: 'lastupdate', value: lastUpdated(data['time_utc']), unit: "")
				child?.sendEvent(name: 'date_min_temp', value: lastUpdated(data['date_min_temp']), unit: "")
				child?.sendEvent(name: 'date_max_temp', value: lastUpdated(data['date_max_temp']), unit: "")
				break;
			case 'NAModule2':
				if (logEnable) log.debug "Updating Wind Module $data"
				child?.sendEvent(name: 'WindAngle', value: data['WindAngle'], unit: "°", displayed: false)
				child?.sendEvent(name: 'GustAngle', value: data['GustAngle'], unit: "°", displayed: false)
				child?.sendEvent(name: 'battery', value: detail['battery_percent'], unit: "%")
				child?.sendEvent(name: 'WindStrength', value: (windToPref(data['WindStrength'])).toDouble().trunc(1), unit: settings.windUnits)
				child?.sendEvent(name: 'GustStrength', value: (windToPref(data['GustStrength'])).toDouble().trunc(1), unit: settings.windUnits)
				child?.sendEvent(name: 'max_wind_str', value: (windToPref(data['max_wind_str'])).toDouble().trunc(1), unit: settings.windUnits)
				child?.sendEvent(name: 'units', value: settings.windUnits)
				child?.sendEvent(name: 'lastupdate', value: lastUpdated(data['time_utc']), unit: "")
				child?.sendEvent(name: 'date_max_wind_str', value: lastUpdated(data['date_max_wind_str']), unit: "")
				child?.sendEvent(name: 'WindDirection', value: windTotext(data['WindAngle']))
				child?.sendEvent(name: 'GustDirection', value: gustTotext(data['GustAngle']))
				child?.sendEvent(name: 'WindStrengthUnits', value: windToPrefUnits(data['WindStrength']), displayed: false)
				child?.sendEvent(name: 'GustStrengthUnits', value: windToPrefUnits(data['GustStrength']), displayed: false)
				child?.sendEvent(name: 'max_wind_strUnits', value: windToPrefUnits(data['max_wind_str']), displayed: false)
				break;
		}
	}
}

def cToPref(temp) {
	if(getTemperatureScale() == 'C') {
		return temp
	} else {
		return temp * 1.8 + 32
	}
}

def rainToPref(rain) {
	if(settings.rainUnits == 'mm') {
		return rain.toDouble().trunc(1)
	} else {
		return (rain * 0.039370).toDouble().trunc(3)
	}
}

def rainToPrefUnits(rain) {
	if(settings.rainUnits == 'mm') {
		return rain.toDouble().trunc(1) + " mm"
	} else {
		return (rain * 0.039370).toDouble().trunc(3) + " in"
	}
}

def pressToPref(Pressure) {
	if(settings.pressUnits == 'mbar') {
		return Pressure
	} else {
		return Pressure * 0.029530
	}
}

def windToPref(Wind) {
	if(settings.windUnits == 'kph') {
		return Wind
	} else if (settings.windUnits == 'ms') {
		return Wind * 0.277778
	} else if (settings.windUnits == 'mph') {
		return Wind * 0.621371192
	} else if (settings.windUnits == 'kts') {
		return Wind * 0.539956803
	}
}

def windToPrefUnits(Wind) {
	if(settings.windUnits == 'kph') {
		return Wind
	} else if (settings.windUnits == 'ms') {
		return (Wind * 0.277778).toDouble().trunc(1) +" ms"
	} else if (settings.windUnits == 'mph') {
		return (Wind * 0.621371192).toDouble().trunc(1) +" mph"
	} else if (settings.windUnits == 'kts') {
		return (Wind * 0.539956803).toDouble().trunc(1) +" kts"
	}
}

def lastUpdated(time) {
	if(location.timeZone == null) {
		log.warn "Time Zone is not set, time will be in UTC. Go to your ST app and set your hub location to get local time!"
		def updtTime = new Date(time*1000L).format("HH:mm")
		state.lastUpdated = updtTime
		return updtTime + " UTC"
	} else if(settings.time == '24') {
		def updtTime = new Date(time*1000L).format("HH:mm", location.timeZone)
		state.lastUpdated = updtTime
		return updtTime
	} else if(settings.time == '12') {
		def updtTime = new Date(time*1000L).format("h:mm aa", location.timeZone)
		state.lastUpdated = updtTime
		return updtTime
	}
}

def windTotext(WindAngle) {
	if(WindAngle < 23) {
		return WindAngle + "° North"
	} else if (WindAngle < 68) {
		return WindAngle + "° NorthEast"
	} else if (WindAngle < 113) {
		return WindAngle + "° East"
	} else if (WindAngle < 158) {
		return WindAngle + "° SouthEast"
	} else if (WindAngle < 203) {
		return WindAngle + "° South"
	} else if (WindAngle < 248) {
		return WindAngle + "° SouthWest"
	} else if (WindAngle < 293) {
		return WindAngle + "° West"
	} else if (WindAngle < 338) {
		return WindAngle + "° NorthWest"
	} else if (WindAngle < 361) {
		return WindAngle + "° North"
	}
}

def gustTotext(GustAngle) {
	if(GustAngle < 23) {
		return GustAngle + "° North"
	} else if (GustAngle < 68) {
		return GustAngle + "° NEast"
	} else if (GustAngle < 113) {
		return GustAngle + "° East"
	} else if (GustAngle < 158) {
		return GustAngle + "° SEast"
	} else if (GustAngle < 203) {
		return GustAngle + "° South"
	} else if (GustAngle < 248) {
		return GustAngle + "° SWest"
	} else if (GustAngle < 293) {
		return GustAngle + "° West"
	} else if (GustAngle < 338) {
		return GustAngle + "° NWest"
	} else if (GustAngle < 361) {
		return GustAngle + "° North"
	}
}

def noiseTosound(Noise) {
	if(Noise > settings.sound) {
		return "detected"
	} else {
		return "not detected"
	}
}

def checkloc() {
	if(location.timeZone == null)
		sendPush("Netatmo: Time Zone is not set, time will be in UTC. Go to your ST app and set your hub location to get local time!")
}

def debugEvent(message, displayEvent) {
	def results = [
		name: "appdebug",
		descriptionText: message,
		displayed: displayEvent
	]
	if (logEnable) log.debug "Generating AppDebug Event: ${results}"
	sendEvent (results)
}
