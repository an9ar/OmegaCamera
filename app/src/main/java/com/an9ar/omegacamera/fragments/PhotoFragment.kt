package com.an9ar.omegacamera.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.an9ar.omegacamera.R
import com.an9ar.omegacamera.utils.ViewPagerItemClickListener
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import java.io.File

class PhotoFragment internal constructor() : Fragment()  {

    private var listener: ViewPagerItemClickListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?) = PhotoView(context)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return
        val resource = args.getString(FILE_NAME_KEY)?.let { File(it) } ?: R.drawable.ic_photo
        Glide.with(view).load(resource).into(view as PhotoView)
        view.setOnClickListener {
            listener?.onClick()
        }
    }

    companion object {
        private const val FILE_NAME_KEY = "file_name"

        fun create(image: File, listener: ViewPagerItemClickListener) = PhotoFragment().apply {
            this.listener = listener
            arguments = Bundle().apply {
                putString(FILE_NAME_KEY, image.absolutePath)
            }
        }
    }

}