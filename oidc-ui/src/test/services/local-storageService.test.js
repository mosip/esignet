import localStorageService from '../../services/local-storageService';

describe('localStorageService', () => {
  beforeEach(() => {
    localStorage.clear();
    Object.defineProperty(document, 'cookie', {
      writable: true,
      value: 'testKey=testValue; anotherKey=anotherValue',
    });
  });

  describe('getCookie', () => {
    it('should return the cookie value when key exists', () => {
      expect(localStorageService.getCookie('testKey')).toBe('testValue');
    });

    it('should return empty string when key does not exist', () => {
      expect(localStorageService.getCookie('nonExistent')).toBe('');
    });
  });

  describe('addDeviceInfos', () => {
    it('should add device info to localStorage', () => {
      localStorageService.addDeviceInfos('1234', { name: 'DeviceA' });

      const stored = JSON.parse(localStorage.getItem('deviceInfo'));
      expect(stored['1234']).toEqual({ name: 'DeviceA' });
    });

    it('should update existing device info', () => {
      localStorage.setItem(
        'deviceInfo',
        JSON.stringify({ 1234: { name: 'Old' } })
      );
      localStorageService.addDeviceInfos('1234', { name: 'New' });

      const stored = JSON.parse(localStorage.getItem('deviceInfo'));
      expect(stored['1234']).toEqual({ name: 'New' });
    });
  });

  describe('getDeviceInfos', () => {
    it('should return parsed deviceInfo', () => {
      localStorage.setItem(
        'deviceInfo',
        JSON.stringify({ 5678: { name: 'DeviceB' } })
      );
      const result = localStorageService.getDeviceInfos();
      expect(result['5678']).toEqual({ name: 'DeviceB' });
    });
  });

  describe('clearDeviceInfos', () => {
    it('should remove deviceInfo from localStorage', () => {
      localStorage.setItem('deviceInfo', JSON.stringify({ 9999: {} }));
      localStorageService.clearDeviceInfos();
      expect(localStorage.getItem('deviceInfo')).toBeNull();
    });

    it("should do nothing if deviceInfo doesn't exist", () => {
      expect(() => localStorageService.clearDeviceInfos()).not.toThrow();
    });
  });

  describe('addDiscoveredDevices', () => {
    it('should add discovered devices to localStorage', () => {
      localStorageService.addDiscoveredDevices('5678', ['dev1', 'dev2']);
      const stored = JSON.parse(localStorage.getItem('discover'));
      expect(stored['5678']).toEqual(['dev1', 'dev2']);
    });

    it('should update existing discovered devices', () => {
      localStorage.setItem('discover', JSON.stringify({ 5678: ['oldDev'] }));
      localStorageService.addDiscoveredDevices('5678', ['newDev']);
      const stored = JSON.parse(localStorage.getItem('discover'));
      expect(stored['5678']).toEqual(['newDev']);
    });
  });

  describe('clearDiscoveredDevices', () => {
    it('should remove discover from localStorage', () => {
      localStorage.setItem('discover', JSON.stringify({ 1111: {} }));
      localStorageService.clearDiscoveredDevices();
      expect(localStorage.getItem('discover')).toBeNull();
    });

    it("should do nothing if discover doesn't exist", () => {
      expect(() => localStorageService.clearDiscoveredDevices()).not.toThrow();
    });
  });
});
