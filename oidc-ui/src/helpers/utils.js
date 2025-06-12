import { Buffer } from "buffer";
import sha256 from "crypto-js/sha256";
import Base64 from "crypto-js/enc-base64";

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
    if (config && (property in config)) {
        return true;
    }
    return false;
}

const sortKeysDeep = obj => {
    if (Array.isArray(obj)) {
      return obj.map(sortKeysDeep);
    } else if (obj !== null && typeof obj === "object") {
      return Object.keys(obj)
        .sort()
        .reduce((result, key) => {
          result[key] = sortKeysDeep(obj[key]);
          return result;
        }, {});
    }
    return obj;
};

const getOauthDetailsHash = async (value) => {
    let sha256Hash = sha256(JSON.stringify(sortKeysDeep(value)));
    let hashB64 = Base64.stringify(sha256Hash)
        .split("=")[0]
        .replace(/\+/g, "-")
        .replace(/\//g, "_");
    return hashB64;
};

export { encodeString, decodeHash, checkConfigProperty, sortKeysDeep, getOauthDetailsHash };