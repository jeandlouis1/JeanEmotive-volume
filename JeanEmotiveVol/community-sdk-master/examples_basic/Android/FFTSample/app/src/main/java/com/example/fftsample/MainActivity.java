package com.example.fftsample;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.LinkedList;

import com.example.fftsample.R;

import android.media.AudioManager;
import android.os.Build;
import android.os.Vibrator;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.content.pm.PackageManager;
import android.Manifest;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;

import com.emotiv.insight.IEdk;
import com.emotiv.insight.IEdkErrorCode;
import com.emotiv.insight.IEdk.IEE_DataChannel_t;
import com.emotiv.insight.IEdk.IEE_Event_t;;

public class MainActivity extends Activity {
	//Theta ,Alpha ,Low beta ,High beta , Gamma
	private TextView Theta;
	private TextView Alpha;
	private TextView LowBeta;
	private TextView HighBeta;
	private TextView Gamma;
	private TextView engagment; // calculated value

	private Thread processingThread;
    //get bluetooth permissions
	private static final int REQUEST_ENABLE_BT = 1;
	private static final int MY_PERMISSIONS_REQUEST_BLUETOOTH = 0;
	private BluetoothAdapter mBluetoothAdapter;
	private NumberFormat formatter;
	private boolean lock = false;
	private boolean isEnablGetData = false;
	private boolean isEnableWriteFile = false;
	int userId;
	private BufferedWriter motion_writer;
	//moving averages to calculate the engagement in real time.
	private MovingAverage Theta_RA;
	private MovingAverage Alpha_RA;
	private MovingAverage LowBeta_RA;
	private MovingAverage HighBeta_RA;
	private MovingAverage Gamma_RA;

	Button Start_button,Stop_button;
	IEE_DataChannel_t[] Channel_list = {IEE_DataChannel_t.IED_AF3, IEE_DataChannel_t.IED_T7,IEE_DataChannel_t.IED_Pz,
			IEE_DataChannel_t.IED_T8,IEE_DataChannel_t.IED_AF4};
	String[] Name_Channel = {"AF3","T7","Pz","T8","AF4"};

//**********************************************************
	//alerts
	private SeekBar volumeSeekbar = null;
	private SeekBar engagmentSeekBar= null;
	private AudioManager audioManager = null;
	//private Vibrator vibrator; //not for tablets
//**********************************************************
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		final BluetoothManager bluetoothManager =
				(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			/***Android 6.0 and higher need to request permission*****/
			if (ContextCompat.checkSelfPermission(this,
					Manifest.permission.ACCESS_FINE_LOCATION)
					!= PackageManager.PERMISSION_GRANTED) {

				ActivityCompat.requestPermissions(this,
						new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
						MY_PERMISSIONS_REQUEST_BLUETOOTH);
			}
			else{
				checkConnect();
			}
		}
		else {
			checkConnect();
		}

		Start_button = (Button)findViewById(R.id.startbutton);
		Stop_button  = (Button)findViewById(R.id.stopbutton);


		//Theta ,Alpha ,Low beta ,High beta , Gamma
		Theta =(TextView)findViewById(R.id.textView1);
		Alpha =(TextView)findViewById(R.id.textView2);
		LowBeta =(TextView)findViewById(R.id.textView3);
		HighBeta =(TextView)findViewById(R.id.textView4);
		Gamma =(TextView)findViewById(R.id.textView5);
		engagment = (TextView)findViewById(R.id.engagment);
        //init size 5 buffer
		Theta_RA = new MovingAverage(5);
		Alpha_RA = new MovingAverage(5);
		LowBeta_RA = new MovingAverage(5);
		HighBeta_RA = new MovingAverage(5);
		Gamma_RA = new MovingAverage(5);
		formatter = new DecimalFormat("#0.0000");

