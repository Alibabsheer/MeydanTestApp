package com.example.meydantestapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R

class UserProjectsAdapter(
    private val projects: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<UserProjectsAdapter.ProjectViewHolder>() {

    inner class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val projectNameText: TextView = itemView.findViewById(R.id.projectNameText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_project, parent, false)
        return ProjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val projectName = projects[position]
        holder.projectNameText.text = projectName
        holder.itemView.setOnClickListener {
            onItemClick(projectName)
        }
    }

    override fun getItemCount(): Int = projects.size
}
