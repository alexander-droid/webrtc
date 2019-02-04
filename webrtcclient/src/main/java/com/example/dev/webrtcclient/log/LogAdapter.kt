package com.example.dev.webrtcclient.log

import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.dev.webrtcclient.R

sealed class SimpleEvent {
    data class InMessage(val message: String?): SimpleEvent()
    data class OutMessage(val message: String?): SimpleEvent()
    data class InternalMessage(val message: String?): SimpleEvent()
    data class ErrorMessage(val message: String?): SimpleEvent()
}

class LogAdapter: RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val list = mutableListOf<SimpleEvent>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.log_item, parent, false)
        return LogViewHolder(itemView)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(viewHolder: LogViewHolder, position: Int) {
        viewHolder.bind(list[position])
    }

    fun add(event: SimpleEvent) {
        list.add(event)
        notifyItemInserted(list.size)
    }

    fun setAll(list: List<SimpleEvent>) {
        this.list.clear()
        this.list.addAll(list)
        notifyDataSetChanged()
    }

    fun clear() {
        list.clear()
        notifyDataSetChanged()
    }

    class LogViewHolder(view: View): RecyclerView.ViewHolder(view) {

        private val textView = itemView.findViewById<TextView>(R.id.textView)

        fun bind(event: SimpleEvent) {
            val textColor: Int
            val text: String
            when(event) {
                is SimpleEvent.InternalMessage -> {
                    textColor = ContextCompat.getColor(itemView.context, R.color.grey)
                    text = "${event.message}"
                }
                is SimpleEvent.InMessage -> {
                    textColor = ContextCompat.getColor(itemView.context, R.color.blue)
                    text = "In Message: ${event.message}"
                }
                is SimpleEvent.OutMessage -> {
                    textColor = ContextCompat.getColor(itemView.context, R.color.green)
                    text = "Out Message: ${event.message}"
                }
                is SimpleEvent.ErrorMessage -> {
                    textColor = ContextCompat.getColor(itemView.context, R.color.red)
                    text = "Error ${event.message}"
                }
            }

            textView.setTextColor(textColor)
            textView.text = "$adapterPosition) $text"
        }
    }

}