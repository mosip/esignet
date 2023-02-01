import axios from "axios";
import { configurationKeys } from "../constants/clientConstants";
import localStorageService from "./local-storageService";
import * as jose from "jose";

const {
  addDeviceInfos,
  addDiscoveredDevices,
  clearDeviceInfos,
  clearDiscoveredDevices,
} = {
  ...localStorageService,
};

const SBI_DOMAIN_URI = window.origin;
const purpose = "Auth";
const certification = "L1";
const DeviceStatusReady = "Ready";

const deviceEndPoint = "/device";
const infoEndPoint = "/info";
const captureEndPoint = "/capture";

const mosip_DiscoverMethod = "MOSIPDISC";
const mosip_DeviceInfoMethod = "MOSIPDINFO";
const mosip_CaptureMethod = "CAPTURE";

const FACE_TYPE = "Face";
const FINGER_TYPE = "Finger";
const IRIS_TYPE = "Iris";


class sbiService {
  constructor(oAuthDetails) {
    this.getIdpConfiguration = oAuthDetails.getIdpConfiguration;
  }

  /**
   * Triggers capture request of SBI for auth capture
   * @param {url} host SBI is hosted on given host
   * @param {int} port port on which SBI is listening to.
   * @param {string} transactionId same as idp transactionId
   * @param {string} specVersion supported spec version
   * @param {string} type modality type
   * @param {string} deviceId
   * @returns auth capture response
   */
  capture_Auth = async (
    host,
    port,
    transactionId,
    specVersion,
    type,
    deviceId
  ) => {
    const env =
      this.getIdpConfiguration(configurationKeys.sbiEnv) ??
      process.env.REACT_APP_SBI_ENV;

    const captureTimeout =
      this.getIdpConfiguration(configurationKeys.sbiCAPTURETimeoutInSeconds) ??
      process.env.REACT_APP_SBI_CAPTURE_TIMEOUT;

    const irisBioSubtypes =
      this.getIdpConfiguration(configurationKeys.sbiIrisBioSubtypes) ??
      process.env.REACT_APP_SBI_IRIS_BIO_SUBTYPES;

    const fingerBioSubtypes =
      this.getIdpConfiguration(configurationKeys.sbiFingerBioSubtypes) ??
      process.env.REACT_APP_SBI_FINGER_BIO_SUBTYPES;

    let count = 1;
    let requestedScore = 70;
    let bioSubType = ["UNKNOWN"];
    switch (type) {
      case FACE_TYPE:
        count = this.getIdpConfiguration(configurationKeys.sbiFaceCaptureCount) ??
          process.env.REACT_APP_SBI_FACE_CAPTURE_COUNT;
        requestedScore = this.getIdpConfiguration(configurationKeys.sbiFaceCaptureScore) ??
          process.env.REACT_APP_SBI_FACE_CAPTURE_SCORE;
        bioSubType = null; //For Face: No bioSubType
        break;
      case FINGER_TYPE:
        count = this.getIdpConfiguration(configurationKeys.sbiFingerCaptureCount) ??
          process.env.REACT_APP_SBI_FINGER_CAPTURE_COUNT;
        requestedScore = this.getIdpConfiguration(configurationKeys.sbiFingerCaptureScore) ??
          process.env.REACT_APP_SBI_FINGER_CAPTURE_SCORE;
        bioSubType = fingerBioSubtypes.split(",").map((x) => x.trim());
        break;
      case IRIS_TYPE:
        count = this.getIdpConfiguration(configurationKeys.sbiIrisCaptureCount) ??
          process.env.REACT_APP_SBI_IRIS_CAPTURE_COUNT;
        requestedScore = this.getIdpConfiguration(configurationKeys.sbiIrisCaptureScore) ??
          process.env.REACT_APP_SBI_IRIS_CAPTURE_SCORE;
        bioSubType = irisBioSubtypes.split(",").map((x) => x.trim());
        break;
    }

    let request = {
      env: env,
      purpose: purpose,
      specVersion: specVersion,
      timeout: captureTimeout * 1000,
      captureTime: new Date().toISOString(),
      domainUri: SBI_DOMAIN_URI,
      transactionId: transactionId,
      bio: [
        {
          type: type, //modality
          count: count, // from configuration
          bioSubType: bioSubType,
          requestedScore: requestedScore, // from configuration
          deviceId: deviceId, // from discovery
          deviceSubId: 0, //Set as 0, not required for Auth capture.
          previousHash: "", // empty string
        },
      ],
      customOpts: null,
    };

    let endpoint = host + ":" + port + captureEndPoint;

    let response = await axios({
      method: mosip_CaptureMethod,
      url: endpoint,
      data: request,
      headers: {
        "Content-Type": "application/json",
      },
      timeout: captureTimeout * 1000,
    });

    return response?.data;
  };

