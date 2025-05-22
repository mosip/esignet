import { Buffer } from "buffer";

const encodeString = (str) => {
    return Buffer.from(str).toString("base64");
}

const decodeHash = (hash) => {
    return Buffer.from(hash, "base64").toString();
}

/**
 * Take a config object and a property name, and check if the property exists
 * in the config object. If it does, return true, otherwise return false.
 * @param object config 
 * @param string property 
 * @returns boolean
 */
const checkConfigProperty = (config, property) => {
    if (config && config[property]) {
        return true;
    }
    return false;
}

export { encodeString, decodeHash, checkConfigProperty };