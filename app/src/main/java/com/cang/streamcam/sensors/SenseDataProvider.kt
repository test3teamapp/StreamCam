package com.cang.streamcam.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import com.cang.streamcam.ConnectionHandler

class SenseDataProvider private constructor(ctx:Context): SensorEventListener{

    private lateinit var sensorManager: SensorManager
    private lateinit var mainContext: Context
    private  var hasGyro:Boolean = false

    init {
        mainContext = ctx
        sensorManager = mainContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    fun findSensors():Boolean{
        if (sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
            // Success! There's a magnetometer.
            Log.d(TAG,"Found Gyroscope")
            hasGyro = true
        } else {
            // Failure! No magnetometer.
        }
        return hasGyro
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do something here if sensor accuracy changes.
    }

    override fun onSensorChanged(event: SensorEvent) {

        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            // The light sensor returns a single value.
            // Many sensors return 3 values, one for each axis.
            //val lux = event.values[0]
            // Do something with this sensor value.
            // TODO sent sensor data IDENTIFY which of the 3 is needed for our purposes
            ConnectionHandler.getInstance().sendUDP("x_rad:" + event.values[0].toString())
            // TODO update StreamCamReceiver.py to receive the "x_rad/sec:xxx" data
        }
    }




    companion object {
        private const val TAG = "SenseDataProvider"
        private var singletonObject : SenseDataProvider? = null

        fun getInstance(ctx:Context): SenseDataProvider {
            if (singletonObject == null) {
                singletonObject = SenseDataProvider(ctx)
            }
            return singletonObject as SenseDataProvider
        }
    }
}