package io.hengam.lib.admin.analytics.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import io.hengam.lib.admin.R
import io.hengam.lib.admin.analytics.activities.FragmentIndex

class FragmentWithLayouts : Fragment() {

    var firstFragmentIndex = FragmentIndex.FIRST
    var secondFragmentIndex = FragmentIndex.SECOND

    private lateinit var changeFirstButton: Button
    private lateinit var changeSecondButton: Button

    private lateinit var tvName: TextView

    override fun onCreateView(
            inflater: LayoutInflater,
            parent: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.fragment_with_layout, parent, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Set values for view here

        changeFirstButton = view.findViewById(R.id.buttonInnerFragment)
        changeSecondButton = view.findViewById(R.id.buttonInnerFragment2)
        tvName = view.findViewById(R.id.tvName)

        tvName.text = "(Fragment with frameLayouts)"

        childFragmentManager
                .beginTransaction()
                .replace(R.id.fragmentFLContainer11, getFirstFragment())
                .addToBackStack(null)
                .commit()

        childFragmentManager
                .beginTransaction()
                .replace(R.id.fragmentFLContainer12, getSecondFragment())
                .addToBackStack(null)
                .commit()

        changeFirstButton.setOnClickListener {
            childFragmentManager
                    .beginTransaction()
                    .replace(R.id.fragmentFLContainer11, getFirstFragment())
                    .addToBackStack(null)
                    .commit()
        }

        changeSecondButton.setOnClickListener {
            childFragmentManager
                    .beginTransaction()
                    .replace(R.id.fragmentFLContainer12, getSecondFragment())
                    .addToBackStack(null)
                    .commit()

        }
    }

    private fun getFirstFragment(): Fragment {
        val fragment: Fragment
        when (firstFragmentIndex) {
            FragmentIndex.FIRST -> {
                fragment = FragmentWithLayouts2()
                firstFragmentIndex = FragmentIndex.SECOND
            }
            FragmentIndex.SECOND -> {
                fragment = InnerFragmentB()
                firstFragmentIndex = FragmentIndex.FIRST
            }
        }
        return fragment
    }

    private fun getSecondFragment(): Fragment {
        val fragment: Fragment
        when (secondFragmentIndex) {
            FragmentIndex.FIRST -> {
                fragment = FragmentWithLayouts2()
                secondFragmentIndex = FragmentIndex.SECOND
            }
            FragmentIndex.SECOND -> {
                fragment = InnerFragmentB()
                secondFragmentIndex = FragmentIndex.FIRST
            }
        }
        return fragment
    }
}