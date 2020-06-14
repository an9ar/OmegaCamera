package com.an9ar.omegacamera.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.an9ar.omegacamera.R
import com.an9ar.omegacamera.extensions.gone
import com.an9ar.omegacamera.extensions.visible
import com.tapadoo.alerter.Alerter
import kotlinx.android.synthetic.main.fragmet_permissions.*

class PermissionsFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasPermissions(requireContext())) {
            requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
        } else {
            Navigation.findNavController(requireActivity(), R.id.fragmentContainer).navigate(PermissionsFragmentDirections.actionPermissionsFragmentToCameraFragment())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragmet_permissions, container, false)

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (PackageManager.PERMISSION_GRANTED == grantResults.firstOrNull()) {
                Navigation.findNavController(requireActivity(), R.id.fragmentContainer).navigate(PermissionsFragmentDirections.actionPermissionsFragmentToCameraFragment())
                noPhotoView.gone()
            } else {
                Alerter.create(activity)
                    .setTitle(getString(R.string.permissions_error_title))
                    .setText(getString(R.string.permissions_error_value))
                    .setBackgroundColorRes(R.color.colorAccent)
                    .setIcon(R.drawable.ic_error)
                    .setDuration(2500)
                    .setOnClickListener(View.OnClickListener {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.fromParts("package", activity?.packageName, null)
                        activity?.startActivity(intent)
                    })
                    .show()
                noPhotoView.visible()
            }
        }
    }

    companion object {
        const val PERMISSIONS_REQUEST_CODE = 10
        private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)

        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}