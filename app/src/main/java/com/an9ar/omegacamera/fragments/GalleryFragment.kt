package com.an9ar.omegacamera.fragments

import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.an9ar.omegacamera.BuildConfig
import com.an9ar.omegacamera.R
import com.an9ar.omegacamera.extensions.*
import com.an9ar.omegacamera.utils.*
import kotlinx.android.synthetic.main.fragment_gallery.*
import java.io.File
import java.util.*

class GalleryFragment : Fragment() {

    private val args: GalleryFragmentArgs by navArgs()
    private var isControlsPanelShown: Boolean = true

    private lateinit var mediaList: MutableList<File>

    inner class MediaPagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getCount(): Int = mediaList.size
        override fun getItem(position: Int): Fragment = PhotoFragment.create(mediaList[position], object : ViewPagerItemClickListener{
            override fun onClick() {
                if (isControlsPanelShown){
                    isControlsPanelShown = false
                    cutoutSafeArea.gone()
                }
                else{
                    isControlsPanelShown = true
                    cutoutSafeArea.visible()
                }
            }
        })
        override fun getItemPosition(obj: Any): Int = POSITION_NONE
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gallery, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true

        val rootDirectory = File(args.rootDirectory)

        mediaList = rootDirectory.listFiles {
                file -> EXTENSION_WHITELIST.contains(file.extension.toUpperCase(Locale.ROOT))
        }?.sortedDescending()?.toMutableList() ?: mutableListOf()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (mediaList.isEmpty()) {
            deleteButton.isEnabled = false
            shareButton.isEnabled = false
        }
        photoViewPager.offscreenPageLimit = 2
        photoViewPager.adapter = MediaPagerAdapter(childFragmentManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cutoutSafeArea.padWithDisplayCutout()
        }

        backButton.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.fragmentContainer).navigateUp()
        }

        shareButton.setOnClickListener {
            mediaList.getOrNull(photoViewPager.currentItem)?.let { mediaFile ->
                val intent = Intent().apply {
                    log(
                        "mediaType - ${MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(mediaFile.extension)}"
                    )
                    val mediaType = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(mediaFile.extension)
                    val uri = FileProvider.getUriForFile(
                        view.context, BuildConfig.APPLICATION_ID + ".provider", mediaFile)
                    log("URI - $uri")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = mediaType
                    action = Intent.ACTION_SEND
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share_hint)))
            }
        }

        deleteButton.setOnClickListener {
            mediaList.getOrNull(photoViewPager.currentItem)?.let { mediaFile ->
                AlertDialog.Builder(view.context, android.R.style.Theme_Material_Dialog)
                    .setTitle(getString(R.string.delete_dialog_title))
                    .setMessage(getString(R.string.delete_dialog_question))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes) { _, _ ->
                        mediaFile.delete()
                        MediaScannerConnection.scanFile(
                            view.context, arrayOf(mediaFile.absolutePath), null, null)
                        mediaList.removeAt(photoViewPager.currentItem)
                        photoViewPager.adapter?.notifyDataSetChanged()
                        if (mediaList.isEmpty()) {
                            Navigation.findNavController(requireActivity(), R.id.fragmentContainer).navigateUp()
                        }
                    }
                    .setNegativeButton(android.R.string.no, null)
                    .create().showImmersive()
            }
        }

    }

    companion object{
        val EXTENSION_WHITELIST = arrayOf("JPG")
    }
}