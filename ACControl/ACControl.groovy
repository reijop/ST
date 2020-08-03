/* Fuck off */

definition(
    name: "Bedroom AC Control",
    namespace: "reijop/ST",
    author: "Reijo",
    version: "1.0a1",
    description: "do things with AC units",
    category: "Rationality",
    iconUrl: "https://en.gravatar.com/userimage/163628812/03855629a9f170cba5ceba6672d3efde.jpg?size=201",
    iconX2Url: "https://en.gravatar.com/userimage/163628812/03855629a9f170cba5ceba6672d3efde.jpg?size=402"
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
    }
    
    section("Switches and Sensors") {
    	input "inTemp", "capability.temperatureMeasurement", title: "Indoor Thermometers", multiple: true
      input "inMotion", "capability.presence", title: "Indoor Motion Sensors", multiple: true
      input "fans", "capability.switch", title: "Vent Fan", multiple: true
      input "acUnits", "capability.switch", title: "AC Unit", multiple: true
    }
    
    section("Runtime") {
    	input "suspend", "capability.switch", title: "Dont run when switch is on"
      input "overrideFan", "capability.switch", title: "Only run fan when switch is on"
      input "overrideAC", "capability.switch", title: "Only run AC when switch is on"
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
	state.fanRunning = false 
        state.acRunning = false;

    subscribe(outTemp, "temperature", "checkThings");
    subscribe(inTemp, "temperature", "checkThings");
    subscribe(inMotion, "motion", "checkThings");
    subscribe(thermostat, "thermostatMode", "checkThings");
    subscribe(thermostat, "thermostatOperatingState", "checkThings");
    subscribe(suspend, "switch", "checkThings");
    subscribe(overrideFan, "switch", "checkThings");
    subscribe(overrideAC, "switch", "checkThings");
}

def checkThings(evt) {
    def outsideTemp = settings.outTemp.currentValue('temperature')
    def insideTemp = settings.inTemp.currentValue('temperature').average
    def insideMotion = settings.inMotion.currentValue('motion')
    def thermostatMode = settings.thermostat.currentValue('thermostatMode')
    def thermostatOperatingState = settings.thermostat.currentValue('thermostatOperatingState')
    def suspendHelper = settings.suspend.currentValue('switch')
    def overrideFan = settings.overrideFan.currentValue('switch')
    def overrideAC = settings.overrideAC.currentValue('switch')
    
    log.debug "disable:($suspendHeper) override:(f:$overrideFan/ac:$overrideAC)
    log.debug "Inside: $insideTemp, Outside: $outsideTemp, Thermostat: $thermostatMode/$thermostatOperatingState"
    log.debug "Motion: $insideMotion"
    
    def fanRequestRun = false;
    def acRequestRun = false;
    
    /* Temp Control */
	if(outsideTemp <= insideTemp) {
		if(insideTemp >= minFan) {
			fanRequestRun = true;
			acRequestRun = false;
		}
		else { /* Fan Low temp cutoff */
			fanRequestRun = false;
			acRequestRun = false;
		}
	}
	else {
		if((insideTemp >= minAC /* && insideMotion.map(eq('active')) */ ) ||
		   (insideTemp >= minTemp)) {
			fanRequestRun = false;
			acRequestRun = true;
		}
		else { /* AC Low temp cutoff */
			fanRequestRun = false;
			acRequestRun = false;
		}
	}

    /* negatives */
    if(enableThermostat == true && 
    thermostatMode == ('heat') &&
    thermostatOperatingState.matches('heat')){
    	log.debug "Not running due to thermostat mode/state ($enableThermostat:$thermostatMode/$thermostatOperatingState)"
    	fanRequestRun = false;
        acRequestRun = false;
    }

    /* manuel */
    if(overrideFan == 'on') {
       log.debug "OVERRIDE: Request fan on"
       fanShouldRun = true;
       acShouldRun = false;
    }
    
    if(overrideAC == 'on') {
       log.debug "OVERRIDE: Request AC on"
       fanShouldRun = false;
       acShouldRun = true;
    }
    
    if(overrideAC == 'on' &&
       log.debug "OVERRIDE: Request AC and fan on - turning on only fan"
       overrideFan == 'on') {
       fanShouldRun = true;
       acShouldRun = false;
    }
    
    if(suspendHelper == 'on') {
        log.debug "DISABLE: Not running due to disable switch: $suspendHelper"
    	fanShouldRun = false;
        acShouldRun = false;
    }
    
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
