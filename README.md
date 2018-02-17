# JeanEmotive-volume
work in progress
control android device volume based on engagement 
Using Emotive insight EEG headset to control the volume of the music on an android device based on the user’s engagement.

It features three main parts: signal acquisition, signal filtering, command execution.

## Filter
This example is built on the Emotive Community SDK Android FFT sample. The FFT sample pulls raw EEG and normalize it. In this code the EEG bands were used to calculate the engagement( beta/(alpha+Theta)) of a user. To continuously calculate engagement a rolling average of 5 samples are taken from 2 electrodes, af3 and af4. This example only uses two of the five electrodes to cut down on noise.

```java
for (int g = 0; g < data.length; g++) {
    if(data[g] > 100){
        //filter out noise, value needs to be less then 100
        goodSet = false;
    }
}
```

##Engagment Equation

A sample of five is thrown out if one of the values is greater than 100, it counts as noise. From the new 5 the new average is calculated for that electrode. For the engagement equatation

```java
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
```

##AudioManager
After the level of engagement is calculated, the volume seek bar is move with a given ratio. The movement triggers the on data-change listener and the Audio manger adjust the volume accordingly.

```java 
 if(((int)(getEngagment()*100))<100){

  	engagmentSeekBar.setProgress(((int)(getEngagment()*100)));
            }
            //how about nupdate current val based on ratio
            //if x + cuurent sound < max sound
            //you could write yor own volume to engagment ration
            volumeSeekbar.setProgress(12-(int)(getEngagment()*10));
            //else if engagment very smalll vibrate
      
```

##onProgressChanged
```java
audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
volumeSeekbar.setMax(audioManager
      .getStreamMaxVolume(AudioManager.STREAM_MUSIC));
volumeSeekbar.setProgress(audioManager
      .getStreamVolume(AudioManager.STREAM_MUSIC));
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
```

##Summery
1. filter out numbers that are to high
2. calculate real time engagement running means of signals
3. adjust the volume seek bar with a desired ratio/ speed to change the volume with respect to engagement

[learn more about AudioManager](https://developer.android.com/reference/android/media/AudioManager.html)
[Emotiv community-sdk](https://github.com/Emotiv/community-sdk)

