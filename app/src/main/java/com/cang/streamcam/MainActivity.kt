package com.cang.streamcam

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cang.streamcam.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.cang.streamcam.Utils.BitmapUtils
import com.cang.streamcam.Utils.NetUtils
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer

//typealias LumaListener = (luma: Double) -> Unit
typealias StreamListener = (success: Boolean, jpgbytes: ByteArray) -> Unit


class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    private var startCountFrameMillSecs = System.currentTimeMillis()
    private var numOfFrames = 0
    private lateinit var connectedIpArray: ArrayList<String>
    private lateinit var broadcastIP: InetAddress
    private lateinit var imageClientIP: InetAddress
    private var isImageClientIPIdentified = false
    private var udpSocket = DatagramSocket()
    private var tcpSocket = Socket()

    @SuppressLint("MissingSuperCall")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        //super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // get addresses of connected devices
        connectedIpArray = NetUtils.getArpLiveIps(true)
        Log.d(TAG, "Connected ips : $connectedIpArray")
        // get broadcast address net
        broadcastIP =
            NetUtils.getBroadcast(NetUtils.getIpAddress())//InetAddress.getByName("192.168.1.12")//NetUtils.getBroadcast(NetUtils.getIpAddress())
        Log.d(TAG, "Connected ips : $connectedIpArray")
        // open udp socket
        openUDPSocket(broadcastIP)
        /*
        if (isImageClientIPIdentified) {
            openTCPSocket(imageClientIP)
        }
        */
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }
        viewBinding.fpsTextview.setText("fps");

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun openUDPSocket(inetAddress: InetAddress) {
        try {
            //Open a port to send the package
            if (udpSocket == null){
                udpSocket = DatagramSocket()
            }
            if (udpSocket != null) {
                Log.d(TAG, "openUDPSocket: socket not null")
                if (udpSocket.isClosed) {
                    Log.d(TAG, "openUDPSocket: socket was closed")
                    udpSocket = DatagramSocket()
                    udpSocket.broadcast = true
                } else if (!udpSocket.isConnected) {
                    Log.d(TAG, "openUDPSocket: socket was not connected")
                    udpSocket = DatagramSocket()
                    //udpSocket.connect(inetAddress, UDP_PORT)
                    udpSocket.broadcast = true
                }
                // get remote addresses of image clients
                //sendUDP("tcp")
            }
        } catch (e: Exception) {
            Log.e(TAG, "openUDPSocket: Exception: " + e.message)
        }
    }

    private fun closeUDPSocket() {
        try {
            //Close the port
            if (!udpSocket.isClosed) {
                udpSocket.disconnect()
                udpSocket.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "closeUDPSocket: IOException: " + e.message)
        }
    }

    private fun openTCPSocket(inetAddress: InetAddress) {
        var retries = 0
        val maxRetries = 1
        try {
            //Open a port to send the package
            if (tcpSocket == null){
                println("tcpSocket was null.")
                println("Opening tcp connection @ = $inetAddress")
                tcpSocket = Socket(inetAddress.hostAddress, TCP_PORT)
                tcpSocket.connect(InetSocketAddress(imageClientIP, TCP_PORT))

            }
            if (tcpSocket != null) {
                if (tcpSocket.isClosed) {
                    println("tcpSocket was closed.")
                    println("Opening tcp connection @ = $inetAddress")
                    tcpSocket = Socket(inetAddress.hostAddress, TCP_PORT)
                    tcpSocket.connect(InetSocketAddress(imageClientIP, TCP_PORT))

                } else if (!tcpSocket.isConnected) {
                    println("tcpSocket was open but not connected.")
                    tcpSocket.connect(InetSocketAddress(imageClientIP, TCP_PORT))

                } else if (tcpSocket.isInputShutdown || tcpSocket.isOutputShutdown){
                    println("tcpSocket had input/output shutdown.")
                    println("Closing and opening again tcp connection @ = $inetAddress")
                    tcpSocket.close()
                    tcpSocket = Socket(inetAddress.hostAddress, TCP_PORT)
                    tcpSocket.connect(InetSocketAddress(imageClientIP, TCP_PORT))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "openTCPSocket: Exception: " + e.message)
            // try once more
            if (retries < maxRetries) {
                retries++
                if (e.message.toString().compareTo("Socket closed") == 0) {
                    println("tcpSocket was -- actually -- closed.")
                    println("Opening tcp connection @ = $inetAddress")
                    tcpSocket = Socket(inetAddress.hostAddress, TCP_PORT)
                    tcpSocket.connect(InetSocketAddress(imageClientIP, TCP_PORT))
                }
            }
        }
    }

    private fun closeTCPSocket() {
        try {
            //Close the port
            if (!tcpSocket.isClosed) {
                tcpSocket.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "closeTCPSocket: IOException: " + e.message)
        }
    }

    private fun writeIntTo4BytesToBuffer(data: Int) : ByteArray {
        var buffer = ByteArray(4)
        buffer[0] = (data shr 0).toByte()
        buffer[1] = (data shr 8).toByte()
        buffer[2] = (data shr 16).toByte()
        buffer[3] = (data shr 24).toByte()
        return buffer
    }

    private fun sendTCP(jpgbytes: ByteArray) {
        // Hack Prevent crash (sending should be done using an async task)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        try {
            val networkWriter = DataOutputStream(tcpSocket!!.getOutputStream())
            // add the size in the first 16 bytes
            val newArray = writeIntTo4BytesToBuffer(jpgbytes.size) + jpgbytes
            networkWriter.write(newArray)
            networkWriter.flush()
            println("fun sendTCP: ${jpgbytes.size}  bytes sent to: $imageClientIP:$TCP_PORT")
        } catch (e: IOException) {
            Log.e(TAG, "sendTCP: IOException: " + e.message)
            closeTCPSocket();
            isImageClientIPIdentified = false;
        /*
            // try again to open connection
            closeTCPSocket()
            // get the remote address
            openUDPSocket(broadcastIP)
            if (isImageClientIPIdentified){
                openTCPSocket(imageClientIP)
            }
             */
        }
    }

    private fun sendUDP(messageStr: String) {
        // Hack Prevent crash (sending should be done using an async task)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        try {
            val sendData = messageStr.toByteArray()
            val sendPacket = DatagramPacket(sendData, sendData.size, broadcastIP, UDP_PORT)
            udpSocket.send(sendPacket)
            println("fun sendBroadcast: \"$messageStr\" sent to: $broadcastIP:$UDP_PORT")

            if (messageStr == "tcp"){
                // wait for responces
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                udpSocket.soTimeout = 2000 // set timeout 2 secs
                try {
                    udpSocket.receive(packet)
                    val stringData = String(packet.data, packet.offset, packet.length)
                    println("UDP packet received = $stringData")
                    // check if the udp we received is about the tcp connection request
                    // first part of return string should be "tcp" (the request)
                    if (stringData.split(":")[0].compareTo("tcp") == 0) {
                        println("TCP details received")
                        // get remote tcp address (and port, but for now port is static/just for one client)
                        imageClientIP = InetAddress.getByName(stringData.split(":")[1])
                        isImageClientIPIdentified = true
                        // try to open tcp
                        openTCPSocket(imageClientIP)
                    }
                }catch (e: Exception) {
                    // timeout exception.
                    println("Timeout reached!!! " + e);

                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendUDP: Exception: " + e.message)
        }
    }

    private fun sendUDP(jpgbytes: ByteArray) {
        // Hack Prevent crash (sending should be done using an async task)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        try {
            val sendPacket = DatagramPacket(jpgbytes, jpgbytes.size, broadcastIP, UDP_PORT)
            udpSocket.send(sendPacket)
            println("fun sendBroadcast: ${jpgbytes.size}  bytes sent to: $broadcastIP:$UDP_PORT")
        } catch (e: IOException) {
            Log.e(TAG, "sendUDP: IOException: " + e.message)
        }
    }

    private fun takePhoto() {}

    private fun captureVideo() {}

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // make sure udp socket is ready
            //openUDPSocket(broadcastIP)
            //if (isImageClientIPIdentified) {
             //   openTCPSocket(imageClientIP)
            //}

            // Preview --not necessarry to be used
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }
            // Analysis - for streamming
            val analysisBuilder = ImageAnalysis.Builder()
            val ext: Camera2Interop.Extender<*> = Camera2Interop.Extender(analysisBuilder)

            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_STATE_LOCKED
            )
            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_OFF
            )

            ext.setCaptureRequestOption(
                CaptureRequest.JPEG_QUALITY, 80
            )


            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range<Int>(30, 60)
            )


            //analysisBuilder.setTargetResolution(Size(1280, 720))
            //analysisBuilder.setTargetResolution(Size(1920, 1080))


            val imageAnalyzer = analysisBuilder.build();

            imageAnalyzer.setAnalyzer(cameraExecutor, FrameStreamer { success, jpgbytes ->
                //Log.d(TAG, "Total frames analysed : $numOfFrames")
                //Log.d(TAG, "start time (nanno seconds) : $startCountFramesNanoSecs")
                //count frames for displaying fps
                if (numOfFrames == 0) {
                    startCountFrameMillSecs = System.currentTimeMillis()
                    numOfFrames += 1
                } else {
                    if (System.currentTimeMillis() - startCountFrameMillSecs >= (10 * 1000)) {
                        // every 10 seconds count fps and reset counter
                        val fps = numOfFrames / 10
                        numOfFrames = 0
                        viewBinding.fpsTextview.setText("fps: $fps")
                        Log.d(TAG, "fps: $fps")
                    } else {
                        numOfFrames += 1
                    }
                }

                // try sending
                if (success) {
                    //sendUDP("size:${jpgbytes.size}")
                    //sendUDP(jpgbytes)
                    if (tcpSocket.isConnected && (!tcpSocket.isClosed)) {
                        sendTCP(jpgbytes)
                    }else {
                        sendUDP("tcp")
                        // and wait ... for connection to be established
                        Thread.sleep(2000)  // wait for 2 second
                    }
                }

            })

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            //val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        closeUDPSocket()
        closeTCPSocket()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "StreamCam"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
        private const val UDP_PORT = 20001
        private const val TCP_PORT = 20002
    }
}

private class FrameStreamer(private val listener: StreamListener) : ImageAnalysis.Analyzer {


    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {

//        val buffer = image.planes[0].buffer
//        val data = buffer.toByteArray()
//        val pixels = data.map { it.toInt() and 0xFF }
//        val luma = pixels.average()

        // process ImageProxy to jpg
        val bitmap = BitmapUtils.getBitmap(image)
        //Convert bitmap to byte array
        val bos = ByteArrayOutputStream()
        if (bitmap != null) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
            val jpgdata = bos.toByteArray()
            //Log.d(TAG, "jpg image size : ${jpgdata.size} bytes")
            // return the data to main executor
            listener(true, jpgdata)
        } else {
            listener(false, "".toByteArray())
        }

        image.close()
    }

    companion object {
        private const val TAG = "FrameStreamer"
    }
}

