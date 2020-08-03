/**
 *  Bedroom AC Control
 *
 *  Copyright 2020 Reijo Pitkanen
 *
 */
definition(
    name: "Bedroom AC Control",
    namespace: "reijop",
    author: "Reijo Pitkanen",
    description: "Bedroom AC/fan control",
    category: "SmartThings Labs",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
    )


preferences {
	section("Thermostat") {
		input "enableThermostat", "bool", title: "Dont run when heat is on"
    	input "thermostat", "capability.thermostat", title: "Thermostat"
    }
    
	section("Outdoor") {
		input "outTemp", "capability.temperatureMeasurement", title: "Outdoor Thermometer"
	}
    
    section("Indoor") {
        input "minFan", "number", title: "Minimum Fan Temp"
        input "minAC", "number", title: "Minimum AC Temp, Occupied"
        input "minTemp", "number", title: "Minimum AC Temp, Unoccupied"
        input "inMotionDelay", "number", title: "Motion Sensor Delay", required: false
    }
    
    section("Switches and Sensors") {
    	input "inTemp", "capability.temperatureMeasurement", title: "Indoor Thermometers", multiple: true
        input "inMotion", "capability.motionSensor", title: "Indoor Motion Sensors", multiple: true, required: false
        input "fans", "capability.switch", title: "Vent Fan", multiple: true
        input "acUnits", "capability.switch", title: "AC Unit", multiple: true
    }
    
    section("Runtime") {
    	input "suspend", "capability.switch", title: "Dont run when switch is on", required: false
        input "overrideFan", "capability.switch", title: "Only run fan when switch is on", required: false
        input "overrideAC", "capability.switch", title: "Only run AC when switch is on", required: false
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
	state.fanRunning = false;
    state.acRunning = false;
    state.occupied = false;

    subscribe(outTemp, "temperature", "checkThings");
    subscribe(inTemp, "temperature", "checkThings");
    subscribe(inMotion, "motion", "setMotion");
    subscribe(thermostat, "thermostatMode", "checkThings");
    subscribe(thermostat, "thermostatOperatingState", "checkThings");
    subscribe(suspend, "switch", "checkThings");
    subscribe(overrideFan, "switch", "checkThings");
    subscribe(overrideAC, "switch", "checkThings");
}

def expireMotion(evt) {
	log.debug "motion timeout expire"
	state.occupied = false;
}
 
def setMotion(evt) {
    def occupied = settings.inMotion?.currentValue('motion')
	if (occupied == 'active') {
        log.debug "motion timeout set"
    	state.occupied = true;
    	runin(inMotionDelay, expireMotion)
    }
}

def switchUnits(evt) {
    if(fanShouldRun && !state.fanRunning) {
        log.debug "request fan on"
        fans.on();
        state.fanRunning = true;
    } else if(!shouldRun && state.fanRunning) {
        log.debug "request fan off"
    	fans.off();
        state.fanRunning = false;
    }

    if(acShouldRun && !state.acRunning) {
        log.debug "request AC on"
        acUnits.on();
        state.accRunning = true;
    } else if(!acShouldRun && state.acRunning) {
        log.debug "request AC off"
    	acUnits.off();
        state.acRunning = false;
    }
}

def checkThings(evt) {
    def outsideTemp = settings.outTemp.currentValue('temperature')
    def insideTemp = settings.inTemp.currentValue('temperature').sum() / settings.inTemp.currentValue('temperature').size()
    def insideMotion = settings.inMotion?.currentValue('motion')
    def thermostatMode = settings.thermostat.currentValue('thermostatMode')
    def thermostatOperatingState = settings.thermostat.currentValue('thermostatOperatingState')
    def suspendHelper = settings.suspend?.currentValue('switch')
    def overrideFan = settings.overrideFan?.currentValue('switch')
    def overrideAC = settings.overrideAC?.currentValue('switch')
    
    log.debug "def****: disable:($suspendHelper) override:(f:$overrideFan/ac:$overrideAC) Inside: $insideTemp, Outside: $outsideTemp, Thermostat: $thermostatMode/$thermostatOperatingState, Motion: $insideMotion"
	log.debug "state**: fanRunning: $state.fanRunning, acRunning: $state.acRunning, occupied: $state.occupied"
    def fanRequestRun = false;
    def acRequestRun = false;
    
    /* Temp Control */
	if(outsideTemp <= insideTemp) {
    	log.debug "outsideTemp:$outsideTemp <= insideTemp:$insideTemp"
		if(insideTemp >= minFan) {
        	log.debug "FANON: insideTemp:$insideTemp >= minFan:$minFan"
			fanRequestRun = true;
			acRequestRun = false;
		}
		else { /* Fan Low temp cutoff */
        	log.debug "ALLOFF: fan low temp cutoff: insideTemp:$insideTemp < minFan:$minFan"
			fanRequestRun = false;
			acRequestRun = false;
		}
	}
	else {
        log.debug "outsideTemp:$outsideTemp > insideTemp:$insideTemp"
		if((insideTemp >= minAC && occupied) ||
		   (insideTemp >= minTemp)) {
            log.debug "ACON: insideTemp:$insideTemp >= minAC/occ:$minAc/$occupied OR insideTemp:$insideTemp >= minTemp:$minTemp"
			fanRequestRun = false;
			acRequestRun = true;
		}
		else { /* AC Low temp cutoff */
            log.debug "ALLOFF: ac low temp cutoff: insideTemp:$insideTemp < $minAC:$minAC"
			fanRequestRun = false;
			acRequestRun = false;
		}
	}


    /* negatives */
    if(enableThermostat == true && 
    thermostatMode == ('heat') &&
    thermostatOperatingState.matches('heat')){
    	log.debug "THERM: Not running due to thermostat mode/state ($enableThermostat:$thermostatMode/$thermostatOperatingState)"
    	fanRequestRun = false;
        acRequestRun = false;
    }

    /* manuel */
    if(overrideFan == 'on') {
        log.debug "OVERRIDE: Request fan on"
    	fanRequestRun = true;
        acRequestRun = false;
    }
    
    if(overrideAC == 'on') {
        log.debug "OVERRIDE: Request AC on"
    	fanRequestRun = false;
        acRequestRun = true;
    }
    
    if(overrideAC == 'on' && overrideFan == 'on') {
        log.debug "OVERRIDE: Request AC and fan on - turning on only fan"
    	fanRequestRun = true;
        acRequestRun = false;
    }
    
    if(suspendHelper == 'on') {
        log.debug "DISABLE: Not running due to disable switch: $suspendHelper"
    	fanRequestRun = false;
        acRequestRun = false;
    }
    
    if(fanRequestRun && !state.fanRunning) {
        log.debug "request fan on"
        fans.on();
        state.fanRunning = true;
    } else if(!fanRequestRun && state.fanRunning) {
        log.debug "request fan off"
    	fans.off();
        state.fanRunning = false;
    }

    if(acRequestRun && !state.acRunning) {
        log.debug "request AC on"
        acUnits.on();
        state.acRunning = true;
    } else if(!acRequestRun && state.acRunning) {
        log.debug "request AC off"
    	acUnits.off();
        state.acRunning = false;
    }

}