  /**
   * Triggers MOSIPDISC request on multiple port simultaneously.
   * @param {url} host SBI is hosted on given host
   * @returns MOSIPDISC requests for the given host and the port ranging between fromPort and tillPort
   */
  mosipdisc_DiscoverDevicesAsync = async (host) => {
    clearDiscoveredDevices();
    clearDeviceInfos();

    const portRange =
      this.getIdpConfiguration(configurationKeys.sbiPortRange) ??
      process.env.REACT_APP_SBI_PORT_RANGE;
    const discTimeout =
      this.getIdpConfiguration(configurationKeys.sbiDISCTimeoutInSeconds) ??
      process.env.REACT_APP_SBI_DISC_TIMEOUT;
    const dinfoTimeout =
      this.getIdpConfiguration(configurationKeys.sbiDINFOTimeoutInSeconds) ??
      process.env.REACT_APP_SBI_DINFO_TIMEOUT;

    let ports = portRange.split("-").map((x) => x.trim());

    let fromPort = ports[0].trim();
    let tillPort = ports[1].trim();

    //port validations
    let portsValid = true;
    if (
      isNaN(fromPort) ||
      isNaN(tillPort) ||
      !(fromPort > 0) ||
      !(tillPort > 0) ||
      !(fromPort <= tillPort)
    ) {
      portsValid = false;
    }

    if (!portsValid) {
      //take default values
      fromPort = 4501;
      tillPort = 4510;
    }

    let discoverRequestList = [];
    for (let i = fromPort; i <= tillPort; i++) {
      discoverRequestList.push(discoverRequestBuilder(host, i, discTimeout, dinfoTimeout));
    }

    return axios.all(discoverRequestList);
  };

}

/**
 * Builds MOSIPDISC API request for multiple ports to discover devices on
 * the specifed host and port. On success response, discovered devices
 * are cached and MOSIPDINFO API is called to fetch deviceInfo.
 * @param {url} host SBI is hosted on given host
 * @param {int} port port on which SBI is listening to.
 * @returns MOSIPDISC request for the give host and port
 */
const discoverRequestBuilder = async (host, port, discTimeout, dinfoTimeout) => {
  let endpoint = host + ":" + port + deviceEndPoint;

  let request = {
    type: "Biometric Device",
  };

  return axios({
    method: mosip_DiscoverMethod,
    url: endpoint,
    data: request,
    timeout: discTimeout + 1000,
  })
    .then(async (response) => {
      if (response?.data !== null) {
        addDiscoveredDevices(port, response.data);
        await mosipdinfo_DeviceInfo(host, port, dinfoTimeout);
      }
    })
    .catch((error) => {
      //ignore
    });
};

/**
 * MOSIPDINFO API call for fetch deviceinfo from SBI on the specifed host and port
 * On success response, the device infos are decoded, validated and cached.
 * @param {url} host SBI is hosted on given host
 * @param {int} port port on which SBI is listening to.
 */
const mosipdinfo_DeviceInfo = async (host, port, dinfoTimeout) => {

  let endpoint = host + ":" + port + infoEndPoint;

  await axios({
    method: mosip_DeviceInfoMethod,
    url: endpoint,
    timeout: dinfoTimeout * 1000,
  })
    .then(async (response) => {
      if (response?.data !== null) {
        var decodedDeviceDetails = await decodeAndValidateDeviceInfo(
          response.data
        );
        addDeviceInfos(port, decodedDeviceDetails);
      }
    })
    .catch((error) => {
      //ignore
    });
};

/**
 * decodes and validates the JWT device info response from /deviceinfo api of SBI
 * @param {json Object} deviceInfo JWT response array from /deviceinfo api of SBI
 * @returns {Array<Object>} JWT decoded deviceInfo array
 */
const decodeAndValidateDeviceInfo = async (deviceInfoList) => {
  var deviceDetailList = [];
  for (let i = 0; i < deviceInfoList.length; i++) {
    var decodedDevice = await decodeJWT(deviceInfoList[i].deviceInfo);
    decodedDevice.digitalId = await decodeJWT(decodedDevice.digitalId);

    if (validateDeviceInfo(decodedDevice)) {
      deviceDetailList.push(decodedDevice);
    }
  }
  return deviceDetailList;
};

/**
 * validates the device info for device certification level, purpose and status.
 * @param {*} deviceInfo decoded deviceInfo
 * @returns {boolean}
 */
const validateDeviceInfo = (deviceInfo) => {
  if (
    deviceInfo.certification === certification &&
    deviceInfo.purpose === purpose &&
    deviceInfo.deviceStatus === DeviceStatusReady
  ) {
    return true;
  }
  return false;
};

/**
 * decode the JWT
 * @param {JWT} signed_jwt
 * @returns decoded jwt data
 */
const decodeJWT = async (signed_jwt) => {
  const data = await new jose.decodeJwt(signed_jwt);
  return data;
};


export default sbiService;
