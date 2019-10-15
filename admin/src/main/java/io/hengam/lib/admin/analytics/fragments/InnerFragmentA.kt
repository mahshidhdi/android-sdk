package io.hengam.lib.admin.analytics.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.hengam.lib.admin.R

class InnerFragmentA : Fragment() {

    private lateinit var tvName: TextView

    override fun onCreateView(
            inflater: LayoutInflater,
            parent: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.inner_fragment, parent, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Set values for view here

        tvName = view.findViewById(R.id.fragmentName)
        tvName.text = "(Inner Fragment A)"
    }
}