		Start_button.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				Log.e("FFTSample","Start Write File");
				//setDataFile();
				isEnableWriteFile = true;
			}
		});
		Stop_button.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				Log.e("FFTSample","Stop Write File");
                //StopWriteFile();
				isEnableWriteFile = false;
			}
		});

		processingThread=new Thread()
		{
			@Override
			public void run() {
				// TODO Auto-generated method stub
				super.run();
				while(true)
				{
					try
					{
						handler.sendEmptyMessage(0);
						handler.sendEmptyMessage(1);
						if(isEnablGetData && isEnableWriteFile)handler.sendEmptyMessage(2);
						Thread.sleep(5);
					}
					
					catch (Exception ex)
					{
						ex.printStackTrace();
					}
				}
			}
		};
		//vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
		initControls();
		processingThread.start();


	}
	
	Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			TextView[] sensors= {Theta,Alpha,LowBeta,HighBeta,Gamma};
            //**********************************************************************************
            //array of averages
			MovingAverage [] averages ={Theta_RA,Alpha_RA,LowBeta_RA,HighBeta_RA,Gamma_RA};
			switch (msg.what) {

                case 0:
                        int state = IEdk.IEE_EngineGetNextEvent();
                        if (state == IEdkErrorCode.EDK_OK.ToInt()) {
                            int eventType = IEdk.IEE_EmoEngineEventGetType();
                            userId = IEdk.IEE_EmoEngineEventGetUserId();
                            if(eventType == IEE_Event_t.IEE_UserAdded.ToInt()){
                                Log.e("SDK","User added");
                                IEdk.IEE_FFTSetWindowingType(userId, IEdk.IEE_WindowsType_t.IEE_BLACKMAN);
                                isEnablGetData = true;
                            }
                            if(eventType == IEE_Event_t.IEE_UserRemoved.ToInt()){
                                Log.e("SDK","User removed");
                                isEnablGetData = false;
                            }
                        }

                        break;
                case 1:
                    /*Connect device with Insight headset*/
                    int number = IEdk.IEE_GetInsightDeviceCount();
                    if(number != 0) {
                        if(!lock){
                            lock = true;
                            IEdk.IEE_ConnectInsightDevice(0);
                        }
                    }
                    /**************************************/
                    /*Connect device with Epoc Plus headset*/
    //				int number = IEdk.IEE_GetEpocPlusDeviceCount();
    //				if(number != 0) {
    //					if(!lock){
    //						lock = true;
    //						IEdk.IEE_ConnectEpocPlusDevice(0,false);
    //					}
    //				}
                    /**************************************/
                    else lock = false;
                    break;
    //*************************************************************************************
                case 2:
                    //simple basic filter
                    //if(motion_writer == null) return;
                    boolean goodSet = true;
                    //get 5 samples from each EEG channels
                    for(int i=0; i < Channel_list.length; i++) {
                        double[] data = IEdk.IEE_GetAverageBandPowers(Channel_list[i]);
                         goodSet = true;

                        /*
                        After receiving 5  samples in from a perticular channel
                        you loop through the five and see if all points are less 100 if not you
                        you discard the set
                        */
                        if(data.length == 5){
                            for (int g = 0; g < data.length; g++) {
                                if(data[g] > 100){
                                    //filter out noise, value needs to be less then 100
                                    goodSet = false;
                                }
                            }

                        }
                        if(data.length == 5){
                            try {
                                motion_writer.write(Name_Channel[i] + ",");
                                for(int j=0; j < data.length;j++){
                                    addData(data[j]);
                                    //if num>100   extract row
                                    //use the two front sensors
                                    //theta /beta drowsnyii
                                    if((i==0||i==4)&& goodSet){ //af3 and af4 two front sensors
                                sensors[j].setText(" "+formatter.format(averages[j].next(data[j])));
                                    }
                                }

                                //set text
                                motion_writer.newLine();
                            } catch (IOException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }
                        engagment.setText("Engagment: "+((int)(getEngagment()*100)));
                        //formatter.format(getEngagment())

                        //number <100%
                        if(((int)(getEngagment()*100))<100){

                        engagmentSeekBar.setProgress(((int)(getEngagment()*100)));
                        }
                        //how about nupdate current val based on ratio
                        //if x + cuurent sound < max sound
                        //you could write yor own volume to engagment ration
                        volumeSeekbar.setProgress(12-(int)(getEngagment()*10));
                        //else if engagment very smalll vibrate
                    }

                    break;
            }

		}

	};
