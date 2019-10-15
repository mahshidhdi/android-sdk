package io.hengam.lib.admin.analytics

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.hengam.lib.admin.R


class RecyclerViewAdapter// Provide a suitable constructor (depends on the kind of dataSet)
(private val values: MutableList<String>) : RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    inner class ViewHolder(var layout: View) : RecyclerView.ViewHolder(layout) {
        // each data item is just a string in this case
        var txtHeader: TextView = layout.findViewById(R.id.firstLine) as TextView
        var txtFooter: TextView = layout.findViewById(R.id.secondLine) as TextView
    }

    fun add(position: Int, item: String) {
        values.add(position, item)
        notifyItemInserted(position)
    }

    fun remove(position: Int) {
        values.removeAt(position)
        notifyItemRemoved(position)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerViewAdapter.ViewHolder {
        // create a new view
        val inflater = LayoutInflater.from(parent.context)
        val v = if (viewType == 1) inflater.inflate(R.layout.list_odd_item_layout, parent, false)
        else inflater.inflate(R.layout.list_even_item_layout, parent, false)

        // set the view's size, margins, padding and layout parameters
        return ViewHolder(v)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // - get element from your dataSet at this position
        // - replace the contents of the view with that element
        val name = values[position]
        holder.txtHeader.text = name
        holder.txtHeader.setOnClickListener {
            remove(position)
        }

        holder.txtFooter.text = "Footer: $name"
    }

    // Return the size of your dataSet (invoked by the layout manager)
    override fun getItemCount(): Int {
        return values.size
    }

    override fun getItemViewType(position: Int): Int {
        return position % 2
    }
}