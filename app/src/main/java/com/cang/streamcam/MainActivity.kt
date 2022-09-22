package com.cang.streamcam

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.display.DisplayManager
import android.os.*
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cang.streamcam.databinding.ActivityMainBinding
import com.cang.streamcam.gps.LocationProvider
import com.cang.streamcam.sensors.SenseDataProvider
import java.io.ByteArrayOutputStream
import java.net.*
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() , ActivityCompat.OnRequestPermissionsResultCallback {
    private lateinit var viewBinding: ActivityMainBinding

    private var currentLinearZoom: Float = 0f
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var isShowingPreview = false
    private var mIsSendingImages = true

    private lateinit var cameraExecutor: ExecutorService

    private var startCountFrameMillSecs = System.currentTimeMillis()
    private var numOfFrames = 0

    /**
     * ID of the current [CameraDevice].
     */
    private var mCameraId: String? = null

    /**
     * An [AutoFitTextureView] for camera preview.
     */
    private var mTextureView: AutoFitTextureView? = null

    /**
     * A [CameraCaptureSession] for camera preview.
     */
    private var mCaptureSession: CameraCaptureSession? = null

    /**
     * A reference to the opened [CameraDevice].
     */
    private var mCameraDevice: CameraDevice? = null

    /**
     * The [android.util.Size] of camera preview.
     */
    private var mPreviewSize: Size? = null

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var mBackgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var mBackgroundHandler: Handler? = null

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null

    /**
     * [CaptureRequest] generated by [.mPreviewRequestBuilder]
     */
    private var mPreviewRequest: CaptureRequest? = null

    /**
     * The current state of camera state for taking pictures.
     *
     * @see .mCaptureCallback
     */
    private var mState = CameraState.CLOSED

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val mCameraOpenCloseLock = Semaphore(1)

    /**
     * Whether the current camera device supports Flash or not.
     */
    private var mFlashSupported = false

    /**
     * Orientation of the camera sensor
     */
    private var mSensorOrientation = 0



    private val displayManager by lazy {
        this.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    private lateinit var connectionHandler: ConnectionHandler


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                if (mTextureView != null) {
                    if (mTextureView!!.isAvailable()) {
                        openCamera(mTextureView!!.getWidth(), mTextureView!!.getHeight())
                    }
                }
            } else {
                showToast("Permissions not granted by the user.", true)
                finish()
            }
        }
    }

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayid: Int) = Unit
        override fun onDisplayRemoved(displayid: Int) = Unit
        override fun onDisplayChanged(displayid: Int) = Unit
    }

    /**
     * Shows a [Toast] on the UI thread.
     *
     * @param text The message to show
     */
    private fun showToast(text: String, showLong:Boolean) {
        if (showLong) {
            this.runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
        }else {
            this.runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        // keep screen always on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Every time the orientation of device changes, update rotation for use cases
        displayManager.registerDisplayListener(displayListener, null)
        connectionHandler = ConnectionHandler.getInstance()
        connectionHandler.createSockets()

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }
        viewBinding.showPreviewButton.setOnClickListener { showPreview() }
        viewBinding.fpsTextview.setText("fps")
        viewBinding.switchCameraButton.setOnClickListener { switchCamera() }
        viewBinding.speedTextview.setText("-1")
        viewBinding.stopTcpButton.setOnClickListener{ startStopImageSent()}
        mTextureView = viewBinding.texture

        //// --- PERFORMANCE WHEN CHANGING THIS ?????? ----- /////
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startStopImageSent() {
        mIsSendingImages = !mIsSendingImages
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        LocationProvider.getInstance(this).startLocationUpdates()
        val hasGyro = SenseDataProvider.getInstance(this).findSensors()
        if (hasGyro){
            showToast("Has Gyro sensor !", true)
            SenseDataProvider.getInstance(this).enableSensors()
        }else {
            showToast("NO Gyro sensor !", true)
        }
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView != null) {
            if (mTextureView!!.isAvailable()) {
                openCamera(mTextureView!!.getWidth(), mTextureView!!.getHeight())
            } else {
                mTextureView!!.setSurfaceTextureListener(mSurfaceTextureListener)
            }
        }
    }

    override fun onPause() {
        //closeCamera()
        //stopBackgroundThread()
        super.onPause()
    }


    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int) {
        val manager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }
                val map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                ) ?: continue


                // For still image captures, we use the largest available size.
                val largest = Collections.max(
                    Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
                    CompareSizesByArea()
                )
                /*
                mImageReader = ImageReader.newInstance(
                    largest.width, largest.height,
                    ImageFormat.JPEG,  /*maxImages*/2
                )
                mImageReader!!.setOnImageAvailableListener(
                    mOnImageAvailableListener, mBackgroundHandler
                )
                   */
                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.

                val displayRotation = this.windowManager.defaultDisplay.rotation
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                var swappedDimensions = false
                when (displayRotation) {
                    Surface.ROTATION_0, Surface.ROTATION_180 -> if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                        swappedDimensions = true
                    }
                    Surface.ROTATION_90, Surface.ROTATION_270 -> if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                        swappedDimensions = true
                    }
                    else -> Log.e(
                        TAG,
                        "Display rotation is invalid: $displayRotation"
                    )
                }


                val displaySize = Point()
                this.windowManager.defaultDisplay.getSize(displaySize)
                var rotatedPreviewWidth = width
                var rotatedPreviewHeight = height
                var maxPreviewWidth = displaySize.x
                var maxPreviewHeight = displaySize.y
                if (swappedDimensions) {
                    rotatedPreviewWidth = height
                    rotatedPreviewHeight = width
                    maxPreviewWidth = displaySize.y
                    maxPreviewHeight = displaySize.x
                }
                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH
                }
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(
                    map.getOutputSizes(
                        SurfaceTexture::class.java
                    ),
                    rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                    maxPreviewHeight, largest
                )

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                val orientation: Int = getResources().getConfiguration().orientation
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView?.setAspectRatio(
                        mPreviewSize!!.width, mPreviewSize!!.height
                    )
                } else {
                    mTextureView?.setAspectRatio(
                        mPreviewSize!!.height, mPreviewSize!!.width
                    )
                }

                // Check if the flash is supported.
                val available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                mFlashSupported = available ?: false
                mCameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(
                TAG,
                "setUpCameraOutputs : Camera2API is used but not supported on the device:"
            )
        }
    }

    /**
     * Opens the camera specified by [Camera2BasicFragment.mCameraId].
     */
    @SuppressLint("MissingPermission")
    private fun openCamera(width: Int, height: Int) {
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        setUpCameraOutputs(width, height)
        configureTransform(width, height)
        val manager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            with(manager) { openCamera(mCameraId!!, mStateCallback, mBackgroundHandler) }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeCamera() {
        try {
            mCameraOpenCloseLock.acquire()
            if (null != mCaptureSession) {
                mCaptureSession!!.close()
                mCaptureSession = null
            }
            if (null != mCameraDevice) {
                mCameraDevice!!.close()
                mCameraDevice = null
            }
            /*
            if (null != mImageReader) {
                mImageReader!!.close()
                mImageReader = null
            }
             */
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    /**
     * A [CameraCaptureSession.CaptureCallback] that handles events related to JPEG capture.
     */
    private val mCaptureCallback: CameraCaptureSession.CaptureCallback =
        object : CameraCaptureSession.CaptureCallback() {
            private fun process(result: CaptureResult) {
                when (mState) {
                    CameraState.OPEN_PREVIEW -> {}
                }
            }

            override fun onCaptureProgressed(
                @NonNull session: CameraCaptureSession,
                @NonNull request: CaptureRequest,
                @NonNull partialResult: CaptureResult
            ) {
                process(partialResult)
            }

            override fun onCaptureCompleted(
                @NonNull session: CameraCaptureSession,
                @NonNull request: CaptureRequest,
                @NonNull result: TotalCaptureResult
            ) {
                process(result)
            }
        }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession() {
        try {
            val texture: SurfaceTexture = mTextureView?.getSurfaceTexture()!!

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder!!.addTarget(surface)

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice!!.createCaptureSession(
                //Arrays.asList(surface, mImageReader!!.surface),
                Arrays.asList(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(@NonNull cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (null == mCameraDevice) {
                            return
                        }

                        // When the session is ready, we start displaying the preview.
                        mCaptureSession = cameraCaptureSession
                        try {

                            /*
                             Auto focus should be continuous for camera preview.
                            mPreviewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                            )
                            */
                            mPreviewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_OFF,
                            )
                            // focus to infinity
                            mPreviewRequestBuilder!!.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)

                            // Flash is automatically enabled when necessary.
                            //setAutoFlash(mPreviewRequestBuilder)

                            // Finally, we start displaying the camera preview.
                            mPreviewRequest = mPreviewRequestBuilder!!.build()
                            mCaptureSession!!.setRepeatingRequest(
                                mPreviewRequest!!,mCaptureCallback, mBackgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(
                        @NonNull cameraCaptureSession: CameraCaptureSession
                    ) {
                        showToast("Camera Preview Configuration Failed", true)
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        if (null == mTextureView || null == mPreviewSize || null == this) {
            return
        }
        val rotation = this.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0F, 0F, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(
            0F, 0F, mPreviewSize!!.height.toFloat(),
            mPreviewSize!!.width.toFloat()
        )
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / mPreviewSize!!.height,
                viewWidth.toFloat() / mPreviewSize!!.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        mTextureView!!.setTransform(matrix)
    }

    /**
    * Retrieves the JPEG orientation from the specified screen rotation.
    *
    * @param rotation The screen rotation.
    * @return The JPEG orientation (one of 0, 90, 270, and 360)
    */
    private fun getOrientation(rotation: Int): Int {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS[rotation] + mSensorOrientation + 270) % 360
    }

    /**
     * Compares two `Size`s based on their areas.
     */
    internal class CompareSizesByArea : Comparator<Size?> {
        override fun compare(lhs: Size?, rhs: Size?): Int {
            // We cast here to ensure the multiplications won't overflow
            if (lhs != null) {
                if (rhs != null) {
                    return java.lang.Long.signum(
                        lhs.width.toLong() * lhs.height -
                                rhs.width.toLong() * rhs.height
                    )
                }
            }
            // ??? need to default-return something.
            return 0
        }
    }

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val mSurfaceTextureListener: TextureView.SurfaceTextureListener = object :
        TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
            if (mIsSendingImages) {
                if (mTextureView != null) {
                    val thread = Thread {
                        val image: Bitmap? = mTextureView?.bitmap
                        /*
                        val resizedBitmap =
                            image?.let {
                                Bitmap.createScaledBitmap(
                                    it,
                                    MAX_PREVIEW_WIDTH,
                                    MAX_PREVIEW_HEIGHT,
                                    false)
                            }

                         */
                        val matrix = Matrix()

                        //matrix.postRotate(getOrientation(this@MainActivity.windowManager.defaultDisplay.rotation).toFloat())
                        matrix.postRotate(270.0f)

                        val rotatedBitmap =
                            image?.let {
                                Bitmap.createBitmap(
                                    it,
                                    0,
                                    0,
                                    it.width,
                                    it.height,
                                    matrix,
                                    false)
                            }

                        //Convert bitmap to byte array
                        val bos = ByteArrayOutputStream()
                        if (rotatedBitmap != null) {
                            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                            val jpgdata = bos.toByteArray()
                            //Log.d(TAG, "jpg image size : ${jpgdata.size} bytes")
                            // return the data to main executor
                            //Log.d(TAG,"image captured . size : " + image.width + "x" + image.height)
                            // try sending
                            connectionHandler.sendTCP(jpgdata)
                        }
                    }

                    thread.start()
                }
            }
            // update speed
            if (LocationProvider.getInstance(this@MainActivity).lastLocation != null) {
                viewBinding.speedTextview.setText(
                    LocationProvider.getInstance(this@MainActivity).lastLocation!!.speed.toString()
                )
            }
            if (numOfFrames == 0) {
                startCountFrameMillSecs = System.currentTimeMillis()
                numOfFrames += 1
            } else {
                if (System.currentTimeMillis() - startCountFrameMillSecs >= 10 * 1000) {
                    // every 10 seconds count fps and reset counter
                    val fps = numOfFrames / 10
                    numOfFrames = 0
                    Log.d(TAG, "fps: $fps")
                    // try to touch View of UI thread
                    this@MainActivity.runOnUiThread(Runnable {
                        viewBinding.fpsTextview.setText("$fps")
                    })

                } else {
                    numOfFrames += 1
                }
            }
        }
    }

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.
     */
    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(@NonNull cameraDevice: CameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release()
            mCameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(@NonNull cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(@NonNull cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            finish()
        }
    }

    private fun switchZoom() {
        if(currentLinearZoom == 0.0f){
            currentLinearZoom = 0.5f
        }else if (currentLinearZoom == 0.5f){
            currentLinearZoom = 1.0f
        }else currentLinearZoom= 0.0f

        //rebindCameraUseCases()
    }

    private fun takePhoto() {}

    private fun captureVideo() {}

    @SuppressLint("UnsafeOptInUsageError")
    private fun switchCamera() {
    }

    private fun showPreview() {
        isShowingPreview = !isShowingPreview
        rebindCameraUseCases()
    }

    private fun rebindCameraUseCases() {
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        closeCamera()
        stopBackgroundThread()
        LocationProvider.getInstance(this).stopLocationUpdates()
        if (SenseDataProvider.getInstance(this).findSensors()) {
            SenseDataProvider.getInstance(this).disableSensors()
        }
        displayManager.unregisterDisplayListener(displayListener)
        connectionHandler.destroySockets()
    }

    companion object {
        private const val TAG = "StreamCam"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()

        enum class CameraState {
            CLOSED, OPEN_PREVIEW
        }

        /**
         * Max preview width that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_WIDTH = 1280

        /**
         * Max preview height that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_HEIGHT = 720
        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        private val ORIENTATIONS = SparseIntArray()

        /**
         * Given `choices` of `Size`s supported by a camera, choose the smallest one that
         * is at least as large as the respective texture view size, and that is at most as large as the
         * respective max size, and whose aspect ratio matches with the specified value. If such size
         * doesn't exist, choose the largest one that is at most as large as the respective max size,
         * and whose aspect ratio matches with the specified value.
         *
         * @param choices           The list of sizes that the camera supports for the intended output
         * class
         * @param textureViewWidth  The width of the texture view relative to sensor coordinate
         * @param textureViewHeight The height of the texture view relative to sensor coordinate
         * @param maxWidth          The maximum width that can be chosen
         * @param maxHeight         The maximum height that can be chosen
         * @param aspectRatio       The aspect ratio
         * @return The optimal `Size`, or an arbitrary one if none were big enough
         */
        private fun chooseOptimalSize(
            choices: Array<Size>, textureViewWidth: Int,
            textureViewHeight: Int, maxWidth: Int, maxHeight: Int, aspectRatio: Size
        ): Size {

            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough: MutableList<Size> = ArrayList()
            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough: MutableList<Size> = ArrayList()
            val w = aspectRatio.width
            val h = aspectRatio.height
            for (option in choices) {
                if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * h / w) {
                    if (option.width >= textureViewWidth &&
                        option.height >= textureViewHeight
                    ) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            return if (bigEnough.size > 0) {
                Collections.min(bigEnough, CompareSizesByArea())
            } else if (notBigEnough.size > 0) {
                Collections.max(notBigEnough, CompareSizesByArea())
            } else {
                Log.e(TAG, "Couldn't find any suitable preview size")
                choices[0]
            }
        }

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }
}

