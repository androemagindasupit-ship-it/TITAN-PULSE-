import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.titanpulse.app',
  appName: 'TITAN PULSE',
  webDir: 'www',
  bundledWebRuntime: false,
  android: {
    allowMixedContent: false,
    backgroundColor: '#05060a'
  }
};

export default config;