//******************************************************************************************

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   String permissions[], int[] grantResults) {
		switch (requestCode) {
			case MY_PERMISSIONS_REQUEST_BLUETOOTH: {
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0
						&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					checkConnect();

				} else {

					// permission denied, boo! Disable the
					// functionality that depends on this permission.
					Toast.makeText(this, "App can't run without this permission", Toast.LENGTH_SHORT).show();
				}
				return;
			}

		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		if (requestCode == REQUEST_ENABLE_BT) {
			if(resultCode == Activity.RESULT_OK){
				//Connect to emoEngine
				IEdk.IEE_EngineConnect(this,"");
			}
			if (resultCode == Activity.RESULT_CANCELED) {
				Toast.makeText(this, "You must be turn on bluetooth to connect with Emotiv devices"
						, Toast.LENGTH_SHORT).show();
			}
		}
	}



	private void setDataFile() {
		try {
			String eeg_header = "Channel , Theta ,Alpha ,Low beta ,High beta , Gamma ";
			File root = Environment.getExternalStorageDirectory();
			String file_path = root.getAbsolutePath()+ "/FFTSample/";
			File folder=new File(file_path);
			if(!folder.exists())
			{
				folder.mkdirs();
			}		
			motion_writer = new BufferedWriter(new FileWriter(file_path+"bandpowerValue.csv"));
			motion_writer.write(eeg_header);
			motion_writer.newLine();
		} catch (Exception e) {
			Log.e("","Exception"+ e.getMessage());
		}
	}
	private void StopWriteFile(){
		try {
			motion_writer.flush();
			motion_writer.close();
			motion_writer = null;
		} catch (Exception e) {
			// TODO: handle exception
		}
	}
	/**
	 * public void addEEGData(Double[][] eegs) Add EEG Data for write int the
	 * EEG File
	 * 
	 * //@param eegs
	 *            - double array of eeg data
	 */
	public void addData(double data) {

		if (motion_writer == null) {
			return;
		}

			String input = "";
				input += (String.valueOf(data) + ",");
			try {
				motion_writer.write(input);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

	}

	public double getEngagment(){
		//MovingAverage [] averages ={Theta_RA,Alpha_RA,LowBeta_RA,HighBeta_RA,Gamma_RA};
		double betas = HighBeta_RA.getAvg();
		double alphas = Alpha_RA.getAvg();
		double thetas = Theta_RA.getAvg();

		if((alphas+thetas)!= 0){
			return betas/(alphas+thetas);
		}
		else{
			return 0;
		}
	}

	public class MovingAverage {
		LinkedList<Double> queue;
		int size;
		private double avg;

		/** Initialize your data structure here. */
		public MovingAverage(int size) {
			this.queue = new LinkedList<Double>();
			this.size = size;
		}

		public double getAvg() {
			return avg;
		}

		public double next(double val) {
			if(queue.size()<this.size){
				queue.offer(val);
				double sum=0;
				for(double i: queue){
					sum+=i;
				}
				this.avg = sum/queue.size();

				return this.avg;
			}else{
				double head = queue.poll();
				double minus = head/this.size;
				queue.offer(val);
				double add = val/this.size;
				this.avg = avg + add - minus;
				return this.avg;
			}
		}
	}

	private void checkConnect(){
		if (!mBluetoothAdapter.isEnabled()) {
			/****Request turn on Bluetooth***************/
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
		else
		{
			//Connect to emoEngine
			IEdk.IEE_EngineConnect(this,"");
		}
	}


	//alerts
	private void initControls()
	{
		try
		{
			volumeSeekbar = (SeekBar)findViewById(R.id.seekBar);
			engagmentSeekBar = (SeekBar)findViewById(R.id.seekBar2);
			engagmentSeekBar.setMax(100);
			audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			volumeSeekbar.setMax(audioManager
					.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
			volumeSeekbar.setProgress(audioManager
					.getStreamVolume(AudioManager.STREAM_MUSIC));



			volumeSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
			{
				@Override
				public void onStopTrackingTouch(SeekBar arg0)
				{
				}

				@Override
				public void onStartTrackingTouch(SeekBar arg0)
				{
				}

				@Override
				public void onProgressChanged(SeekBar arg0, int progress, boolean arg2)
				{
					audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
							progress, 0);
					if(progress <(volumeSeekbar.getMax()*.10)){
						/*if(vibrator.hasVibrator()){
							vibrator.vibrate(1000);
						}*/

					}
				}

			});




			engagmentSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
			{
				@Override
				public void onStopTrackingTouch(SeekBar arg0)
				{
				}

				@Override
				public void onStartTrackingTouch(SeekBar arg0)
				{
				}

				@Override
				public void onProgressChanged(SeekBar arg0, int progress, boolean arg2)
				{
					//audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
					//		progress, 0);
					if(progress <(volumeSeekbar.getMax()*.10)){
						/*if(vibrator.hasVibrator()){
							vibrator.vibrate(1000);
						}*/

					}
				}

			});

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}



	}

}
