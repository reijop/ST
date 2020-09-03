/**
 *  Sauna Status
 *
 *  Copyright 2020 Reijo Pitkanen
 *
 */
definition(
    name: "Sauna Status",
    namespace: "reijop",
    author: "Reijo Pitkanen",
    description: "What's the Sauna up to?",
    category: "SmartThings Labs",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
    )


preferences {
    section("Light and Color") {
        input "saunaStatusBulbs", "capability.colorControl", title: "Sauna indicator bulb", required: true, multiple: true
    }
 
    section("Monitoring") {
        input "startSaunaTemp", "number", title: "Sauna monitoring starts when temp goes above/below"
        input "readySaunaTemp", "number", title: "Sauna is ready when temp reaches this", required: false
        input "hotSaunatemp", "number", title: "Sauna is hot"
        input "fallingTemp", "number", title: "Sauna heater is off when temp falls this many degrees", required: false
        input "fallingTempTime", "number", title: "Sauna heater is off when temp falls in this many seconds", required: false
        input "fallingTempAlert", "bool", title: "Alert via push notification when temp starts falling"
    }

    section("Sensors") {
        input "outTemp", "capability.temperatureMeasurement", title: "Outdoor Thermometer", required: false
        input "inTemp", "capability.temperatureMeasurement", title: "Sauna Thermometers", multiple: true
        input "inHumidity", "capability.humidityMeasurement", title: "Sauna Humidity", multiple: true, required: false
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
    state.priorTemp = 0;
    state.maxTemp = 0;

    subscribe(outTemp, "temperature", "checkThings");
    subscribe(inTemp, "temperature", "checkThings");
    subscribe(inHumidity, "temperature", "checkThings");
}

def priorTempCalc() {
    insideTemp = settings.inTemp.currentValue('temperature').sum() / settings.inTemp.currentValue('temperature').size()
    v = state.priorTemp.toInteger() - insideTemp.toInteger()
    return v
}

def setState(evt) {
    # SetMax
    if (settings.inTemp.currentValue('temperature').any{ it > state.priorTemp }){
        state.maxTemp = settings.inTemp.currentValue('temperature').max()
    }

    state.sauna = true
}

#     return [Red: 0, Green: 39, Blue: 70, Yellow: 25, Orange: 10, Purple: 75, Pink: 83]
def setBulbColor(insideTemp) {
    saunaSatusBulbs*.on()
    switch(insideTemp) {
        case { it >= startSaunaTemp && it < readySaunaTemp}:
            log.debug "Sauna is tepid" # Green
            saunaStatusBulbs*.setColor([switch: "on", hue: 39, saturation: 100, level: 100])

        case { it >= readySaunaTemp && it < hotSaunaTemp}:
            log.debug "Sauna is warm" # Yellow
            saunaStatusBulbs*.setColor([switch: "on", hue: 25, saturation: 100, level: 100])

        case { it >= hotSaunaTemp && it < 212}:
            log.debug "Sauna is hot" # Red
            saunaStatusBulbs*.setColor([switch: "on", hue: 0, saturation: 100, level: 100])

        case >= 212:
            log.debug "Sauna is on fire"
            saunaStatusBulbs*.setColor([switch: "on", hue: 70, saturation: 100, level: 100])

        case default : log.warn "Sauna is undefined - should never get here"

    }
}

def checkThings(evt) {
    log.debug "${evt.descriptionText}, $evt.value"

    insideTemp = settings.inTemp.currentValue('temperature').sum() / settings.inTemp.currentValue('temperature').size()

    # Short circut if there's nothing to do.
    if (insideTemp < startSaunaTemp){
        state.sauna = false
        log.debug "Sauna is cold"
        return
    }

    def color = "White"
    def hueColor = 100
    def saturation = 100
    Map hClr = [:]
    hClr.hex = "#FFFFFF"


    if (priorTempCalc == 0){
        state.sauna = "stable"
    }
    else if (priorTempCalc > 0){
        state.sauna = "warming"
    }
    else {
        state.sauna = "cooling"
    }

    setBulbColor(insideTemp)
    setState(evt)
}
