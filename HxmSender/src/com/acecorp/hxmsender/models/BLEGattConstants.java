package com.acecorp.hxmsender.models;

import java.util.UUID;

public class BLEGattConstants {
	
	public static class Service {
		public final static UUID HEART_RATE =
				UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
		public final static UUID BATTERY_SERVICE =
				UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
	}
	
	public static class Characteristics {
		public final static UUID HEART_RATE_MEASUREMENT =
				UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
		public final static UUID BATTERY_LEVEL_CHARACTERISTIC =
				UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
	}
	
	public static class Descriptors {
		public final static UUID CLIENT_CHARACTERISTIC_CONFIGURATION =
				UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	}
}
