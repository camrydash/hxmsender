package com.acecorp.hxmsender.activities;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import com.acecorp.hxmsender.R;
import com.acecorp.hxmsender.R.id;
import com.acecorp.hxmsender.R.layout;
import com.acecorp.hxmsender.R.menu;

import com.acecorp.hxmsender.models.BLEGattConstants;
import com.acecorp.hxmsender.models.BLEGattConstants.Characteristics;
import com.acecorp.hxmsender.server.HxmServer;
import com.acecorp.hxmsender.server.HxmServerCallback;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private final int DEVICE_REQUEST_ENABLE_BT = 2;
	private final long DEVICE_SCAN_TIMEOUT_PERIOD = 60000;
	private final int TCP_SERVER_PORT = 6622;
	
	private BluetoothManager mBluetoothManager = null;
	private BluetoothAdapter mBluetoothAdapter = null;
	private BluetoothDevice mZephyrDevice = null;
	
	private final Handler mHandler = new Handler();
	private final Object mLock = new Object();
	
	private TextView mHrText = null;
	private TextView mBatteryText = null;
	
    private BluetoothGatt mBluetoothGatt = null;
    private int mConnectionState = STATE_DISCONNECTED;

    static final int STATE_DISCONNECTED = 0;
    static final int STATE_CONNECTING = 1;
    static final int STATE_CONNECTED = 2;
    
    private final ArrayList<Integer> mHrValues = new ArrayList<Integer>();
    
    private int mBatterylevelRequest = -1;
    private int mHrValueCount =0;
    //private final static ArrayList<int> HR_VAL = new ArrayList<int>();
	
    public final static String ACTION_GATT_CONNECTED =
            "com.acecorp.hxmsender.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
    		"com.acecorp.hxmsender.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
    		"com.acecorp.hxmsender.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
    		"com.acecorp.hxmsender.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
    		"com.acecorp.hxmsender.bluetooth.le.EXTRA_DATA";

	private static final String TAG = "hxmbluetooth";
	
	private HxmServer hxmappserver = null;
    	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);	
		
		mHrText = ((TextView)findViewById(R.id.hr_text_view));
		mHrText.setText("E");
		
		mBatteryText = ((TextView)findViewById(R.id.battery_text_view));
		mBatteryText.setText("-");
		
		//mConsoleText = ((TextView)findViewById(R.id.hr_console_text_view));
	}
	
	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();
	}
	
	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();		
		
		if(true) {
			/**
			 * Disable notifications (data stream) to conserve power while not in use.
			 */
			disableCharastericNotification(BLEGattConstants.Service.HEART_RATE, BLEGattConstants.Characteristics.HEART_RATE_MEASUREMENT);
			disableCharastericNotification(BLEGattConstants.Service.BATTERY_SERVICE, BLEGattConstants.Characteristics.BATTERY_LEVEL_CHARACTERISTIC);
			mHrText.setText("-");
			if(hxmappserver != null) {
				hxmappserver.send("P");
			}
			flush_hr_values();
		}
	}
	
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		if(mZephyrDevice == null) {
			synchronized(mLock) {
				if(mZephyrDevice == null) {
					init();
				}
			}
		}
		else {
			if(mZephyrDevice != null) {
				//onDeviceFound(mZephyrDevice);
				onDeviceResume();
			} else {
				Log.w(TAG, "Unable to locate device instance");
			}
		}
	}
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		if(mBluetoothGatt != null) {
			mBluetoothGatt.disconnect();
			mBluetoothGatt = null;
		}
		if(hxmappserver != null) {
			hxmappserver.stop();
		}
		mTimer.cancel();
		mTimer=null;
	}
	

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private void init() {
		
		mBluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		if(mBluetoothManager != null ) {
			mBluetoothAdapter= mBluetoothManager.getAdapter();
		}
		
		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
		    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    startActivityForResult(enableBtIntent, DEVICE_REQUEST_ENABLE_BT);
		}
		
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
		    Toast.makeText(this, "BLE is not supported.", Toast.LENGTH_SHORT).show();
		    finish();
		}
		else
		{
			Toast.makeText(this, "Mate 64!", Toast.LENGTH_LONG).show();
		}  
		
		/**
		 * Stops scanning after 10 seconds
		 */
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
            	if(mBluetoothAdapter.isDiscovering()) {
            		Log.d(TAG, "adapterIsDiscovering");
            		while(mBluetoothAdapter.isDiscovering()) {
            			try {
							Thread.sleep(5000);
							Log.d(TAG, "Sleep...");
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
            		}
            	}
            	if(mZephyrDevice == null) {
	            	synchronized(mLock) {
		            	if(mZephyrDevice == null) {
		            		mBluetoothAdapter.stopLeScan(mLeScanCallback);    
		            		onDeviceScanTimeout();
		            	}
	            	}
            	}
            }
        }, DEVICE_SCAN_TIMEOUT_PERIOD);
        Log.d(TAG, "Starting LE Scan");
		mBluetoothAdapter.startLeScan(mLeScanCallback);
	}
	
	final BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
		   if(device != null) {
			   Log.d(TAG, "Found Bluetooth LE: " + device.getAddress());
			   if(device.getName() != null && device.getName().startsWith("Zephyr")) {
				   Log.d(TAG, device.getName());
				   onDeviceFound(device);
			   }
		   }
		}
	};
	
	private void onDeviceResume() {
		if(mBluetoothGatt != null) {
			enableCharacteristicNotification(mBluetoothGatt, BLEGattConstants.Service.HEART_RATE, BLEGattConstants.Characteristics.HEART_RATE_MEASUREMENT);
			//enableCharacteristicNotification(mBluetoothGatt, BLEGattConstants.Service.BATTERY_SERVICE, BLEGattConstants.Characteristics.BATTERY_LEVEL_CHARACTERISTIC);
		} else {
			mBluetoothGatt = mZephyrDevice.connectGatt(this, true, mGattCallback);
		}
	}
	
	private void onDeviceFound(BluetoothDevice device) {	
		
		final HxmServerCallback callback = new HxmServerCallback() {
			@Override
			public void onStarted() {
				Log.d(TAG, "Server started...");
			}
			@Override
			public void onDisconnected() {
				Log.d(TAG, "Server disconnected...");
			}
			@Override
			public void onDataSend(String msg) {
				Log.d(TAG, "Server transmitting data...: " + msg);
			}
		};
		
		if(mZephyrDevice==null) {
			synchronized(mLock) {
				if(mZephyrDevice==null) {
					mBluetoothAdapter.stopLeScan(mLeScanCallback);		
					mZephyrDevice = device;
					if(hxmappserver == null) {
						hxmappserver = new HxmServer(TCP_SERVER_PORT);
						if(!hxmappserver.isRunning()) {
							hxmappserver.start(callback);
						}else {
							Log.d(TAG, "Server is already running.");
						}
					} else {
						Log.w(TAG, "hxmappserver already initialized.");
					}
					mBluetoothGatt = mZephyrDevice.connectGatt(this, true, mGattCallback);
				}
			}
		}
	}
	
    private final BluetoothGattCallback mGattCallback =
            new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:" +
                		gatt.discoverServices());
    	        runOnUiThread(new Runnable() {
    				@Override
    				public void run() {
    					mHrText.setText("C");
    					Log.d(TAG, "Connected to device. Attempting to stream data.");
    				}
    			});

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
    	        runOnUiThread(new Runnable() {
    				@Override
    				public void run() {
    					mHrText.setText("D");
    					Log.d(TAG, "Failed to connect to device. Try reopening app.");
    				}
    			});
    	        try {
    	        	gatt.close();
    	        }
    	        catch (Exception e) {
    	            Log.d(TAG, "close ignoring: " + e);
    	          }
            }
        }

        @Override
        // New services discovered
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            	Log.d(TAG, "onServicesDiscovered");
            	scheduleBatteryTask(gatt);
            	//enableCharacteristicNotification(gatt, BLEGattConstants.Service.BATTERY_SERVICE, BLEGattConstants.Characteristics.BATTERY_LEVEL_CHARACTERISTIC);
            	broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }
        
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic)
        {
        	broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }  

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic,
                int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            	Log.i(TAG, "onCharacteristicRead");
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            } else {
                Log.w(TAG, "onCharacteristicRead received: " + status);
            }
        }
    };
	
	private void onDeviceScanTimeout() {
		Toast.makeText(this, "Timeout in finding Zephyr HxM Sensor.", Toast.LENGTH_SHORT).show();
		Log.d(TAG, "Timeout in finding Zephyr HxM Sensor.");
		finish();
	}
	
	private Timer mTimer = new Timer();
	private void scheduleBatteryTask(final BluetoothGatt gatt) {
		getBatteryLevel(gatt);
		/**
		 * Schedule every half hour
		 */
		mTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				getBatteryLevel(gatt);
			}
		}, 1800*1000);
	}
	
	private void getBatteryLevel(BluetoothGatt gatt) {
		Log.i(TAG, "retrieving battery level.");
		if(gatt == null) {
			Log.d(TAG, "mBluetoothGatt not initialized.");
			return;
		}
		
		BluetoothGattService service= gatt.getService(BLEGattConstants.Service.BATTERY_SERVICE);
		if(service==null) {
			Log.d(TAG, "Unable to retrieve battery service.");
			return;
		}
		
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(BLEGattConstants.Characteristics.BATTERY_LEVEL_CHARACTERISTIC);
		if(characteristic==null) {
			Log.d(TAG, "Battery level characteristic not available.");
			return;
		}
		
		boolean result = gatt.readCharacteristic(characteristic);
    	if(!result) {
    		Log.d(TAG, "reading battery characteristic failed!");
    	}
	}
	
	private void enableCharacteristicNotification(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID) {
		final BluetoothGattService service =
				gatt.getService(serviceUUID);
				
		if(service == null) {
			Log.w(TAG, "Heart Rate service unavailable");
			return;
		}
		
		Log.d(TAG, "Found Heart Rate Service");
		
		final BluetoothGattCharacteristic characteristic = service
				.getCharacteristic(characteristicUUID);
    	
    	
    	boolean result = gatt.setCharacteristicNotification(characteristic, true);
    	if(!result) {
    		Log.d(TAG, "Enabling characteristic notification failed!");
    	}
			   
    	BluetoothGattDescriptor descriptor = 
    			characteristic.getDescriptor(BLEGattConstants.Descriptors.CLIENT_CHARACTERISTIC_CONFIGURATION);
    	if(descriptor != null) {
    		descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
    		gatt.writeDescriptor(descriptor);
    		Log.d(TAG, "Enabled notifications");
    	} else {
    		Log.d(TAG, "Failed to get descriptor!");
    	}
	}
	
	private void disableCharastericNotification(UUID serviceUUID, UUID characteristicUUID) {
		if(mBluetoothGatt != null) {
			final BluetoothGattService service =
					mBluetoothGatt.getService(serviceUUID);
					
			if(service == null) {
				Log.w(TAG, "Heart Rate service unavailable");
				return;
			}
			
			final BluetoothGattCharacteristic characteristic = service
					.getCharacteristic(characteristicUUID);
			
			boolean result = mBluetoothGatt.setCharacteristicNotification(characteristic, false);
			if(!result) {
				Log.w(TAG, "Failed to disable characteristic notification");
				return;
			}
			
			final BluetoothGattDescriptor descriptor =
					characteristic.getDescriptor(BLEGattConstants.Descriptors.CLIENT_CHARACTERISTIC_CONFIGURATION);
			if(descriptor ==null) {
				Log.w(TAG, "Failed to get client characteristic configuration descriptor");
				return;
			}
			
			descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
			Log.d(TAG, "Disabled notifications");
		}
	}
	
	private void broadcastUpdate(final String action) {
	    final Intent intent = new Intent(action);
	    sendBroadcast(intent);
	}
	
	public static String getDateFormattedString(Date date, String format) {
		SimpleDateFormat dateFormat = new SimpleDateFormat(format);
		return dateFormat.format(date);
	}

	/**
	 * Flushes HR ints into a csv file
	 */
	void flush_hr_values() {
		synchronized (mLock) {
			if(mHrValues.size()==0) return;
			SimpleDateFormat dateFormat = new SimpleDateFormat("dd_MM_yyyy_HHmmss");
			String fmt= dateFormat.format(new Date());
			
	        File f = new File("hxmhr" + fmt + ".csv");
	        BufferedWriter bw=null;
	        
        	try {
        		if(!f.exists()) 
        			f.createNewFile();					
				bw = new BufferedWriter(new FileWriter(f, true));
		        for(Integer x: mHrValues) {
					bw.write(x.toString());
		        }
				bw.flush();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} finally {
				if(bw!=null)
					try {
						bw.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				mHrValues.clear();
			}
        	String path = f.getAbsolutePath();
        	Toast.makeText(this, "Save=" + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
		}
	}
	
	private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
	    final Intent intent = new Intent(action);
	    
	    // This is special handling for the Heart Rate Measurement profile. Data
	    // parsing is carried out as per profile specifications.
	    if (BLEGattConstants.Characteristics.HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
	        int flag = characteristic.getProperties();
	        int format = -1;
	        if ((flag & 0x01) != 0) {
	            format = BluetoothGattCharacteristic.FORMAT_UINT16;
	            //Log.d(TAG, "Heart rate format UINT16.");
	        } else {
	            format = BluetoothGattCharacteristic.FORMAT_UINT8;
	            //Log.d(TAG, "Heart rate format UINT8.");
	        }
	        final int heartRate = characteristic.getIntValue(format, 1);
	        synchronized (mLock) {
	        	mHrValues.add(heartRate);
			}
	        
	        	        
	        //
	        //Log.d(TAG, String.format("Received heart rate: %d", heartRate));
	        //findViewById(R.id);
	        intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
	        runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mHrText.setText(String.valueOf(heartRate));
				}
			});
	        if(hxmappserver != null) {
	        	hxmappserver.send(String.valueOf(heartRate));
	        } else {
	        	Log.w(TAG, "server not running. Cannot transmit data...");
	        }
	    }
	    else if (BLEGattConstants.Characteristics.BATTERY_LEVEL_CHARACTERISTIC.equals(characteristic.getUuid())) {
	    	final int batterylevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
	        runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mBatteryText.setText(String.valueOf(batterylevel)+"%");
					mBatteryText.setBackgroundColor(batterylevel > 80 ? Color.GREEN : (batterylevel > 40 ? Color.YELLOW : Color.RED));
				}
			});
	        
	        if(mBatterylevelRequest==-1) {
		        synchronized (mLock) {
		        	if(mBatterylevelRequest==-1) {
		        		enableCharacteristicNotification(mBluetoothGatt, BLEGattConstants.Service.HEART_RATE, BLEGattConstants.Characteristics.HEART_RATE_MEASUREMENT);
		        		mBatterylevelRequest=0;
		        	}
				}
	        }
	        
	    }	    
	    else {
	        // For all other profiles, writes the data formatted in HEX.
	        final byte[] data = characteristic.getValue();
	        if (data != null && data.length > 0) {
	            final StringBuilder stringBuilder = new StringBuilder(data.length);
	            for(byte byteChar : data)
	                stringBuilder.append(String.format("%02X ", byteChar));
	            intent.putExtra(EXTRA_DATA, new String(data) + "\n" +
	                    stringBuilder.toString());
	        }
	    }
	    sendBroadcast(intent);
	}
}
