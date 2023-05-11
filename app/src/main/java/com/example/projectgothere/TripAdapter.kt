package com.example.projectgothere

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class TripAdapter(var context: Context, var trips:Array<File>) :RecyclerView.Adapter<TripAdapter.TripViewHolder>(){

    private lateinit var listener : onItemClickListener

    interface onItemClickListener{
        fun onItemClick(position: Int)
    }

    fun setOnItemClickListener(listener: onItemClickListener){
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.trip_item,parent,false)
        return TripViewHolder(itemView,listener)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        val currentItem = trips[position]
        holder.tripTitle.text = currentItem.name
    }

    override fun getItemCount(): Int {
        return trips.size
    }

    class TripViewHolder(itemView: View, listener:onItemClickListener):RecyclerView.ViewHolder(itemView){
        var tripTitle = itemView.findViewById(R.id.tripTitle) as TextView

        init{
            itemView.setOnClickListener{
                listener.onItemClick(absoluteAdapterPosition)
            }
        }

    }
}