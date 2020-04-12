package com.an9ar.omegacamera.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.Navigation
import com.an9ar.omegacamera.R
import com.an9ar.omegacamera.activities.MainActivity
import com.an9ar.omegacamera.activities.MainActivity.Companion.KEY_EVENT_ACTION
import com.an9ar.omegacamera.activities.MainActivity.Companion.KEY_EVENT_EXTRA
import com.an9ar.omegacamera.extensions.ANIMATION_FAST_MILLIS
import com.an9ar.omegacamera.extensions.ANIMATION_SLOW_MILLIS
import com.an9ar.omegacamera.extensions.log
import com.an9ar.omegacamera.extensions.simulateClick
import com.an9ar.omegacamera.utils.LuminosityAnalyzer
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import kotlinx.android.synthetic.main.camera_ui.*
import kotlinx.android.synthetic.main.camera_ui.view.*
import kotlinx.android.synthetic.main.fragment_camera.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraFragment : Fragment() {

    private lateinit var outputDirectory: File
    private lateinit var broadcastManager: LocalBroadcastManager

    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    private lateinit var cameraExecutor: ExecutorService

    private val volumeDownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN)) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    cameraButtonPhoto.simulateClick()
                }
            }
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                log("Rotation changed: ${view.display.rotation}")
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        broadcastManager = LocalBroadcastManager.getInstance(view.context)
        val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
        broadcastManager.registerReceiver(volumeDownReceiver, filter)

        displayManager.registerDisplayListener(displayListener, null)

        outputDirectory = MainActivity.getOutputDirectory(requireContext())

        previewView.post {
            displayId = previewView.display.displayId
            updateCameraUi()
            bindCameraUseCases()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragmentContainer).navigate(
                CameraFragmentDirections.actionCameraFragmentToPermissionsFragment()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        broadcastManager.unregisterReceiver(volumeDownReceiver)
        displayManager.unregisterDisplayListener(displayListener)
    }

    private fun bindCameraUseCases() {
        val metrics = DisplayMetrics().also { previewView.display.getRealMetrics(it) }
        log("Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")
        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        log("Preview aspect ratio: $screenAspectRatio")
        val rotation = previewView.display.rotation
        log("Rotation: $rotation")
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            preview = Preview.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build()

            preview?.setSurfaceProvider(previewView.previewSurfaceProvider)

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build()

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luminosity ->
                        log("Average luminosity: $luminosity")
                    })
                }

            cameraProvider.unbindAll()

            try {
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)
            } catch(exc: Exception) {
                log("Use case binding failed - $exc")
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun updateCameraUi() {
        cameraContainer.removeView(cameraUiContainer)
        val controls = View.inflate(requireContext(), R.layout.camera_ui, cameraContainer)

        lifecycleScope.launch(Dispatchers.IO) {
            outputDirectory.listFiles { file ->
                GalleryFragment.EXTENSION_WHITELIST.contains(file.extension.toUpperCase(Locale.ROOT))
            }?.max()?.let {
                setGalleryThumbnail(Uri.fromFile(it))
            }
        }

        controls.cameraButtonSwitch.setOnClickListener {
            lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            bindCameraUseCases()
        }

        controls.cameraButtonGallery.setOnClickListener {
            if (true == outputDirectory.listFiles()?.isNotEmpty()) {
                Navigation.findNavController(
                    requireActivity(), R.id.fragmentContainer
                ).navigate(CameraFragmentDirections.actionCameraFragmentToGalleryFragment(outputDirectory.absolutePath))
            }
        }

        controls.cameraButtonPhoto.setOnClickListener {
            imageCapture?.let { imageCapture ->

                val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)
                val metadata = ImageCapture.Metadata().apply {
                    isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
                }

                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                    .setMetadata(metadata)
                    .build()

                imageCapture.takePicture(
                    outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exc: ImageCaptureException) {
                            log( "Photo capture failed: ${exc.message}")
                        }

                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                            log( "Photo capture succeeded: $savedUri")

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                setGalleryThumbnail(savedUri)
                            }

                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                                requireActivity().sendBroadcast(
                                    Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri)
                                )
                            }

                            val mimeType = MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(savedUri.toFile().extension)
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(savedUri.toString()),
                                arrayOf(mimeType)
                            ) { _, uri ->
                                log("Image capture scanned into media store: $uri")
                            }
                        }
                    })

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    cameraContainer.postDelayed({
                        cameraContainer.foreground = ColorDrawable(Color.BLACK)
                        cameraContainer.postDelayed(
                            { cameraContainer.foreground = null }, ANIMATION_FAST_MILLIS)
                    }, ANIMATION_SLOW_MILLIS)
                }
            }
        }
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun setGalleryThumbnail(uri: Uri) {
        cameraButtonGallery.post {
            cameraButtonGallery.setPadding(resources.getDimension(R.dimen.stroke_small).toInt())
            Glide.with(cameraButtonGallery)
                .load(uri)
                .apply(RequestOptions.circleCropTransform())
                .into(cameraButtonGallery)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateCameraUi()
    }

    companion object {
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(baseFolder, SimpleDateFormat(format, Locale.US)
                .format(System.currentTimeMillis()) + extension)
    }
}