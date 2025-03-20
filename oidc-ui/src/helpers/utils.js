import { Buffer } from "buffer";

const encodeString = (str) => {
    return Buffer.from(str).toString("base64");
}

const decodeHash = (hash) => {
    return Buffer.from(hash, "base64").toString();
}

export { encodeString, decodeHash };