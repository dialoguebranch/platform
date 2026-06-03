import { ConsoleLogger } from './dlb-lib/util/ConsoleLogger.js';
import { LOG_LEVEL_NAMES } from './dlb-lib/util/AbstractLogger.js';
import { WCTAClientState } from './dlb-lib/WCTAClientState.js';
import config from './config.js';

const LOGTAG = "WCTAController";
const logger = new ConsoleLogger(config.logLevel);
logger.info(LOGTAG, "Initialized Logger with log level '"
    + config.logLevel
    + "' ('"
    + LOG_LEVEL_NAMES[config.logLevel]
    + "').");

// Initialize the ClientState object and take actions
const state = new WCTAClientState(logger);
state.loadFromCookie();

export default state;
