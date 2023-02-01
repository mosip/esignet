const device_info_keyname = "deviceInfo";
const discover_keyname = "discover";

/**
 * Clear the cache of discovered devices
 */
const clearDiscoveredDevices = () => {
  if (localStorage.getItem(discover_keyname)) {
    localStorage.removeItem(discover_keyname);
  }
};

/**
 * Clear the cache of deviceInfo
 */
const clearDeviceInfos = () => {
  if (localStorage.getItem(device_info_keyname)) {
    localStorage.removeItem(device_info_keyname);
  }
};

/**
 * cache discoveredDevices against the port no.
 * @param {int} port
 * @param {*} discoveredDevices
 */
const addDiscoveredDevices = (port, discoveredDevices) => {
  let discover = {};

  //initialize if empty
  if (!localStorage.getItem(discover_keyname)) {
    localStorage.setItem(discover_keyname, JSON.stringify(discover));
  }

  discover = JSON.parse(localStorage.getItem(discover_keyname));
  discover[port] = discoveredDevices;
  localStorage.setItem(discover_keyname, JSON.stringify(discover));
};

/**
 * cache deviceInfo against the port no.
 * @param {int} port
 * @param {*} decodedDeviceInfo
 */
const addDeviceInfos = (port, decodedDeviceInfo) => {
  let deviceInfo = {};

  //initialize if empty
  if (!localStorage.getItem(device_info_keyname)) {
    localStorage.setItem(device_info_keyname, JSON.stringify(deviceInfo));
  }
  deviceInfo = JSON.parse(localStorage.getItem(device_info_keyname));
  deviceInfo[port] = decodedDeviceInfo;
  localStorage.setItem(device_info_keyname, JSON.stringify(deviceInfo));
};

/**
 * @returns deviceInfoList
 */
const getDeviceInfos = () => {
  return JSON.parse(localStorage.getItem(device_info_keyname));
};

/**
 * retrieves cookie from the browser 
 * @param {string} key
 * @returns cookie value
 */
function getCookie(key) {
  var b = document.cookie.match("(^|;)\\s*" + key + "\\s*=\\s*([^;]+)");
  return b ? b.pop() : "";
}

const localStorageService = {
  addDeviceInfos: addDeviceInfos,
  getDeviceInfos: getDeviceInfos,
  clearDeviceInfos: clearDeviceInfos,
  clearDiscoveredDevices: clearDiscoveredDevices,
  addDiscoveredDevices: addDiscoveredDevices,
  getCookie: getCookie
};

export default localStorageService;
