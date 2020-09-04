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

    section("Alerts") {
        input "readyAlert", "bool", title: "Alert via push notification when temp is Ready"
        input "hotAlert", "bool", title: "Alert via push notification when temp is Hot"
        input "fallingTempAlert", "bool", title: "Alert via push notification when temp starts falling"
        input "maxSessionsStats",  "bool", title: "Send stats via push notification when session ends"
    }

    section("Monitoring") {
        input "startSaunaTemp", "number", title: "Sauna monitoring starts when temp goes above/below"
        input "readySaunaTemp", "number", title: "Sauna is ready when temp reaches this", required: false
        input "hotSaunatemp", "number", title: "Sauna is hot"
        input "fallingTemp", "number", title: "Sauna heater is off when temp falls this many degrees", required: false
        input "fallingTempTime", "number", title: "Sauna heater is off when temp falls in this many seconds", required: false
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
    state.sauna = "off";
    state.startSession = now()
    state.stopSession = now()
    state.priorTemp = 0;
    state.maxTemp = 0;
    subscribe(outTemp, "temperature", "checkThings");
    subscribe(inTemp, "temperature", "checkThings");
    subscribe(inHumidity, "temperature", "checkThings");
}

def timeSince(start, end) {
    log.debug "timeSince(${start}, ${end})"
    if (start == end) {
        return "0h0m0s"
    } else if (end - start < 0) {
        return "Time-travel"
    } else {
        int dur = ((end - start).intdiv(1000));
        log.debug "dur: $dur"
        int sec = (int) (dur % 60);
        int min = (int) (dur.intdiv(60) % 60);
        int hr = (int) (dur.intdiv(60).intdiv(60) % 24);
        return "${hr}h${min}m${sec}s"
    }
}

def sendMessageMaximums() {
    log.info "Sending out session stats"

    def session = timeSince(state.startSession, state.stopSession)
    def maxDelta = state.maxTemp - settings.outTemp.currentValue('temperature')
    def curDelta = insideTemp - settings.outTemp.currentValue('temperature')
    def msg = "Temp Current:${insideTemp}/\u0394:${curDelta} Max:${state.maxTemp}/\u0394:${maxDelta} Session: ${session}"
    log.debug "msg: ${msg}"

    if (maxSessionStats) { sendPush(msg) }
    sendNotificationEvent(msg)
}

def checkThings(evt) {
    log.debug "evt: ${evt.descriptionText}, $evt.value"
    log.debug "state: sauna:${state.sauna}, maxTemp:${state.maxTemp}, priorTemp:${state.priorTemp}, sessions(stop:(${state.stopSession}), start:(${state.startSession}) "

    def insideTemp = (settings.inTemp.currentValue('temperature').sum() / settings.inTemp.currentValue('temperature').size())
    def priorTempCalc = (state.priorTemp.toInteger() - insideTemp.toInteger())
    def sat = 100 // default Saturation

    log.debug "priorTempCalc: ${priorTempCalc}, insideTemp: ${insideTemp}"

    // Short circut if there's nothing to do.
    if (insideTemp < startSaunaTemp){
        log.info "Sauna is cold ($insideTemp < startSaunaTemp:${startSaunaTemp})"

          // Exit tasks TODO: HERE
        if (state.sauna != "off") {
            log.info "Stopping sauna session"
            sendNotificationEvent("Ending Sauna Session")
            state.stopSession = now()
            state.sauna = "off"
            sendMessageMaximums()
            state.maxTemp = insideTemp
            state.priorTemp = insideTemp
        }

        saunaStatusBulbs*.setColorTemperature(2700)
        return 0;
    }

    // Entry tasks TODO: heRE
    if (state.sauna == "off") {
        log.info "Starting sauna session"
        sendNotificationEvent("Starting Sauna Session")
        state.startSession = now()
        state.priorTemp = insideTemp
        state.maxTemp = 0
        state.sauna = "on"
    }

    def maxDelta = state.maxTemp - settings.outTemp.currentValue('temperature')
    def curDelta = insideTemp - settings.outTemp.currentValue('temperature')
    def session = timeSince(state.startSession, now())
    def msg = "Temps Current:${insideTemp}:\u0394:${curDelta}, Max:${state.maxTemp}:\u0394:${maxDelta}, Current Session Length: ${session}"
    log.info "msg: ${msg}"

    // bulbs are half-saturated if sauna is stable , full saturated if heating, barely saturated if cooling.
    if (priorTempCalc > 1){
       	state.sauna = "cooling"
        log.info "Sauna temp cooling"
        sat = 30
    } else if (priorTempCalc < -1){
          state.sauna = "warming"
        log.info "Sauna temp warming"
        sat = 100
    } else {
        state.sauna = "stable"
        log.info "Sauna temp stable"
        sat = 85
    }

    //return [Red: 0, Green: 39, Blue: 70, Yellow: 25, Orange: 10, Purple: 75, Pink: 83]
    switch(insideTemp) {
        case {it >= startSaunaTemp && it < readySaunaTemp}:
            log.info "Sauna is tepid" // Yellow/Green
            saunaStatusBulbs*.setColor([switch: "on", hue: 30, saturation: sat, level: 100])
            break;

        case {it >= readySaunaTemp && it < hotSaunaTemp}:
            log.info "Sauna is warm" // Orange
            saunaStatusBulbs*.setColor([switch: "on", hue: 15, saturation: sat, level: 100])
            break;

        case {it >= hotSaunaTemp && it < 212}:
            log.info "Sauna is hot" // Red
            saunaStatusBulbs*.setColor([switch: "on", hue: 0, saturation: sat, level: 100])
            break;

        case {it >= 212}:
            log.error "Sauna is on fire" // Blue
            saunaStatusBulbs*.setColor([switch: "on", hue: 70, saturation: 100, level: 100])
            break;

        default:
            log.error "Should never get here"
            break;
    }

    // Track max
    if (settings.inTemp.currentValue('temperature').any { it > state.maxTemp }){
        log.info "Setting new maxtemp: ${settings.inTemp.currentValue('temperature').max()} from ${settings.inTemp.currentValue('temperature')}"
        state.maxTemp = settings.inTemp.currentValue('temperature').max()
    }

    // Set state.PriorTemp for next run
    state.priorTemp = insideTemp
}
