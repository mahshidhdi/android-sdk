package io.hengam.lib.admin.analytics.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.hengam.lib.admin.R

class DuplicateFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_duplicate, parent, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    }
}