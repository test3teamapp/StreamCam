package com.cang.streamcam.Utils

import android.annotation.SuppressLint
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.camera.camera2.interop.Camera2Interop
import com.cang.streamcam.MainActivity

public class CameraUtils {

    @SuppressLint("UnsafeOptInUsageError")
    public fun setExtendedCameraSettings(ext: Camera2Interop.Extender<*>, highQuality: Boolean){
        if (highQuality) {
            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_STATE_CONVERGED
            )
            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_EDOF
            )
            ext.setCaptureRequestOption(CaptureRequest.JPEG_QUALITY, 80)
            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range<Int>(30, 30)
            )
            ext.setCaptureRequestOption(
                CaptureRequest.EDGE_MODE,
                CameraMetadata.EDGE_MODE_HIGH_QUALITY
            );
            ext.setCaptureRequestOption(
                CaptureRequest.SHADING_MODE,
                CameraMetadata.SHADING_MODE_HIGH_QUALITY
            );
            ext.setCaptureRequestOption(
                CaptureRequest.TONEMAP_MODE,
                CameraMetadata.TONEMAP_MODE_HIGH_QUALITY
            );
            ext.setCaptureRequestOption(
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
            );
            ext.setCaptureRequestOption(
                CaptureRequest.COLOR_CORRECTION_MODE,
                CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
            );
            ext.setCaptureRequestOption(
                CaptureRequest.HOT_PIXEL_MODE,
                CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY
            );
            ext.setCaptureRequestOption(
                CaptureRequest.NOISE_REDUCTION_MODE,
                CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
            );
            ext.setCaptureRequestOption(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
            );
        } else {
            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_STATE_LOCKED
            )
            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_OFF
            )
            ext.setCaptureRequestOption(CaptureRequest.JPEG_QUALITY, 80)
            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range<Int>(30, 30)
            )
            ext.setCaptureRequestOption(
                CaptureRequest.EDGE_MODE,
                CameraMetadata.EDGE_MODE_FAST
            );
            ext.setCaptureRequestOption(
                CaptureRequest.SHADING_MODE,
                CameraMetadata.SHADING_MODE_FAST
            );
            ext.setCaptureRequestOption(
                CaptureRequest.TONEMAP_MODE,
                CameraMetadata.TONEMAP_MODE_FAST
            );
            ext.setCaptureRequestOption(
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_FAST
            );
            ext.setCaptureRequestOption(
                CaptureRequest.COLOR_CORRECTION_MODE,
                CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_FAST
            );
            ext.setCaptureRequestOption(
                CaptureRequest.HOT_PIXEL_MODE,
                CameraMetadata.HOT_PIXEL_MODE_FAST
            );
            ext.setCaptureRequestOption(
                CaptureRequest.NOISE_REDUCTION_MODE,
                CameraMetadata.NOISE_REDUCTION_MODE_FAST
            );
            ext.setCaptureRequestOption(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
            );

        }
    }
}