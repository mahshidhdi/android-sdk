package co.pushe.plus.admin.analytics.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import co.pushe.plus.admin.R
import co.pushe.plus.admin.analytics.activities.FragmentIndex

class FragmentWithLayouts2 : Fragment() {

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

        return inflater.inflate(R.layout.fragment_with_layout2, parent, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Set values for view here

        changeFirstButton = view.findViewById(R.id.buttonInnerFragment)
        changeSecondButton = view.findViewById(R.id.buttonInnerFragment2)
        tvName = view.findViewById(R.id.tvName)

        tvName.text = "(Fragment with frameLayouts2)"

        childFragmentManager
                .beginTransaction()
                .replace(R.id.fragmentFLContainer21, getFirstFragment())
                .addToBackStack(null)
                .commit()

        childFragmentManager
                .beginTransaction()
                .replace(R.id.fragmentFLContainer22, getSecondFragment())
                .addToBackStack(null)
                .commit()

        changeFirstButton.setOnClickListener {
            childFragmentManager
                    .beginTransaction()
                    .replace(R.id.fragmentFLContainer21, getFirstFragment())
                    .addToBackStack(null)
                    .commit()
        }

        changeSecondButton.setOnClickListener {
            childFragmentManager
                    .beginTransaction()
                    .replace(R.id.fragmentFLContainer22, getSecondFragment())
                    .addToBackStack(null)
                    .commit()

        }
    }

    private fun getFirstFragment(): Fragment {
        val fragment: Fragment
        when (firstFragmentIndex) {
            FragmentIndex.FIRST -> {
                fragment = InnerFragmentA()
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
                fragment = InnerFragmentA()
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