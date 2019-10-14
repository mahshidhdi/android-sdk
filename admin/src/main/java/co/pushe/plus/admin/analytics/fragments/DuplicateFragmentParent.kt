package co.pushe.plus.admin.analytics.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import co.pushe.plus.admin.R

class DuplicateFragmentParent : Fragment() {

    private lateinit var nextButton: Button
    private var currentFragmentIndex = 0

    override fun onCreateView(
            inflater: LayoutInflater,
            parent: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_duplicate_parent, parent, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        nextButton = view.findViewById(R.id.buttonFragmentInnerNext)

        nextButton.setOnClickListener {
            if (currentFragmentIndex == 0) {
                childFragmentManager
                        .beginTransaction()
                        .replace(R.id.activityFragmentContainer, DuplicateFragment())
                        .addToBackStack(null)
                        .commit()
                currentFragmentIndex++
            } else {
                childFragmentManager
                        .beginTransaction()
                        .replace(R.id.activityFragmentContainer, FragmentB())
                        .addToBackStack(null)
                        .commit()
                currentFragmentIndex--
            }

        }

        childFragmentManager
                .beginTransaction()
                .replace(R.id.activityFragmentContainer, FragmentB())
                .addToBackStack(null)
                .commit()
    }
}