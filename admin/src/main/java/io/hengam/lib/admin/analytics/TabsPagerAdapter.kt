package io.hengam.lib.admin.analytics

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import io.hengam.lib.admin.analytics.fragments.FirstPagerFragment
import io.hengam.lib.admin.analytics.fragments.SecondPagerFragment
import io.hengam.lib.admin.analytics.fragments.ThirdPagerFragment

class TabsPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

    private val titles = arrayOf("First Fragment", "Second Fragment", "Third Fragment")

    // Returns total number of pages
    override fun getCount(): Int {
        return ITEMS_COUNT
    }

    // Returns the fragment to display for that page
    override fun getItem(position: Int): Fragment? {
        return when (position) {
            0 -> FirstPagerFragment()
            1 -> SecondPagerFragment()
            2 -> ThirdPagerFragment()
            else -> null
        }
    }

    // Returns the page title for the top indicator
    override fun getPageTitle(position: Int): CharSequence? {
        return titles[position]
    }

    companion object {
        private const val ITEMS_COUNT = 3
    }

}
